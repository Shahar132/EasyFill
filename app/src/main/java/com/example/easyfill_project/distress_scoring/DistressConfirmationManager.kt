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

    // After dismissing an alternative, wait before calming.
    private val dismissedAlternativeCalmingDelayMillis: Long = 20_000L,

// After closing a calming message, wait before the next action.
    private val dismissedNextActionDelayMillis: Long = 25_000L,

    // Earliest time at which the exact dismissed default may return.
    private val exactSuggestionRepeatDelayMillis: Long = 60_000L,

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


    /*
     * Tracks whether the user has already opened the current pending suggestion.
     *
     * An unopened form-filling suggestion may be upgraded to a higher confirmed
     * level. Once opened, it keeps the existing chatbot behavior without upgrades.
     */
    private var pendingSuggestionWasOpened = false

    /**
     * The exact original recording suggestion for which the user pressed
     * "Not now".
     *
     * It may return only after enough later recordings have completed
     * and only when a later recording reaches the same distress level.
     */
    private var dismissedRecordingSuggestion: BotSuggestion? = null

    /**
     * Number of recordings completed after the original recording
     * suggestion was dismissed.
     */
    private var recordingsSinceDismissal: Int = 0

    /**
     * The original dismissed recording suggestion cannot return
     * before three later recordings have completed.
     */
    private val recordingsBeforeRepeat: Int = 3

    /**
     * Controls the support item shown on later distressed recordings.
     *
     * true:
     * show a calming message on the next distressed recording.
     *
     * false:
     * request an unused alternative action on the next distressed recording.
     */
    private var showCalmingNextForRecording: Boolean = true

    /**
     * True after the user accepted or dismissed a recording suggestion.
     *
     * Later recordings then show calming messages or unused alternatives
     * instead of immediately restarting the normal default suggestion.
     */
    private var hasRecordingFollowUpFlow: Boolean = false

    /**
     * Remembers whether the currently displayed action came from
     * a voice-recording result.
     *
     * This prevents accepted recording actions from starting the
     * form-filling 20/25-second timer flow.
     */

    /*
     * The distress level associated with the current recording follow-up flow.
     *
     * After accepting a recording suggestion, calming/alternative support
     * should continue only when the next recording has the same level.
     *
     * A different level must receive its normal default suggestion.
     */
    private var recordingFollowUpLevel: Int? = null
    private var acceptedActionCameFromRecording: Boolean = false

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
     * Marks the current pending suggestion as opened by the user.
     *
     * Any partially collected higher-level candidate is discarded,
     * and the existing chatbot behavior continues unchanged.
     */
    fun onPendingSuggestionOpened() {

        if (!hasPendingSuggestion()) {
            return
        }

        pendingSuggestionWasOpened = true

        /*
         * Do not use windows collected before the user opened
         * the current suggestion.
         */
        clearCandidate()

        Log.d(
            "DISTRESS_CONFIRM",
            "Pending suggestion was opened. Future level upgrades are disabled."
        )
    }




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
     * Receives one completed VOICE_RECORDING distress result.
     *
     * Recording mode is event-based:
     *
     * - Each completed recording is handled independently.
     * - It does not require two matching windows.
     * - It does not use the form-filling timer flow.
     * - A support item is shown immediately after a distressed recording.
     *
     * Recording behavior:
     *
     * 1. The first distressed recording shows the normal default suggestion.
     *
     * 2. If that suggestion is dismissed:
     *    - later recordings with the same level show calming/alternative support;
     *    - on recordings 3, 6, 9... after dismissal, the exact original suggestion
     *      may return when the level is still the same;
     *    - a later recording with a different level receives the normal default
     *      suggestion for that new level.
     *
     * Example:
     *
     * Original recording level 2
     * -> default level-2 suggestion
     * -> user presses "Not now"
     *
     * Later level 2
     * -> calming or alternative
     *
     * Later recording number 3, 6, 9... with level 2
     * -> exact original level-2 suggestion
     *
     * Later level 4
     * -> normal level-4 default suggestion
     */
    fun processVoiceRecording(
        result: VoiceRecordingDistressResult
    ) {
        val level = result.level.coerceIn(0, 4)

        Log.d(
            "DISTRESS_CONFIRM",
            """
            Completed multimodal voice recording:
            level=$level
        
            voiceAvailable=${result.voiceAvailable}
            voiceScore=${result.voiceScore}
        
            faceAvailable=${result.faceAvailable}
            faceAverage=${result.faceAverage}
        
            handAvailable=${result.handAvailable}
            handAverage=${result.handAverage}
        
            weightedScore=${result.weightedScore}
        
            dismissedSuggestion=${dismissedRecordingSuggestion?.id}
            dismissedSuggestionLevel=${dismissedRecordingSuggestion?.level}
            recordingsSinceDismissal=$recordingsSinceDismissal
            """.trimIndent()
                )

        /*
         * Recording results must never complete a partially confirmed
         * FORM_FILLING candidate.
         */
        clearCandidate()

        /*
         * Do not replace a support item that is still waiting for
         * the user to accept, dismiss, or close it.
         */
        if (hasPendingSuggestion()) {
            Log.d(
                "DISTRESS_CONFIRM",
                "Recording result ignored for chatbot UI because an item is pending."
            )
            return
        }

        /*
         * Every completed recording after dismissing an original
         * recording suggestion advances the repeat counter.
         *
         * Level-0 recordings also count as completed recordings,
         * but they do not display a support item.
         */
        if (dismissedRecordingSuggestion != null) {
            recordingsSinceDismissal += 1
        }

        /*
         * Recording results are independent.
         *
         * This value is only the most recent detected recording level.
         * It is not used for two-window confirmation.
         */
        _confirmedLevel.value = level

        /*
         * No detected distress.
         *
         * The recording may still count toward 3, 6, 9..., but no
         * chatbot message should be displayed.
         */
        if (level <= 0) {
            Log.d(
                "DISTRESS_CONFIRM",
                "Recording completed with level 0. No chatbot item displayed."
            )
            return
        }

        val savedDismissedSuggestion =
            dismissedRecordingSuggestion

        /*
         * An original recording suggestion is currently remembered
         * because the user previously pressed "Not now".
         */
        if (savedDismissedSuggestion != null) {

            val dismissedLevel =
                savedDismissedSuggestion.level.coerceIn(0, 4)

            val isSameLevelAsDismissed =
                level == dismissedLevel

            val isExactRepeatOpportunity =
                recordingsSinceDismissal > 0 &&
                        recordingsSinceDismissal %
                        recordingsBeforeRepeat == 0

            /*
             * The current recording has a different distress level.
             *
             * Treat it as a separate distress situation and show the
             * normal default suggestion for the current level.
             *
             * Example:
             *
             * dismissed suggestion level = 2
             * current recording level = 4
             *
             * result:
             * normal level-4 default suggestion
             */
            if (!isSameLevelAsDismissed) {
                emitRecordingDefaultSuggestion(level)

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Recording level $level differs from dismissed level " +
                            "$dismissedLevel. Requested the normal default " +
                            "suggestion for level $level."
                )

                return
            }

            /*
             * The current recording has the same level as the
             * original dismissed suggestion.
             *
             * On recordings 3, 6, 9... repeat the exact original.
             */
            if (
                isExactRepeatOpportunity &&
                savedDismissedSuggestion.id !in acceptedSuggestionIds
            ) {
                pendingSuggestionLevel = level
                pendingSuggestionSource =
                    DistressUiEvent.DistressAlertSource.VOICE_RECORDING
                pendingSuggestionHandled = false

                emitEvent(
                    DistressUiEvent.ShowExactSuggestion(
                        eventId = newEventId(),
                        suggestion = savedDismissedSuggestion
                    )
                )

                /*
                 * Do not clear dismissedRecordingSuggestion here.
                 *
                 * The user must first handle the repeated suggestion:
                 *
                 * - Accept:
                 *   onActionAccepted() clears the stored repeat state.
                 *
                 * - Not now:
                 *   onSuggestionDismissed() saves it again and resets
                 *   recordingsSinceDismissal to zero.
                 */
                Log.d(
                    "DISTRESS_CONFIRM",
                    "Exact dismissed recording suggestion repeated on " +
                            "recording $recordingsSinceDismissal: " +
                            savedDismissedSuggestion.id
                )

                return
            }

            /*
             * Same level as the dismissed original, but this recording
             * is not position 3, 6, 9...
             *
             * Show immediate support without timers:
             *
             * one recording -> calming
             * next recording -> unused alternative
             * next recording -> calming
             */
            if (showCalmingNextForRecording) {
                emitRecordingCalmingMessage(level)
                showCalmingNextForRecording = false

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Same level as dismissed recording suggestion. " +
                            "Immediate calming message requested."
                )
            } else {
                emitRecordingAlternativeSuggestion(level)
                showCalmingNextForRecording = true

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Same level as dismissed recording suggestion. " +
                            "Immediate unused alternative requested."
                )
            }

            return
        }

        if (!hasRecordingFollowUpFlow) {
            emitRecordingDefaultSuggestion(level)

            Log.d(
                "DISTRESS_CONFIRM",
                "First recording distress item requested for level $level."
            )

            return
        }

        /*
         * A previous recording suggestion was accepted.
         *
         * Continue the old calming/alternative flow only when the current
         * recording has the same distress level as the accepted suggestion.
         */
        val previousRecordingFollowUpLevel =
            recordingFollowUpLevel

        if (
            previousRecordingFollowUpLevel != null &&
            level != previousRecordingFollowUpLevel
        ) {
            /*
             * The distress level changed.
             *
             * End the previous accepted-recording follow-up flow and show the
             * normal default suggestion for the new level.
             */
            hasRecordingFollowUpFlow = false
            recordingFollowUpLevel = null
            showCalmingNextForRecording = true

            emitRecordingDefaultSuggestion(level)

            Log.d(
                "DISTRESS_CONFIRM",
                "Recording level changed after accepted suggestion: " +
                        "$previousRecordingFollowUpLevel -> $level. " +
                        "Normal default suggestion requested for level $level."
            )

            return
        }

        /*
         * The current level is the same as the previously accepted level.
         *
         * Continue the recording follow-up flow using immediate
         * calming/alternative items.
         */
        if (showCalmingNextForRecording) {
            emitRecordingCalmingMessage(level)
            showCalmingNextForRecording = false

            Log.d(
                "DISTRESS_CONFIRM",
                "Same recording level after acceptance. " +
                        "Recording follow-up calming message requested."
            )
        } else {
            emitRecordingAlternativeSuggestion(level)
            showCalmingNextForRecording = true

            Log.d(
                "DISTRESS_CONFIRM",
                "Same recording level after acceptance. " +
                        "Recording follow-up alternative suggestion requested."
            )
        }
    }

    /**
     * Requests the normal default suggestion for a recording result.
     */
    private fun emitRecordingDefaultSuggestion(level: Int) {
        pendingSuggestionLevel = level
        pendingSuggestionSource =
            DistressUiEvent.DistressAlertSource.VOICE_RECORDING
        pendingSuggestionHandled = false

        val excluded = acceptedSuggestionIds.toMutableSet()

        /*
       * Prevent the normal builder from returning the dismissed
       * original suggestion outside recordings 3, 6, 9...
       */
        dismissedRecordingSuggestion?.let { suggestion ->
            excluded.add(suggestion.id)
        }

        emitEvent(
            DistressUiEvent.ShowDefaultSuggestion(
                eventId = newEventId(),
                level = level,
                source = DistressUiEvent.DistressAlertSource.VOICE_RECORDING,

                // Accepted actions stay excluded.
                excludedSuggestionIds = excluded
            )
        )

        Log.d(
            "DISTRESS_CONFIRM",
            "Recording default suggestion requested for level $level"
        )
    }

    /**
     * Shows one calming message immediately after a later
     * distressed recording.
     *
     * There is no time-based delay.
     */
    private fun emitRecordingCalmingMessage(level: Int) {
        pendingSuggestionLevel = level
        pendingSuggestionSource =
            DistressUiEvent.DistressAlertSource.VOICE_RECORDING
        pendingSuggestionHandled = false

        emitCalmingMessage(level)

        Log.d(
            "DISTRESS_CONFIRM",
            "Immediate recording calming message emitted for level $level"
        )
    }

    /**
     * Requests one unused alternative immediately after a later
     * distressed recording.
     *
     * There is no time-based delay.
     */
    private fun emitRecordingAlternativeSuggestion(level: Int) {
        pendingSuggestionLevel = level
        pendingSuggestionSource =
            DistressUiEvent.DistressAlertSource.VOICE_RECORDING
        pendingSuggestionHandled = false

        emitEvent(
            DistressUiEvent.ShowAlternativeSuggestion(
                eventId = newEventId(),
                level = level,
                source = DistressUiEvent.DistressAlertSource.VOICE_RECORDING,
                excludedSuggestionIds =
                    getExcludedAlternativeSuggestionIds()
            )
        )

        Log.d(
            "DISTRESS_CONFIRM",
            "Immediate recording alternative requested for level $level"
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
 * Level 0 means that current distress is no longer detected.
 *
 * A currently visible support item must remain visible until the user
 * explicitly accepts, dismisses, or closes it.
 *
 * Any delayed follow-up job that has not yet appeared should be cancelled.
 */
        if (level == 0) {
            clearCandidate()
            cancelCooldown()

            /*
             * Reset the measurement level so any later non-zero distress must
             * pass the normal confirmation process again.
             */
            _confirmedLevel.value = 0

            /*
             * Preserve a visible or pending action suggestion, calming message,
             * success card, or other chatbot item.
             *
             * Do not call clearCurrentUiEvent() or clearFlowState() here.
             */
            if (hasPendingSuggestion() || _uiEvent.value != null) {
                Log.d(
                    "DISTRESS_CONFIRM",
                    "Distress decreased to level 0. " +
                            "Visible chatbot UI preserved; future cooldown stopped."
                )
                return
            }

            /*
             * Nothing is visible or waiting for the user, so the remaining
             * short-term state can be safely reset.
             */
            resetShortTermState()
            return
        }

        /*
         * A chatbot item is already waiting for the user.
         */
        if (hasPendingSuggestion()) {

            val waitingLevel =
                pendingSuggestionLevel
                    ?: run {
                        clearCandidate()
                        return
                    }

            /*
             * Only an unopened default form-filling suggestion
             * may be upgraded to a higher confirmed level.
             *
             * Once the user opens it, all existing chatbot
             * behavior continues unchanged.
             */
            val canUpgradeUnopenedSuggestion =
                !pendingSuggestionWasOpened &&
                        source ==
                        DistressUiEvent.DistressAlertSource.FORM_FILLING &&
                        pendingSuggestionSource ==
                        DistressUiEvent.DistressAlertSource.FORM_FILLING &&
                        _uiEvent.value is
                                DistressUiEvent.ShowDefaultSuggestion

            if (!canUpgradeUnopenedSuggestion) {
                clearCandidate()

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Pending suggestion keeps existing behavior. " +
                            "opened=$pendingSuggestionWasOpened, " +
                            "newLevel=$level, " +
                            "waitingLevel=$waitingLevel"
                )

                return
            }

            /*
             * The waiting suggestion may only move upward.
             * Equal or lower levels do not change it.
             */
            if (level <= waitingLevel) {
                clearCandidate()

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Unopened suggestion was not upgraded. " +
                            "newLevel=$level, " +
                            "waitingLevel=$waitingLevel"
                )

                return
            }

            /*
             * Keep the existing consecutive-window confirmation rule.
             *
             * Example:
             * waiting level = 1
             * windows = 2, 2
             * result = upgrade to level 2.
             */
            if (candidateLevel == level) {
                candidateWindowCount += 1
            } else {
                candidateLevel = level
                candidateWindowCount = 1
            }

            Log.d(
                "DISTRESS_CONFIRM",
                "Collecting unopened-suggestion upgrade. " +
                        "waitingLevel=$waitingLevel, " +
                        "candidateLevel=$candidateLevel, " +
                        "candidateCount=$candidateWindowCount"
            )

            if (
                candidateWindowCount <
                requiredMatchingWindows
            ) {
                return
            }

            upgradeUnopenedSuggestion(
                newLevel = level
            )

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
                 * Stop only future delayed follow-up items.
                 *
                 * Do not clear the current UI event, because a visible alert
                 * must remain until the user explicitly accepts or dismisses it.
                 */
                cancelCooldown()

                /*
                 * Clear the old timer-based follow-up state only when there is
                 * no suggestion currently waiting for the user.
                 */
                if (!hasPendingSuggestion()) {
                    clearFlowState()
                }

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Confirmed distress decreased: " +
                            "$previousLevel -> $newLevel. " +
                            "Visible chatbot UI preserved; future cooldown stopped."
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

        /*
         * A newly created suggestion has not been opened yet.
         */
        pendingSuggestionWasOpened = false

        emitEvent(
            DistressUiEvent.ShowDefaultSuggestion(
                eventId = newEventId(),
                level = level,
                source = source,

                /*
                 * Send a defensive copy of all suggestions accepted during
                 * the current application session.
                 */
                excludedSuggestionIds = acceptedSuggestionIds.toSet()
            )
        )

        Log.d(
            "DISTRESS_CONFIRM",
            "Default suggestion requested for level $level from $source"
        )
    }




    /**
     * Replaces an unopened default form-filling suggestion after
     * a higher level passed the existing confirmation rule.
     *
     * This does not change any accepted, dismissed, recording,
     * calming-message, or follow-up behavior.
     */
    private fun upgradeUnopenedSuggestion(
        newLevel: Int
    ) {
        val previousLevel =
            pendingSuggestionLevel
                ?: return

        if (
            pendingSuggestionWasOpened ||
            newLevel <= previousLevel
        ) {
            clearCandidate()
            return
        }

        /*
         * Continue the chatbot flow from the newly confirmed level.
         */
        _confirmedLevel.value =
            newLevel

        pendingSuggestionLevel =
            newLevel

        pendingSuggestionSource =
            DistressUiEvent
                .DistressAlertSource
                .FORM_FILLING

        pendingSuggestionHandled =
            false

        /*
         * The replacement suggestion is also unopened.
         * It may still be upgraded again before the user opens it.
         */
        pendingSuggestionWasOpened =
            false

        clearCandidate()

        /*
         * Replace the current unopened event with the higher-level event.
         */
        emitEvent(
            DistressUiEvent.ShowDefaultSuggestion(
                eventId = newEventId(),
                level = newLevel,
                source =
                    DistressUiEvent
                        .DistressAlertSource
                        .FORM_FILLING,
                excludedSuggestionIds =
                    acceptedSuggestionIds.toSet()
            )
        )

        Log.d(
            "DISTRESS_CONFIRM",
            "Unopened suggestion upgraded: " +
                    "$previousLevel -> $newLevel"
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
        val source = pendingSuggestionSource
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

        if (
            source ==
            DistressUiEvent.DistressAlertSource.VOICE_RECORDING
        ) {
            /*
             * Recording support is event-based, not timer-based.
             *
             * Wait for another completed recording before showing
             * calming or alternative support.
             */
            acceptedActionCameFromRecording = true
            hasRecordingFollowUpFlow = true
            showCalmingNextForRecording = true

            /*
             * Remember the level of the accepted recording suggestion.
             *
             * A later recording continues this follow-up flow only if
             * its detected level is the same.
             */
            recordingFollowUpLevel =
                suggestion.level.coerceIn(1, 4)

            // Do not start the form-filling accepted timer flow.
            followUpMode = null
        } else {
            acceptedActionCameFromRecording = false
            followUpMode = FollowUpMode.AFTER_ACCEPT
        }

        // Accepting support ends any earlier exact-repeat cycle.
        dismissedSuggestion = null
        suggestionDismissedAtMillis = null

        // The success card appears first. After it closes, show calming first.
        showAlternativeNextInAcceptedFlow = false

        clearCurrentUiEvent()
        cancelCooldown()

      //clearCandidate() only clears candidateLevel ,candidateWindowCount
        //Those are only temporary variables while waiting for confirmation.
        //It DOES NOT change confirmedLevel ,which is exactly what you want.
        clearCandidate()

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
        /*
         * Recording support waits for the next completed recording.
         * It must not start a time-based cooldown.
         */
        if (acceptedActionCameFromRecording) {
            acceptedActionCameFromRecording = false

            Log.d(
                "DISTRESS_CONFIRM",
                "Recording success card closed. Waiting for the next recording."
            )
            return
        }

        /*
         * Form-filling support continues with its normal timer flow.
         */
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
            val alternativeSource =  source

            displayedSuggestionIds.add(suggestion.id)
            dismissedAlternativeSuggestionIds.add(suggestion.id)

            pendingSuggestionHandled = true
            pendingSuggestionLevel = null
            pendingSuggestionSource = null

            clearCurrentUiEvent()
            cancelCooldown()

            if (
                alternativeSource ==
                DistressUiEvent.DistressAlertSource.VOICE_RECORDING
            ) {
                /*
                 * Recording alternatives are permanently excluded.
                 *
                 * Do not use a timer. Wait for the next distressed
                 * recording and show a calming message then.
                 */
                hasRecordingFollowUpFlow = true
                showCalmingNextForRecording = true
                followUpMode = null

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Recording alternative dismissed and excluded. " +
                            "Waiting for the next recording: ${suggestion.id}"
                )
            } else {
                /*
                 * Form-filling alternative dismissal keeps its
                 * 20-second calming delay.
                 */
                when (followUpMode) {
                    FollowUpMode.AFTER_DISMISS -> {
                        showAlternativeNextInDismissedFlow = false
                        scheduleCalmingAfterAlternativeDismissal()
                    }

                    FollowUpMode.AFTER_ACCEPT -> {
                        showAlternativeNextInAcceptedFlow = false
                        scheduleCalmingAfterAlternativeDismissal()
                    }

                    null -> Unit
                }
            }

            return
        }

        /*
         * Recording suggestion: do not use the old time-based repeat cycle.
         * Repeat this exact suggestion only after three later reliable recordings.
         */
        /*
 * Recording suggestion: do not use the old time-based repeat cycle.
 * Repeat this exact suggestion only after three later reliable recordings.
 */
        if (
            source ==
            DistressUiEvent.DistressAlertSource.VOICE_RECORDING
        ) {
            /*
             * Save the exact original suggestion.
             *
             * It may return only after three later recordings and only
             * on a later recording with the same distress level.
             */
            dismissedRecordingSuggestion = suggestion
            recordingsSinceDismissal = 0

            /*
             * Later distressed recordings should still receive support.
             *
             * The first later distressed recording will receive a calming
             * message, followed by an unused alternative on the next one.
             */
            hasRecordingFollowUpFlow = true
            showCalmingNextForRecording = true

            pendingSuggestionHandled = true
            pendingSuggestionLevel = null
            pendingSuggestionSource = null

            clearCurrentUiEvent()
            cancelCooldown()

            /*
             * No time-based dismissed flow is used for recording.
             */
            followUpMode = null

            /*
             * Recording results are independent, so the next completed
             * recording starts as a fresh recording measurement.
             */
            clearCandidate()

            Log.d(
                "DISTRESS_CONFIRM",
                "Recording default dismissed. It may repeat after " +
                        "$recordingsBeforeRepeat later recordings at the same level: " +
                        suggestion.id
            )
            return
        }

        /*
         * The form suggestion remained visible while the measured distress
         * returned to level 0.
         *
         * The user has now dismissed it, so close the visible item but do not
         * begin a new delayed calming/alternative flow.
         */
        if (_confirmedLevel.value <= 0) {
            clearCurrentUiEvent()
            cancelCooldown()
            clearFlowState()
            clearCandidate()

            Log.d(
                "DISTRESS_CONFIRM",
                "Form-filling suggestion dismissed after distress returned to level 0. " +
                        "No follow-up flow scheduled."
            )

            return
        }

        /*
         * FORM_FILLING suggestion: preserve the existing calming/alternative and
         * exact-repeat flow.
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

    private fun scheduleCalmingAfterAlternativeDismissal() {
        cancelCooldown()

        val expectedLevel = _confirmedLevel.value
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(dismissedAlternativeCalmingDelayMillis)

            if (_confirmedLevel.value != expectedLevel) {
                stopFollowUpBecauseLevelChanged(
                    expectedLevel = expectedLevel,
                    stage = "dismissed alternative calming"
                )
                return@launch
            }

            if (followUpMode == null) {
                return@launch
            }

            emitCalmingMessage(expectedLevel)

            when (followUpMode) {
                FollowUpMode.AFTER_ACCEPT ->
                    showAlternativeNextInAcceptedFlow = true

                FollowUpMode.AFTER_DISMISS ->
                    showAlternativeNextInDismissedFlow = true

                null -> Unit
            }
        }
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
        /*
         * The calming message was explicitly closed, so it must no longer
         * remain marked as a pending chatbot item.
         *
         * This is especially important for recording mode, where
         * followUpMode is null. Without clearing these values,
         * hasPendingSuggestion() remains true and all future recording
         * and form-filling distress results are ignored.
         */
        pendingSuggestionHandled = true
        pendingSuggestionLevel = null
        pendingSuggestionSource = null

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

            /*
             * Recording support has no timer-based followUpMode.
             *
             * After closing the calming message, simply wait for the next
             * completed recording. The recording flow will then choose an
             * unused alternative because showCalmingNextForRecording was
             * already changed to false.
             */
            null -> {
                Log.d(
                    "DISTRESS_CONFIRM",
                    "Recording calming message closed. " +
                            "Pending state cleared; waiting for next recording."
                )
            }
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

        val expectedLevel = lastAcceptedSuggestionLevel
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(acceptedCalmingDelayMillis)

            if (followUpMode != FollowUpMode.AFTER_ACCEPT) {
                return@launch
            }

            if (_confirmedLevel.value != expectedLevel) {
                stopFollowUpBecauseLevelChanged(
                    expectedLevel = expectedLevel,
                    stage = "accepted calming"
                )
                return@launch
            }

            emitCalmingMessage(expectedLevel)

            // When the calming card closes, the next item is an alternative.
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

        val expectedLevel = lastAcceptedSuggestionLevel
        if (expectedLevel <= 0) return

        cooldownJob = scope.launch {
            delay(acceptedAlternativeDelayMillis)

            if (followUpMode != FollowUpMode.AFTER_ACCEPT) {
                return@launch
            }

            if (_confirmedLevel.value != expectedLevel) {
                stopFollowUpBecauseLevelChanged(
                    expectedLevel = expectedLevel,
                    stage = "accepted alternative"
                )
                return@launch
            }

            emitAlternativeSuggestion(expectedLevel)

            // After this alternative is accepted or dismissed,
            // the next item should again be a calming message.
            showAlternativeNextInAcceptedFlow = false
        }
    }

    private fun stopFollowUpBecauseLevelChanged(
        expectedLevel: Int,
        stage: String
    ) {
        cancelCooldown()
        clearCurrentUiEvent()
        clearFlowState()

        Log.d(
            "DISTRESS_CONFIRM",
            "Stopped $stage flow because level changed. " +
                    "expected=$expectedLevel, actual=${_confirmedLevel.value}"
        )
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
            delay(dismissedNextActionDelayMillis)

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
     * Called by FloatingChatOverlay when the default builder cannot create
     * either the normal default suggestion or an unused alternative.
     *
     * This clears the pending state so the manager does not remain stuck
     * waiting for a suggestion that was never displayed.
     */
    fun onDefaultSuggestionUnavailable() {

        pendingSuggestionHandled = true
        pendingSuggestionLevel = null
        pendingSuggestionSource = null

        clearCurrentUiEvent()
        clearCandidate()

        Log.d(
            "DISTRESS_CONFIRM",
            "No available default or alternative suggestion could be displayed."
        )
    }


    /**
     * Requests an unused alternative for the current support flow.
     */
    private fun emitAlternativeSuggestion(
        level: Int,
        source: DistressUiEvent.DistressAlertSource =
            DistressUiEvent.DistressAlertSource.FORM_FILLING
    ) {
        pendingSuggestionLevel = level
        pendingSuggestionSource = source
        pendingSuggestionHandled = false

        emitEvent(
            DistressUiEvent.ShowAlternativeSuggestion(
                eventId = newEventId(),
                level = level,
                source = source,
                excludedSuggestionIds =
                    getExcludedAlternativeSuggestionIds()
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
     * Immediately resets the short-term distress state
     * when no chatbot item is waiting for the user.
     */
    private fun resetShortTermState() {

        /*
         * A sensor reset must not close or replace a chatbot
         * item that is still waiting for the user.
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