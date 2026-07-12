package com.example.easyfill_project.chatbot.model

import com.example.easyfill_project.chatbot.personalization.ContrastOption
import com.example.easyfill_project.chatbot.personalization.FontSizeOption
import com.example.easyfill_project.chatbot.personalization.SoundOption

sealed class BotAction {

    // Represents an option that performs no app action.
    object None : BotAction()

    // Reads all prepared text for the current screen.
    object ReadAloud : BotAction()

    // Reads only the explanation of the currently focused field.
    object ReadCurrentField : BotAction()

    // Displays the support-information message.
    object ShowEmergencyContacts : BotAction()

    // Plays the sound stored inside option.
    data class PlaySound(
        val option: SoundOption
    ) : BotAction()

    // Applies the selected color/contrast option.
    data class SetContrast(
        val option: ContrastOption
    ) : BotAction()

    // Applies the selected font-size option.
    data class SetFontSize(
        val option: FontSizeOption
    ) : BotAction()
}