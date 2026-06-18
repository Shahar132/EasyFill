package com.example.easyfill_project.chatbot.model

import com.example.easyfill_project.chatbot.personalization.ContrastOption
import com.example.easyfill_project.chatbot.personalization.FontSizeOption
import com.example.easyfill_project.chatbot.personalization.SoundOption

sealed class BotAction {

    object None : BotAction()

    object ReadAloud : BotAction()
    object StopReading : BotAction()

    object EnableAutoRead : BotAction()
    object DisableAutoRead : BotAction()

    object OpenPersonalSettings : BotAction()
    object OpenContrastSettings : BotAction()
    object OpenFontSizeSettings : BotAction()
    object OpenBackgroundSounds : BotAction()

    object OpenHome : BotAction()
    object OpenFormOptions : BotAction()
    object OpenFormsProgress : BotAction()
    object OpenProfile : BotAction()
    object OpenGuidance : BotAction()
    object OpenUploadPdf : BotAction()

    data class PlaySound(
        val option: SoundOption
    ) : BotAction()

    object StopBackgroundMusic : BotAction()

    data class SetContrast(
        val option: ContrastOption
    ) : BotAction()

    data class SetFontSize(
        val option: FontSizeOption
    ) : BotAction()
}