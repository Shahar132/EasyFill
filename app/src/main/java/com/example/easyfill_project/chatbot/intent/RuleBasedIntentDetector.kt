package com.example.easyfill_project.chatbot.intent

import com.example.easyfill_project.chatbot.model.BotIntent
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog

class RuleBasedIntentDetector : IntentDetector {

    override fun detectIntent(userMessage: String): BotIntent {
        val text = userMessage.trim().lowercase()

        val soundOption = PersonalizationCatalog.findSoundInText(text)
        val fontSizeOption = PersonalizationCatalog.findFontSizeInText(text)
        val contrastOption = PersonalizationCatalog.findContrastInText(text)

        return when {
            text.contains("מה עושים") ||
                    text.contains("איפה אני") ||
                    text.contains("תסביר") ||
                    text.contains("המסך הזה") -> {
                BotIntent.ExplainScreen
            }

            text.contains("מה לרשום") ||
                    text.contains("מה צריך למלא") ||
                    text.contains("השדה הזה") ||
                    text.contains("לא יודע מה לכתוב") -> {
                BotIntent.ExplainField
            }

            text.contains("תכבה הקראה אוטומטית") ||
                    text.contains("כבה הקראה אוטומטית") ||
                    text.contains("תבטל הקראה אוטומטית") ||
                    text.contains("בלי הקראה אוטומטית") -> {
                BotIntent.DisableAutoRead
            }

            text.contains("תפעיל הקראה אוטומטית") ||
                    text.contains("הפעל הקראה אוטומטית") ||
                    text.contains("אני רוצה הקראה אוטומטית") ||
                    text.contains("תקריא כל מסך") -> {
                BotIntent.EnableAutoRead
            }

            text.contains("תפסיק להקריא") ||
                    text.contains("עצור הקראה") ||
                    text.contains("תעצור את ההקראה") ||
                    text.contains("די להקריא") -> {
                BotIntent.StopReading
            }

            text.contains("תקריא") ||
                    text.contains("להקריא") ||
                    text.contains("תקרא לי") ||
                    text.contains("תשמיע לי") -> {
                BotIntent.ReadAloud
            }

            text.contains("תכבה מוזיקה") ||
                    text.contains("תפסיק מוזיקה") ||
                    text.contains("עצור מוזיקה") ||
                    text.contains("כבה צליל") ||
                    text.contains("תכבה את המוזיקה") ||
                    text.contains("בלי מוזיקה") -> {
                BotIntent.StopBackgroundMusic
            }

            soundOption != null -> {
                BotIntent.PlaySound(soundOption)
            }

            fontSizeOption != null -> {
                BotIntent.SetFontSize(fontSizeOption)
            }

            text.contains("גודל טקסט") ||
                    text.contains("הגדרות טקסט") ||
                    text.contains("הגדרות כתב") -> {
                BotIntent.OpenFontSizeSettings
            }

            contrastOption != null -> {
                BotIntent.SetContrast(contrastOption)
            }

            text.contains("ניגודיות") ||
                    text.contains("צבעים") ||
                    text.contains("צבעי הממשק") ||
                    text.contains("הגדרות צבעים") -> {
                BotIntent.OpenContrastSettings
            }

            text.contains("צלילי רקע") ||
                    text.contains("מוזיקת רקע") ||
                    text.contains("מנגינה") ||
                    text.contains("רעש רקע") ||
                    text.contains("משהו מרגיע ברקע") -> {
                BotIntent.OpenBackgroundSounds
            }

            text.contains("התאמה אישית") ||
                    text.contains("הגדרות נוחות") ||
                    text.contains("הגדרות נגישות") -> {
                BotIntent.OpenPersonalSettings
            }

            text.contains("דיבור") ||
                    text.contains("קלט קולי") ||
                    text.contains("לדבר במקום לכתוב") ||
                    text.contains("קשה לי להקליד") ||
                    text.contains("מיקרופון") -> {
                BotIntent.VoiceInputHelp
            }

            text.contains("לא הבנתי") ||
                    text.contains("אני לא מבין") ||
                    text.contains("מסובך") ||
                    text.contains("מבולבל") -> {
                BotIntent.UserConfused
            }

            text.contains("קשה לי") ||
                    text.contains("אין לי כוח") ||
                    text.contains("עמוס") ||
                    text.contains("לחוץ") ||
                    text.contains("מוצף") ||
                    text.contains("מעייף") -> {
                BotIntent.UserStressed
            }

            else -> {
                BotIntent.GeneralHelp
            }
        }
    }
}