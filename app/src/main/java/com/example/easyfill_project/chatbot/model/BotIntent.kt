package com.example.easyfill_project.chatbot.model

import com.example.easyfill_project.chatbot.personalization.ContrastOption
import com.example.easyfill_project.chatbot.personalization.FontSizeOption
import com.example.easyfill_project.chatbot.personalization.SoundOption

sealed class BotIntent {

    object ExplainScreen : BotIntent()
    object ExplainField : BotIntent()

    object UserConfused : BotIntent()
    object UserStressed : BotIntent()

    object ReadAloud : BotIntent()
    object StopReading : BotIntent()

    object EnableAutoRead : BotIntent()
    object DisableAutoRead : BotIntent()

    object OpenPersonalSettings : BotIntent()
    object OpenContrastSettings : BotIntent()
    object OpenFontSizeSettings : BotIntent()
    object OpenBackgroundSounds : BotIntent()

    object OpenHome : BotIntent()
    object OpenFormOptions : BotIntent()
    object OpenFormsProgress : BotIntent()
    object OpenProfile : BotIntent()
    object OpenGuidance : BotIntent()
    object OpenUploadPdf : BotIntent()

    data class PlaySound(
        val option: SoundOption
    ) : BotIntent()

    object StopBackgroundMusic : BotIntent()

    data class SetContrast(
        val option: ContrastOption
    ) : BotIntent()

    data class SetFontSize(
        val option: FontSizeOption
    ) : BotIntent()

    object VoiceInputHelp : BotIntent()

    object GeneralHelp : BotIntent()
}