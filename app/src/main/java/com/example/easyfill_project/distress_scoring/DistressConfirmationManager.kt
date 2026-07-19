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
 * 2. Level 0 resets the entire short-term support cycle immediately.
 * 3. A confirmed increase cancels the old cycle and shows the stronger default suggestion.
 * 4. A confirmed decrease cancels the old cycle, clears the UI, and remains silent.
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

    fun processWindow(window: DistressWindowResult) {
        processLevel(window.level)
    }

    /**
     * Receives one completed measurement-window level.
     */
    fun processLevel(rawLevel: Int) {
        val level = rawLevel.coerceIn(0, 4)

        Log.d(
            "DISTRESS_CONFIRM",
            """
            New window:
            level=$level
            candidateLevel=$candidateLevel
            candidateCount=$candidateWindowCount
            confirmedLevel=${_confirmedLevel.value}
            """.trimIndent()
        )

        // One level-0 window resets the short-term flow immediately.
        if (level == 0) {
            resetShortTermState()
            return
        }

        // The confirmed level is unchanged, so no new confirmation is required.
        if (level == _confirmedLevel.value) {
            clearCandidate()
            return
        }

        // A different level must appear in consecutive windows.
        if (candidateLevel == level) {
            candidateWindowCount += 1
        } else {
            candidateLevel = level
            candidateWindowCount = 1
        }

        if (candidateWindowCount < requiredMatchingWindows) {
            return
        }

        confirmCandidateLevel(level)
    }

    /**
     * Applies a newly confirmed level.
     */
    private fun confirmCandidateLevel(newLevel: Int) {
        val previousLevel = _confirmedLevel.value

        _confirmedLevel.value = newLevel
        clearCandidate()

        when {
            // Initial positive level or confirmed increase:
            // begin a new cycle and immediately request the default suggestion.
            previousLevel == 0 || newLevel > previousLevel -> {
                beginNewAlertLevel(newLevel)
            }

            // Confirmed decrease:
            // cancel the stronger-level flow, clear the UI, and remain silent.
            newLevel < previousLevel -> {
                cancelCooldown()
                clearFlowState()
                clearSuggestionHistory()
                emitResetEvent()

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Confirmed level decreased: $previousLevel -> $newLevel. UI cleared."
                )
            }
        }
    }

    /**
     * Starts a fresh cycle for a newly confirmed or stronger level.
     */
    private fun beginNewAlertLevel(level: Int) {
        cancelCooldown()
        clearFlowState()
        clearSuggestionHistory()

        emitEvent(
            DistressUiEvent.ShowDefaultSuggestion(
                eventId = newEventId(),
                level = level
            )
        )

        Log.d(
            "DISTRESS_CONFIRM",
            "Default suggestion requested for level $level"
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
        if (_confirmedLevel.value <= 0) {
            return
        }

        followUpMode = FollowUpMode.AFTER_ACCEPT

        displayedSuggestionIds.add(suggestion.id)
        acceptedSuggestionIds.add(suggestion.id)

        // Accepting support ends any earlier default-repeat cycle.
        dismissedSuggestion = null
        suggestionDismissedAtMillis = null

        // The success card appears first. After it closes, show calming first.
        showAlternativeNextInAcceptedFlow = false

        clearCurrentUiEvent()
        cancelCooldown()

        Log.d(
            "DISTRESS_CONFIRM",
            "Suggestion accepted and excluded from future alternatives: ${suggestion.id}"
        )
    }

    /**
     * Called after the success card for an accepted action closes,
     * finishes automatically, or navigates to a settings screen.
     */
    fun onAcceptedActionMessageClosed() {
        if (
            _confirmedLevel.value <= 0 ||
            followUpMode != FollowUpMode.AFTER_ACCEPT
        ) {
            return
        }

        scheduleAcceptedCalmingMessage()
    }

    /**
     * Called for both the "לא עכשיו" button and tapping outside an action card.
     */
    fun onSuggestionDismissed(suggestion: BotSuggestion) {
        if (_confirmedLevel.value <= 0) {
            return
        }

        /**
         * Alternative dismissal:
         *
         * - store the ID so the action is not offered again;
         * - do not restart the original 40-second timer;
         * - continue according to the current flow.
         */
        if (suggestion.id.startsWith("alternative_")) {
            displayedSuggestionIds.add(suggestion.id)
            dismissedAlternativeSuggestionIds.add(suggestion.id)

            clearCurrentUiEvent()
            cancelCooldown()

            when (followUpMode) {
                FollowUpMode.AFTER_DISMISS -> {
                    // Next dismissed-flow item should be calming.
                    showAlternativeNextInDismissedFlow = false
                    scheduleNextDismissedFlowItem()
                }

                FollowUpMode.AFTER_ACCEPT -> {
                    // After dismissing an accepted-flow alternative,
                    // return to a calming message.
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

        /**
         * Default/exact suggestion dismissal:
         *
         * Store the exact object because it may return only after 40 seconds.
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
            "Default suggestion dismissed: ${suggestion.id}"
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

        val expectedLevel = _confirmedLevel.value
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(acceptedCalmingDelayMillis)

            if (_confirmedLevel.value != expectedLevel) return@launch
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

        val expectedLevel = _confirmedLevel.value
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(acceptedAlternativeDelayMillis)

            if (_confirmedLevel.value != expectedLevel) return@launch
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

    /**
     * Immediate short-term reset for level 0.
     */
    private fun resetShortTermState() {
        val hadActiveState =
            _confirmedLevel.value != 0 ||
                    candidateLevel != null ||
                    _uiEvent.value != null

        cancelCooldown()

        candidateLevel = null
        candidateWindowCount = 0
        _confirmedLevel.value = 0

        clearFlowState()
        clearSuggestionHistory()
        nextCalmingIndexByLevel.clear()

        if (hadActiveState) {
            emitResetEvent()
        }

        Log.d(
            "DISTRESS_CONFIRM",
            "Short-term distress state reset at level 0."
        )
    }

    private fun clearFlowState() {
        followUpMode = null
        dismissedSuggestion = null
        suggestionDismissedAtMillis = null
        showAlternativeNextInDismissedFlow = true
        showAlternativeNextInAcceptedFlow = false
    }

    private fun clearSuggestionHistory() {
        displayedSuggestionIds.clear()
        acceptedSuggestionIds.clear()
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