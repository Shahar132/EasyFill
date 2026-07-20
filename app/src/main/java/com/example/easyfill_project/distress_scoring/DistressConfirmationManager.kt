package com.example.easyfill_project.distress_scoring

import android.os.SystemClock
import android.util.Log
import com.example.easyfill_project.chatbot.logic.BotSuggestion
import com.example.easyfill_project.chatbot.logic.CalmingMessageCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Confirms distress levels and controls the timing of chatbot support items.
 *
 * Main behavior:
 *
 * 1. A non-zero level must appear in two consecutive windows before confirmation.
 * 2. Level 0 resets the short-term cycle only when no suggestion is waiting for the user.
 * 3. A confirmed increase shows the stronger default suggestion when no alert is already pending.
 * 4. A confirmed decrease updates the measured level without closing a pending alert or popup.
 * 5. Dismissing a default suggestion:
 *    - waits 15 seconds;
 *    - shows a calming message;
 *    - keeps at least 10 seconds between later items;
 *    - never repeats the original suggestion before 40 seconds;
 *    - may show unused alternative actions before 40 seconds.
 * 6. Accepting any action:
 *    - stores the accepted action so it cannot be suggested again;
 *    - ends the old 40-second repeat cycle;
 *    - waits for the success card to close;
 *    - waits 10 seconds and shows a calming message;
 *    - after that message closes, waits 15 seconds and shows another unused action.
 *
 * This class does not execute BotAction. It only emits DistressUiEvent values.
 */
class DistressConfirmationManager(

    private val scope: CoroutineScope,

    private val requiredMatchingWindows: Int = 2,

    // Delay from "לא עכשיו" until the first calming message.
    private val firstCalmingDelayMillis: Long = 15_000L,

    // Minimum delay between later items in the dismissed flow.
    private val dismissedFlowGapMillis: Long = 10_000L,

    // Earliest time at which the exact dismissed default may return.
    private val exactSuggestionRepeatDelayMillis: Long = 40_000L,

    // Delay after an accepted-action success card closes.
    private val acceptedCalmingDelayMillis: Long = 10_000L,

    // Delay after the accepted-flow calming message closes.
    private val acceptedAlternativeDelayMillis: Long = 15_000L
) {

    private enum class FollowUpMode {
        AFTER_ACCEPT,
        AFTER_DISMISS
    }

    // Candidate level waiting for a second matching measurement window.
    private var candidateLevel: Int? = null
    private var candidateWindowCount: Int = 0

    private val _confirmedLevel = MutableStateFlow(0)
    val confirmedLevel: StateFlow<Int> = _confirmedLevel

    private val _uiEvent = MutableStateFlow<DistressUiEvent?>(null)
    val uiEvent: StateFlow<DistressUiEvent?> = _uiEvent

    private var nextEventId: Long = 0L
    private var cooldownJob: Job? = null
    private var followUpMode: FollowUpMode? = null

    // Exact default suggestion dismissed by the user.
    private var dismissedSuggestion: BotSuggestion? = null

    // Monotonic timestamp used for the 40-second rule.
    private var suggestionDismissedAtMillis: Long? = null

    // In the dismissed flow, alternate between an action and a calming message.
    private var showAlternativeNextInDismissedFlow: Boolean = true

    // In the accepted flow, the first item is calming and the next is an action.
    private var showAlternativeNextInAcceptedFlow: Boolean = false

    private var pendingSuggestionLevel: Int? = null

    private var pendingSuggestionSource:
            DistressUiEvent.DistressAlertSource? = null

    private var pendingSuggestionHandled = true

    private var dismissedRecordingSuggestion:
            BotSuggestion? = null

    private var recordingsSinceDismissal = 0

    private val recordingsBeforeRepeat = 3

    // Level connected to the most recently accepted action.
    private var lastAcceptedSuggestionLevel: Int = 0

    /**
     * History for the current confirmed distress event.
     *
     * displayedSuggestionIds:
     * every action card that was actually displayed.
     *
     * acceptedSuggestionIds:
     * every action card accepted by the user.
     *
     * dismissedAlternativeSuggestionIds:
     * alternative cards dismissed by the user.
     *
     * All of these IDs are excluded when choosing a future alternative action.
     */
    private val displayedSuggestionIds = mutableSetOf<String>()
    private val acceptedSuggestionIds = mutableSetOf<String>()
    private val dismissedAlternativeSuggestionIds = mutableSetOf<String>()

    // Rotating calming-message position for every distress level.
    private val nextCalmingIndexByLevel = mutableMapOf<Int, Int>()

    /**
     * Receives one completed FORM_FILLING measurement window.
     *
     * Form-filling windows still require the same non-zero
     * distress level to appear in consecutive windows before
     * that level is confirmed.
     */
    fun processWindow(window: DistressWindowResult) {

        processLevel(
            rawLevel = window.level,
            source = DistressUiEvent.DistressAlertSource.FORM_FILLING
        )
    }

    /**
     * Receives one completed VOICE_RECORDING result.
     *
     * A voice-recording result already represents the entire
     * recording:
     *
     * - all recording hand windows were averaged;
     * - one final voice score was calculated;
     * - both values were combined into one weighted result.
     *
     * Therefore, it must NOT go through the two-consecutive-window
     * confirmation rule.
     *
     * It is treated immediately as a completed and confirmed result.
     */
    fun processVoiceRecording(
        result: VoiceRecordingDistressResult
    ) {
        val level = result.level.coerceIn(0, 4)

        Log.d(
            "DISTRESS_CONFIRM",
            """
            Completed voice-recording result received:
            level=$level
            voiceScore=${result.voiceScore}
            handAverage=${result.handAverage}
            weightedScore=${result.weightedScore}
            previousConfirmedLevel=${_confirmedLevel.value}
            """.trimIndent()
        )

        /*
         * A recording is already one complete aggregated result.
         * It must never complete a partially observed FORM_FILLING candidate.
         */
        clearCandidate()

        /*
         * When the user previously selected "Not now" for a recording
         * suggestion, count later reliable recordings. After three later
         * recordings, the same exact suggestion may be shown again.
         */
        if (
            dismissedRecordingSuggestion != null &&
            pendingSuggestionHandled
        ) {
            recordingsSinceDismissal += 1

            Log.d(
                "DISTRESS_CONFIRM",
                "Recordings since dismissal: " +
                        "$recordingsSinceDismissal/$recordingsBeforeRepeat"
            )

            if (recordingsSinceDismissal >= recordingsBeforeRepeat) {
                val suggestion = dismissedRecordingSuggestion

                if (
                    suggestion != null &&
                    suggestion.id !in acceptedSuggestionIds
                ) {
                    pendingSuggestionLevel = suggestion.level
                    pendingSuggestionSource =
                        DistressUiEvent.DistressAlertSource.VOICE_RECORDING
                    pendingSuggestionHandled = false

                    _confirmedLevel.value = suggestion.level.coerceIn(0, 4)

                    emitEvent(
                        DistressUiEvent.ShowExactSuggestion(
                            eventId = newEventId(),
                            suggestion = suggestion
                        )
                    )

                    Log.d(
                        "DISTRESS_CONFIRM",
                        "Dismissed recording suggestion repeated after " +
                                "$recordingsBeforeRepeat later recordings: ${suggestion.id}"
                    )
                }

                recordingsSinceDismissal = 0
                dismissedRecordingSuggestion = null
                return
            }
        }

        /*
         * A complete recording bypasses the two-window confirmation rule.
         */
        processConfirmedLevel(
            level = level,
            source = DistressUiEvent.DistressAlertSource.VOICE_RECORDING
        )
    }

    /**
     * Receives one completed measurement-window level.
     */
    fun processLevel(
        rawLevel: Int,
        source: DistressUiEvent.DistressAlertSource =
            DistressUiEvent.DistressAlertSource.FORM_FILLING
    ) {
        val level = rawLevel.coerceIn(0, 4)

        Log.d(
            "DISTRESS_CONFIRM",
            """
            New window:
            level=$level
            candidateLevel=$candidateLevel
            candidateCount=$candidateWindowCount
            confirmedLevel=${_confirmedLevel.value}
            pendingSuggestion=${hasPendingSuggestion()}
            """.trimIndent()
        )

        /*
         * A visible or unopened suggestion is already waiting.
         * Sensor changes may continue, but they must not close, replace,
         * or reset the current alert and popup.
         */
        if (hasPendingSuggestion()) {
            clearCandidate()

            Log.d(
                "DISTRESS_CONFIRM",
                "Measurement ignored for chatbot UI while suggestion is pending. " +
                        "newLevel=$level, pendingLevel=$pendingSuggestionLevel"
            )
            return
        }

        /*
         * Level 0 may reset only when no suggestion is waiting for a user decision.
         */
        if (level == 0) {
            clearCandidate()
            resetShortTermState()
            return
        }

        if (level == _confirmedLevel.value) {
            clearCandidate()
            return
        }

        // A different FORM_FILLING level must appear in consecutive windows.
        if (candidateLevel == level) {
            candidateWindowCount += 1
        } else {
            candidateLevel = level
            candidateWindowCount = 1
        }

        if (candidateWindowCount < requiredMatchingWindows) {
            return
        }

        confirmCandidateLevel(
            newLevel = level,
            source = source
        )
    }

    /**
     * Applies a newly confirmed level.
     */
    private fun confirmCandidateLevel(
        newLevel: Int,
        source: DistressUiEvent.DistressAlertSource
    ) {
        val previousLevel = _confirmedLevel.value

        _confirmedLevel.value = newLevel
        clearCandidate()

        when {
            previousLevel == 0 -> {
                beginNewAlertLevel(
                    level = newLevel,
                    source = source
                )
            }

            newLevel > previousLevel -> {
                beginNewAlertLevel(
                    level = newLevel,
                    source = source
                )
            }

            newLevel < previousLevel -> {
                /*
                 * Never emit Reset here. A decrease changes the measured
                 * level but does not close an alert or popup.
                 */
                Log.d(
                    "DISTRESS_CONFIRM",
                    "Confirmed distress decreased: " +
                            "$previousLevel -> $newLevel. Pending chatbot UI preserved."
                )
            }
        }
    }

    /**
     * Applies an already-complete result, such as one full voice recording.
     */
    private fun processConfirmedLevel(
        level: Int,
        source: DistressUiEvent.DistressAlertSource
    ) {
        if (hasPendingSuggestion()) {
            Log.d(
                "DISTRESS_CONFIRM",
                "Completed result ignored for chatbot UI because a suggestion is pending."
            )
            return
        }

        if (level <= 0) {
            resetShortTermState()
            return
        }

        val previousLevel = _confirmedLevel.value
        _confirmedLevel.value = level
        clearCandidate()

        when {
            previousLevel == 0 -> {
                beginNewAlertLevel(level, source)
            }

            level > previousLevel -> {
                beginNewAlertLevel(level, source)
            }

            level < previousLevel -> {
                Log.d(
                    "DISTRESS_CONFIRM",
                    "Completed distress result decreased: " +
                            "$previousLevel -> $level. No UI reset emitted."
                )
            }

            else -> {
                Log.d(
                    "DISTRESS_CONFIRM",
                    "Completed distress level unchanged: $level"
                )
            }
        }
    }

    /**
     * Starts a fresh cycle for a newly confirmed or stronger level.
     */
    private fun beginNewAlertLevel(
        level: Int,
        source: DistressUiEvent.DistressAlertSource
    ) {
        if (hasPendingSuggestion()) {
            return
        }

        cancelCooldown()
        clearFlowState()

        /*
         * Keep acceptedSuggestionIds for the full app session so an accepted
         * action cannot be offered again. Other short-term history may reset.
         */
        clearShortTermSuggestionHistory()

        pendingSuggestionLevel = level
        pendingSuggestionSource = source
        pendingSuggestionHandled = false

        emitEvent(
            DistressUiEvent.ShowDefaultSuggestion(
                eventId = newEventId(),
                level = level,
                source = source
            )
        )

        Log.d(
            "DISTRESS_CONFIRM",
            "Default suggestion requested for level $level from $source"
        )
    }

    /**
     * Called by FloatingChatOverlay only after an action suggestion
     * was successfully built and placed in the visible queue.
     */
    fun onSuggestionDisplayed(suggestion: BotSuggestion) {
        // Calming messages have no options and are not action-history items.
        if (suggestion.options.isEmpty()) {
            return
        }

        displayedSuggestionIds.add(suggestion.id)

        Log.d(
            "DISTRESS_CONFIRM",
            "Suggestion displayed: ${suggestion.id}"
        )
    }

    /**
     * Called when the user accepts one of the displayed action suggestions.
     *
     * The exact suggestion is required so its stable ID can be stored.
     */
    fun onActionAccepted(suggestion: BotSuggestion) {
        /*
         * Acceptance must still work even if the live distress level already fell.
         */
        displayedSuggestionIds.add(suggestion.id)
        acceptedSuggestionIds.add(suggestion.id)
        lastAcceptedSuggestionLevel = suggestion.level.coerceIn(0, 4)

        if (dismissedRecordingSuggestion?.id == suggestion.id) {
            dismissedRecordingSuggestion = null
            recordingsSinceDismissal = 0
        }

        pendingSuggestionHandled = true
        pendingSuggestionLevel = null
        pendingSuggestionSource = null

        followUpMode = FollowUpMode.AFTER_ACCEPT

        // Accepting support ends any earlier exact-repeat cycle.
        dismissedSuggestion = null
        suggestionDismissedAtMillis = null

        // The success card appears first. After it closes, show calming first.
        showAlternativeNextInAcceptedFlow = false

        clearCurrentUiEvent()
        cancelCooldown()

        /*
         * Reset only the measurement state. Do not emit Reset because the overlay
         * is now responsible for showing and closing the success card.
         */
        resetMeasurementStateWithoutUiReset()

        Log.d(
            "DISTRESS_CONFIRM",
            "Suggestion accepted and excluded for the app session: ${suggestion.id}"
        )
    }

    /**
     * Called after the success card for an accepted action closes,
     * finishes automatically, or navigates to a settings screen.
     */
    fun onAcceptedActionMessageClosed() {
        if (followUpMode != FollowUpMode.AFTER_ACCEPT) {
            return
        }

        scheduleAcceptedCalmingMessage()
    }

    /**
     * Called for both the "לא עכשיו" button and tapping outside an action card.
     */
    fun onSuggestionDismissed(suggestion: BotSuggestion) {
        val source = pendingSuggestionSource

        /*
         * The suggestion has now been explicitly handled by the user.
         */
        pendingSuggestionHandled = true
        pendingSuggestionLevel = null
        pendingSuggestionSource = null

        /**
         * Alternative dismissal keeps the existing accepted/dismissed follow-up
         * behavior. Alternative IDs remain excluded.
         */
        if (suggestion.id.startsWith("alternative_")) {
            displayedSuggestionIds.add(suggestion.id)
            dismissedAlternativeSuggestionIds.add(suggestion.id)

            clearCurrentUiEvent()
            cancelCooldown()

            when (followUpMode) {
                FollowUpMode.AFTER_DISMISS -> {
                    showAlternativeNextInDismissedFlow = false
                    scheduleNextDismissedFlowItem()
                }

                FollowUpMode.AFTER_ACCEPT -> {
                    showAlternativeNextInAcceptedFlow = false
                    scheduleAcceptedCalmingMessage()
                }

                null -> Unit
            }

            Log.d(
                "DISTRESS_CONFIRM",
                "Alternative dismissed and excluded: ${suggestion.id}"
            )
            return
        }

        /*
         * Recording suggestion: do not use the old time-based repeat cycle.
         * Repeat this exact suggestion only after three later reliable recordings.
         */
        if (source == DistressUiEvent.DistressAlertSource.VOICE_RECORDING) {
            dismissedRecordingSuggestion = suggestion
            recordingsSinceDismissal = 0

            clearCurrentUiEvent()
            cancelCooldown()
            clearFlowState()
            resetMeasurementStateWithoutUiReset()

            Log.d(
                "DISTRESS_CONFIRM",
                "Recording suggestion dismissed. It may repeat after " +
                        "$recordingsBeforeRepeat later recordings: ${suggestion.id}"
            )
            return
        }

        /*
         * FORM_FILLING suggestion: preserve the existing calming/alternative and
         * 40-second exact-repeat flow.
         */
        followUpMode = FollowUpMode.AFTER_DISMISS
        dismissedSuggestion = suggestion
        suggestionDismissedAtMillis = SystemClock.elapsedRealtime()
        showAlternativeNextInDismissedFlow = true

        clearCurrentUiEvent()
        cancelCooldown()
        scheduleFirstCalmingMessageAfterDismiss()

        Log.d(
            "DISTRESS_CONFIRM",
            "Form-filling default suggestion dismissed: ${suggestion.id}"
        )
    }

    /**
     * Waits 15 seconds after "לא עכשיו", then emits the first calming message.
     */
    private fun scheduleFirstCalmingMessageAfterDismiss() {
        cancelCooldown()

        val expectedLevel = _confirmedLevel.value
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(firstCalmingDelayMillis)

            if (_confirmedLevel.value != expectedLevel) return@launch
            if (followUpMode != FollowUpMode.AFTER_DISMISS) return@launch

            emitCalmingMessage(expectedLevel)

            // After this calming message closes, prefer an alternative action.
            showAlternativeNextInDismissedFlow = true
        }
    }

    /**
     * Called when the user closes a calming-message card.
     */
    fun onCalmingMessageClosed() {
        clearCurrentUiEvent()

        when (followUpMode) {
            FollowUpMode.AFTER_ACCEPT -> {
                if (showAlternativeNextInAcceptedFlow) {
                    scheduleAcceptedAlternativeSuggestion()
                } else {
                    scheduleAcceptedCalmingMessage()
                }
            }

            FollowUpMode.AFTER_DISMISS -> {
                scheduleNextDismissedFlowItem()
            }

            null -> Unit
        }
    }

    /**
     * Accepted flow:
     *
     * success card closes
     * -> wait 10 seconds
     * -> calming message
     */
    private fun scheduleAcceptedCalmingMessage() {
        cancelCooldown()

        val expectedLevel = lastHandledSupportLevel()
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(acceptedCalmingDelayMillis)

            if (followUpMode != FollowUpMode.AFTER_ACCEPT) return@launch

            emitCalmingMessage(expectedLevel)

            // After the calming card closes, wait 15 seconds for an alternative.
            showAlternativeNextInAcceptedFlow = true
        }
    }

    /**
     * Accepted flow:
     *
     * calming message closes
     * -> wait 15 seconds
     * -> request another unused action
     */
    private fun scheduleAcceptedAlternativeSuggestion() {
        cancelCooldown()

        val expectedLevel = lastHandledSupportLevel()
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(acceptedAlternativeDelayMillis)

            if (followUpMode != FollowUpMode.AFTER_ACCEPT) return@launch

            emitAlternativeSuggestion(expectedLevel)

            // Once that action is accepted/dismissed, return to calming.
            showAlternativeNextInAcceptedFlow = false
        }
    }

    /**
     * Dismissed flow after the first calming message.
     *
     * Every call waits at least 10 seconds.
     * Before 40 seconds, it alternates between unused actions and calming messages.
     * At or after 40 seconds, the exact original suggestion may return.
     */
    private fun scheduleNextDismissedFlowItem() {
        cancelCooldown()

        val expectedLevel = _confirmedLevel.value
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(dismissedFlowGapMillis)

            if (_confirmedLevel.value != expectedLevel) return@launch
            if (followUpMode != FollowUpMode.AFTER_DISMISS) return@launch

            val dismissedAt = suggestionDismissedAtMillis ?: return@launch
            val elapsed = SystemClock.elapsedRealtime() - dismissedAt

            if (elapsed >= exactSuggestionRepeatDelayMillis) {
                emitDismissedSuggestionIfAvailable()
                return@launch
            }

            if (showAlternativeNextInDismissedFlow) {
                emitAlternativeSuggestion(expectedLevel)
                showAlternativeNextInDismissedFlow = false
            } else {
                emitCalmingMessage(expectedLevel)
                showAlternativeNextInDismissedFlow = true
            }
        }
    }

    /**
     * Called by FloatingChatOverlay when the alternative builder returns null.
     *
     * This prevents the flow from becoming stuck when every action has already
     * been displayed, accepted, dismissed, or is not suitable for appState.
     */
    fun onAlternativeSuggestionUnavailable() {
        clearCurrentUiEvent()
        cancelCooldown()

        when (followUpMode) {
            FollowUpMode.AFTER_ACCEPT -> {
                showAlternativeNextInAcceptedFlow = false
                scheduleAcceptedCalmingMessage()
            }

            FollowUpMode.AFTER_DISMISS -> {
                showAlternativeNextInDismissedFlow = false
                scheduleNextDismissedFlowItem()
            }

            null -> Unit
        }
    }

    /**
     * Emits an event asking the overlay/builder for an unused alternative.
     */
    private fun emitAlternativeSuggestion(level: Int) {
        emitEvent(
            DistressUiEvent.ShowAlternativeSuggestion(
                eventId = newEventId(),
                level = level,
                excludedSuggestionIds = getExcludedAlternativeSuggestionIds()
            )
        )
    }

    /**
     * Returns all IDs the alternative builder must avoid.
     */
    private fun getExcludedAlternativeSuggestionIds(): Set<String> {
        return buildSet {
            addAll(displayedSuggestionIds)
            addAll(acceptedSuggestionIds)
            addAll(dismissedAlternativeSuggestionIds)

            // The original default is never used as an "alternative".
            dismissedSuggestion?.id?.let(::add)
        }
    }

    /**
     * Emits the exact default suggestion saved at dismissal time.
     */
    private fun emitDismissedSuggestionIfAvailable() {
        val suggestion = dismissedSuggestion ?: return

        emitEvent(
            DistressUiEvent.ShowExactSuggestion(
                eventId = newEventId(),
                suggestion = suggestion
            )
        )
    }

    /**
     * Emits the next rotating calming message for the level.
     */
    private fun emitCalmingMessage(level: Int) {
        val message = getNextCalmingMessage(level)
        if (message.isBlank()) return

        emitEvent(
            DistressUiEvent.ShowCalmingMessage(
                eventId = newEventId(),
                level = level,
                message = message
            )
        )
    }

    private fun getNextCalmingMessage(level: Int): String {
        val messages = CalmingMessageCatalog.getMessagesForLevel(level)
        if (messages.isEmpty()) return ""

        val currentIndex = nextCalmingIndexByLevel[level] ?: 0
        val safeIndex = currentIndex % messages.size
        val selectedMessage = messages[safeIndex]

        nextCalmingIndexByLevel[level] =
            (safeIndex + 1) % messages.size

        return selectedMessage
    }


    private fun hasPendingSuggestion(): Boolean {
        return !pendingSuggestionHandled &&
                pendingSuggestionLevel != null
    }

    /**
     * Immediate short-term reset for level 0.
     */
    private fun resetShortTermState() {
        /*
         * A sensor reset is not allowed to close an alert that still awaits
         * an explicit user decision.
         */
        if (hasPendingSuggestion()) {
            clearCandidate()
            Log.d(
                "DISTRESS_CONFIRM",
                "Short-term reset skipped because a suggestion is pending."
            )
            return
        }

        val hadActiveState =
            _confirmedLevel.value != 0 ||
                    candidateLevel != null ||
                    _uiEvent.value != null

        cancelCooldown()

        candidateLevel = null
        candidateWindowCount = 0
        _confirmedLevel.value = 0

        clearFlowState()
        clearShortTermSuggestionHistory()
        nextCalmingIndexByLevel.clear()

        if (hadActiveState) {
            emitResetEvent()
        }

        Log.d(
            "DISTRESS_CONFIRM",
            "Short-term distress state reset at level 0."
        )
    }

    /**
     * Resets only measurement values after the user explicitly handles an alert.
     * No Reset event is emitted, so an action success card is not closed.
     */
    private fun resetMeasurementStateWithoutUiReset() {
        candidateLevel = null
        candidateWindowCount = 0
        _confirmedLevel.value = 0
    }

    private fun lastHandledSupportLevel(): Int {
        return when {
            lastAcceptedSuggestionLevel > 0 -> lastAcceptedSuggestionLevel
            pendingSuggestionLevel != null -> pendingSuggestionLevel ?: 0
            else -> _confirmedLevel.value
        }
    }

    private fun clearFlowState() {
        followUpMode = null
        dismissedSuggestion = null
        suggestionDismissedAtMillis = null
        showAlternativeNextInDismissedFlow = true
        showAlternativeNextInAcceptedFlow = false
    }

    /**
     * Clear only short-term display history. Accepted IDs intentionally remain
     * for the whole app session, so accepted actions are not suggested again.
     */
    private fun clearShortTermSuggestionHistory() {
        displayedSuggestionIds.clear()
        dismissedAlternativeSuggestionIds.clear()
    }

    private fun clearCandidate() {
        candidateLevel = null
        candidateWindowCount = 0
    }

    private fun cancelCooldown() {
        cooldownJob?.cancel()
        cooldownJob = null
    }

    private fun clearCurrentUiEvent() {
        _uiEvent.value = null
    }

    private fun emitResetEvent() {
        emitEvent(
            DistressUiEvent.Reset(
                eventId = newEventId()
            )
        )
    }

    private fun emitEvent(event: DistressUiEvent) {
        _uiEvent.value = event
    }

    private fun newEventId(): Long {
        nextEventId += 1
        return nextEventId
    }
}