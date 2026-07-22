package com.example.easyfill_project.distress_scoring

import com.example.easyfill_project.chatbot.logic.BotSuggestion

/**
 * Events sent from DistressConfirmationManager
 * to FloatingChatOverlay.
 *
 * Every event has a unique eventId so Compose can process
 * each event only once.
 */
sealed class DistressUiEvent {

    abstract val eventId: Long

    /**
     * Requests the normal default suggestion for a newly
     * confirmed distress level.
     */
    data class ShowDefaultSuggestion(
        override val eventId: Long,
        val level: Int,
        val source: DistressAlertSource,
        // Suggestions accepted earlier in the current app session.
        // The builder must not offer these suggestions or equivalent categories.
        val excludedSuggestionIds: Set<String>
    ) : DistressUiEvent()

    /**
     * Re-displays the exact original suggestion that was
     * previously dismissed.
     */
    data class ShowExactSuggestion(
        override val eventId: Long,
        val suggestion: BotSuggestion
    ) : DistressUiEvent()

    /**
     * Requests a different unused alternative action.
     *
     * source is required because a recording may already have ended
     * before FloatingChatOverlay builds the suggestion.
     */
    data class ShowAlternativeSuggestion(
        override val eventId: Long,
        val level: Int,
        val source: DistressAlertSource,
        val excludedSuggestionIds: Set<String>
    ) : DistressUiEvent()

    /**
     * Displays a calming message without action buttons.
     */
    data class ShowCalmingMessage(
        override val eventId: Long,
        val level: Int,
        val message: String
    ) : DistressUiEvent()

    /**
     * Clears the current chatbot alert and popup.
     */
    data class Reset(
        override val eventId: Long
    ) : DistressUiEvent()

    enum class DistressAlertSource {
        FORM_FILLING,
        VOICE_RECORDING
    }
}
