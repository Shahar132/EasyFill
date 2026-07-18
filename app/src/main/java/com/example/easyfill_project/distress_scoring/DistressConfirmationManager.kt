package com.example.easyfill_project.distress_scoring

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
 * Controls the long-term distress-confirmation and chatbot-support flow.
 *
 * Responsibilities:
 *
 * 1. Require the same level in two consecutive measurement windows.
 * 2. Show the default action when a new level is confirmed.
 * 3. Confirm increases before alerting.
 * 4. Stay silent when the confirmed level decreases.
 * 5. Reset when level 0 is detected.
 * 6. Wait after Accepted or "לא עכשיו".
 * 7. Show rotating calming messages.
 * 8. Repeat a dismissed action later.
 *
 * This class does not perform BotAction.
 * It only decides what the chatbot should display next.
 */
class DistressConfirmationManager(

    // Use a Compose-owned or ViewModel-owned coroutine scope.
    private val scope: CoroutineScope,

    // Number of matching measurement windows required for confirmation.
    private val requiredMatchingWindows: Int = 2,

    // Delay after a user response or calming message.
    private val cooldownMillis: Long = 5_000L
) {

    /**
     * Determines what should happen after a calming message.
     */
    private enum class FollowUpMode {

        // The previous action was accepted.
        // Continue showing calming messages only.
        AFTER_ACCEPT,

        // The previous action was dismissed.
        // Show a calming message and later repeat the same action.
        AFTER_DISMISS
    }

    // First unconfirmed level currently being observed.
    private var candidateLevel: Int? = null

    // Number of consecutive windows containing candidateLevel.
    private var candidateWindowCount: Int = 0

    // Stable level confirmed after enough matching windows.
    private val _confirmedLevel = MutableStateFlow(0)
    val confirmedLevel: StateFlow<Int> = _confirmedLevel

    // Current event that should be displayed by the chatbot.
    private val _uiEvent =
        MutableStateFlow<DistressUiEvent?>(null)

    val uiEvent: StateFlow<DistressUiEvent?> =
        _uiEvent

    // Used to give every UI event a unique ID.
    private var nextEventId: Long = 0L

    // Current delayed follow-up operation.
    private var cooldownJob: Job? = null

    // Determines what should follow the next calming message.
    private var followUpMode: FollowUpMode? = null

    // Exact suggestion dismissed with "לא עכשיו".
    private var dismissedSuggestion: BotSuggestion? = null

    /**
     * Stores the next calming-message index separately for every level.
     *
     * Example:
     * level 1 may currently be on message 2,
     * while level 2 may currently be on message 0.
     */
    private val nextCalmingIndexByLevel =
        mutableMapOf<Int, Int>()

    /**
     * Receives one completed measurement window.
     */
    fun processWindow(window: DistressWindowResult) {
        processLevel(window.level)
    }

    /**
     * Receives the final level from one measurement window.
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

        /**
         * According to the selected behavior,
         * one level-0 window immediately resets the short-term process.
         */
        if (level == 0) {
            resetShortTermState()
            return
        }

        /**
         * If the new level equals the already confirmed level,
         * no additional confirmation is required.
         *
         * Cooldown behavior continues independently.
         */
        if (level == _confirmedLevel.value) {
            clearCandidate()

            Log.d(
                "DISTRESS_CONFIRM",
                "Confirmed level remains unchanged: $level"
            )

            return
        }

        /**
         * The new level is different from the confirmed level.
         * It may represent:
         *
         * - Initial distress
         * - An increase
         * - A decrease
         *
         * In all cases, require two matching windows.
         */
        if (candidateLevel == level) {
            candidateWindowCount += 1
        } else {
            candidateLevel = level
            candidateWindowCount = 1
        }

        Log.d(
            "DISTRESS_CONFIRM",
            "Candidate level=$candidateLevel, count=$candidateWindowCount"
        )

        if (candidateWindowCount < requiredMatchingWindows) {
            return
        }

        confirmCandidateLevel(level)
    }

    /**
     * Called after the required number of matching windows.
     */
    private fun confirmCandidateLevel(newLevel: Int) {
        val previousLevel = _confirmedLevel.value

        _confirmedLevel.value = newLevel
        clearCandidate()

        Log.d(
            "DISTRESS_CONFIRM",
            "Confirmed level changed: $previousLevel -> $newLevel"
        )

        when {

            /**
             * Initial confirmation:
             * 0 -> positive level.
             */
            previousLevel == 0 -> {
                beginNewAlertLevel(newLevel)
            }

            /**
             * Confirmed increase:
             * show the default action for the stronger level.
             */
            newLevel > previousLevel -> {
                beginNewAlertLevel(newLevel)
            }

            /**
             * Confirmed decrease:
             * update the internal level but do not alert.
             *
             * Cancel messages belonging to the stronger previous level.
             */
            newLevel < previousLevel -> {
                cancelCooldown()

                followUpMode = null
                dismissedSuggestion = null

                emitResetEvent()

                Log.d(
                    "DISTRESS_CONFIRM",
                    "Level decreased. Internal state updated without alert."
                )
            }
        }
    }

    /**
     * Starts a new support cycle for a newly confirmed level.
     */
    private fun beginNewAlertLevel(level: Int) {
        cancelCooldown()

        followUpMode = null
        dismissedSuggestion = null

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
     * Called when the user selects one of the action buttons.
     *
     * After acceptance:
     *
     * - Do not repeat the same action during the same distress event.
     * - Wait 5 seconds.
     * - If the same level remains confirmed, show a calming message.
     * - After the message is closed, another calming message may be scheduled.
     */
    fun onActionAccepted() {
        if (_confirmedLevel.value <= 0) {
            return
        }

        followUpMode = FollowUpMode.AFTER_ACCEPT
        dismissedSuggestion = null

        // Remove the accepted action suggestion from the manager.
        clearCurrentUiEvent()

        /**
         * Do not schedule a calming message yet.
         *
         * The chatbot first displays the success message related
         * to the selected action.
         *
         * The calming-message cooldown starts only after that
         * success message is closed or finishes automatically.
         */
        cancelCooldown()

        Log.d(
            "DISTRESS_CONFIRM",
            "Action accepted. Waiting for the success message to finish."
        )
    }

    /**
     * Called after the success message belonging to an accepted
     * action has been closed or has finished automatically.
     *
     * From this moment, wait five seconds and then show a
     * calming message if the same confirmed distress level remains.
     */
    fun onAcceptedActionMessageClosed() {
        if (
            _confirmedLevel.value <= 0 ||
            followUpMode != FollowUpMode.AFTER_ACCEPT
        ) {
            return
        }

        scheduleCalmingMessage()

        Log.d(
            "DISTRESS_CONFIRM",
            "Accepted-action success message closed. Calming message scheduled."
        )
    }

    /**
     * Called when the user presses "לא עכשיו".
     *
     * We store the exact suggestion because it should return later.
     */
    fun onSuggestionDismissed(
        suggestion: BotSuggestion
    ) {
        if (_confirmedLevel.value <= 0) {
            return
        }

        followUpMode = FollowUpMode.AFTER_DISMISS
        dismissedSuggestion = suggestion

        clearCurrentUiEvent()
        scheduleCalmingMessage()

        Log.d(
            "DISTRESS_CONFIRM",
            "Suggestion dismissed: ${suggestion.id}"
        )
    }

    /**
     * Called after the user closes a calming message.
     *
     * Accepted path:
     * wait and show another calming message.
     *
     * Dismissed path:
     * wait and repeat the exact dismissed suggestion.
     */
    fun onCalmingMessageClosed() {
        clearCurrentUiEvent()

        when (followUpMode) {
            FollowUpMode.AFTER_ACCEPT -> {
                scheduleCalmingMessage()
            }

            FollowUpMode.AFTER_DISMISS -> {
                scheduleDismissedSuggestion()
            }

            null -> Unit
        }
    }

    /**
     * Waits before showing a calming message.
     */
    private fun scheduleCalmingMessage() {
        cancelCooldown()

        val expectedLevel = _confirmedLevel.value

        if (expectedLevel <= 0) {
            return
        }

        cooldownJob = scope.launch {
            delay(cooldownMillis)

            /**
             * Only show the message if the same confirmed level
             * still exists after the cooldown.
             */
            if (_confirmedLevel.value != expectedLevel) {
                return@launch
            }

            val message =
                getNextCalmingMessage(expectedLevel)

            if (message.isBlank()) {
                return@launch
            }

            emitEvent(
                DistressUiEvent.ShowCalmingMessage(
                    eventId = newEventId(),
                    level = expectedLevel,
                    message = message
                )
            )

            Log.d(
                "DISTRESS_CONFIRM",
                "Calming message emitted for level $expectedLevel"
            )
        }
    }

    /**
     * Waits and then repeats the exact previously dismissed suggestion.
     */
    private fun scheduleDismissedSuggestion() {
        cancelCooldown()

        val expectedLevel = _confirmedLevel.value
        val suggestionToRepeat = dismissedSuggestion

        if (
            expectedLevel <= 0 ||
            suggestionToRepeat == null
        ) {
            return
        }

        cooldownJob = scope.launch {
            delay(cooldownMillis)

            if (_confirmedLevel.value != expectedLevel) {
                return@launch
            }

            emitEvent(
                DistressUiEvent.ShowExactSuggestion(
                    eventId = newEventId(),
                    suggestion = suggestionToRepeat
                )
            )

            Log.d(
                "DISTRESS_CONFIRM",
                "Repeating dismissed suggestion: ${suggestionToRepeat.id}"
            )
        }
    }

    /**
     * Returns the next calming message using rotation.
     *
     * This guarantees that the same message is not shown
     * twice consecutively while other messages are available.
     */
    private fun getNextCalmingMessage(
        level: Int
    ): String {
        val messages =
            CalmingMessageCatalog.getMessagesForLevel(level)

        if (messages.isEmpty()) {
            return ""
        }

        val currentIndex =
            nextCalmingIndexByLevel[level] ?: 0

        val safeIndex =
            currentIndex % messages.size

        val selectedMessage =
            messages[safeIndex]

        nextCalmingIndexByLevel[level] =
            (safeIndex + 1) % messages.size

        return selectedMessage
    }

    /**
     * Immediately resets the short-term distress process.
     *
     * Long-term personalization history can be stored elsewhere later.
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

        followUpMode = null
        dismissedSuggestion = null

        /**
         * Reset calming-message rotation for a new distress event.
         *
         * Remove this line later if you prefer rotation to continue
         * between separate distress events.
         */
        nextCalmingIndexByLevel.clear()

        if (hadActiveState) {
            emitResetEvent()
        }

        Log.d(
            "DISTRESS_CONFIRM",
            "Short-term distress state reset at level 0."
        )
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