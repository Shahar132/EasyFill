package com.example.easyfill_project.distress_scoring

import com.example.easyfill_project.chatbot.logic.BotSuggestion

/**
 * Represents something the confirmation manager wants the chatbot UI to show.
 *
 * eventId is unique for every event.
 * It allows Compose to react even if two events contain similar information.
 */
sealed class DistressUiEvent {

    abstract val eventId: Long

    /**
     * Request the normal default action suggestion for a confirmed level.
     *
     * AppNavigation or FloatingChatOverlay will call BotSuggestionBuilder
     * using this level and the current application state.
     */
    data class ShowDefaultSuggestion(
        override val eventId: Long,
        val level: Int
    ) : DistressUiEvent()

    /**
     * Show the exact action suggestion again.
     *
     * This is used after the user previously pressed "לא עכשיו".
     * We keep the exact object because level 3 currently selects random sounds.
     */
    data class ShowExactSuggestion(
        override val eventId: Long,
        val suggestion: BotSuggestion
    ) : DistressUiEvent()

    /**
     * Show a general supportive message without an app action.
     */
    data class ShowCalmingMessage(
        override val eventId: Long,
        val level: Int,
        val message: String
    ) : DistressUiEvent()

    /**
     * Clear the current short-term chatbot alert.
     */
    data class Reset(
        override val eventId: Long
    ) : DistressUiEvent()
}