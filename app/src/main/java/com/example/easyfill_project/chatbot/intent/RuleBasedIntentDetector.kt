package com.example.easyfill_project.chatbot.intent

import com.example.easyfill_project.chatbot.model.BotIntent

class RuleBasedIntentDetector : IntentDetector {

    override fun detectIntent(userMessage: String): BotIntent {
        val text = userMessage.trim().lowercase()

        return when {
            // הסבר מסך
            text.contains("מה עושים") ||
                    text.contains("איפה אני") ||
                    text.contains("תסביר") ||
                    text.contains("המסך הזה") -> {
                BotIntent.EXPLAIN_SCREEN
            }

            // הסבר שדה
            text.contains("מה לרשום") ||
                    text.contains("מה צריך למלא") ||
                    text.contains("השדה הזה") ||
                    text.contains("לא יודע מה לכתוב") -> {
                BotIntent.EXPLAIN_FIELD
            }

            // כיבוי הקראה אוטומטית
            text.contains("תכבה הקראה אוטומטית") ||
                    text.contains("כבה הקראה אוטומטית") ||
                    text.contains("תבטל הקראה אוטומטית") ||
                    text.contains("בלי הקראה אוטומטית") -> {
                BotIntent.DISABLE_AUTO_READ
            }

            // הפעלת הקראה אוטומטית
            text.contains("תפעיל הקראה אוטומטית") ||
                    text.contains("הפעל הקראה אוטומטית") ||
                    text.contains("אני רוצה הקראה אוטומטית") ||
                    text.contains("תקריא כל מסך") -> {
                BotIntent.ENABLE_AUTO_READ
            }

            // עצירת הקראה רגילה
            text.contains("תפסיק להקריא") ||
                    text.contains("עצור הקראה") ||
                    text.contains("תעצור את ההקראה") ||
                    text.contains("די להקריא") -> {
                BotIntent.STOP_READING
            }

            // הקראה רגילה
            text.contains("תקריא") ||
                    text.contains("להקריא") ||
                    text.contains("תקרא לי") ||
                    text.contains("תשמיע לי") -> {
                BotIntent.READ_ALOUD
            }

            // כיבוי מוזיקה
            text.contains("תכבה מוזיקה") ||
                    text.contains("תפסיק מוזיקה") ||
                    text.contains("עצור מוזיקה") ||
                    text.contains("כבה צליל") ||
                    text.contains("תכבה את המוזיקה") ||
                    text.contains("בלי מוזיקה") -> {
                BotIntent.STOP_BACKGROUND_MUSIC
            }

            // צלילי טבע
            text.contains("צלילי טבע") ||
                    text.contains("טבע") ||
                    text.contains("שים טבע") ||
                    text.contains("תפעיל טבע") -> {
                BotIntent.PLAY_NATURE_SOUND
            }

            // מוזיקת מדיטציה / רגועה
            text.contains("מדיטציה") ||
                    text.contains("מוזיקה למדיטציה") ||
                    text.contains("מוזיקה רגועה") ||
                    text.contains("שים מוזיקה") ||
                    text.contains("שים לי מוזיקה") ||
                    text.contains("תפעיל מוזיקה") ||
                    text.contains("תפעיל צליל") -> {
                BotIntent.PLAY_CALM_MUSIC
            }

            // צלילי נגינה
            text.contains("נגינה") ||
                    text.contains("כינור") ||
                    text.contains("צלילי נגינה") ||
                    text.contains("מוזיקה עם נגינה") -> {
                BotIntent.PLAY_INSTRUMENT_SOUND
            }

            // גודל טקסט קטן
            text.contains("טקסט קטן יותר") ||
                    text.contains("כתב קטן יותר") ||
                    text.contains("תקטין את הטקסט") ||
                    text.contains("תקטין את הכתב") -> {
                BotIntent.SET_FONT_SMALL
            }

            // גודל טקסט רגיל
            text.contains("טקסט רגיל") ||
                    text.contains("כתב רגיל") ||
                    text.contains("תחזיר גודל טקסט") ||
                    text.contains("גודל טקסט רגיל") -> {
                BotIntent.SET_FONT_NORMAL
            }

            // גודל טקסט גדול
            text.contains("להגדיל טקסט") ||
                    text.contains("תגדיל טקסט") ||
                    text.contains("תגדיל את הטקסט") ||
                    text.contains("כתב גדול") ||
                    text.contains("טקסט גדול") ||
                    text.contains("טקסט קטן") ||
                    text.contains("קשה לי לקרוא") ||
                    text.contains("קשה לקרוא") ||
                    text.contains("אני לא מצליח לקרוא") ||
                    text.contains("לא מצליח לקרוא") ||
                    text.contains("הכתב לא ברור") ||
                    text.contains("הטקסט לא ברור") ||
                    text.contains("כתב קטן") -> {
                BotIntent.SET_FONT_LARGE
            }

            // מסך גודל טקסט
            text.contains("גודל טקסט") ||
                    text.contains("הגדרות טקסט") ||
                    text.contains("הגדרות כתב") -> {
                BotIntent.OPEN_FONT_SIZE_SETTINGS
            }

            // צבעים רגילים
            text.contains("צבעים רגילים") ||
                    text.contains("תחזיר צבעים") ||
                    text.contains("מצב צבעים רגיל") ||
                    text.contains("ניגודיות רגילה") -> {
                BotIntent.SET_CONTRAST_DEFAULT
            }

            // ניגודיות גבוהה
            text.contains("ניגודיות גבוהה") ||
                    text.contains("צבעים חזקים") ||
                    text.contains("קונטרסט גבוה") ||
                    text.contains("קשה לי לראות") -> {
                BotIntent.SET_CONTRAST_HIGH
            }

            // צבעים רגועים / ניגודיות נמוכה
            text.contains("צבעים רגועים") ||
                    text.contains("צבעים עדינים") ||
                    text.contains("ניגודיות נמוכה") ||
                    text.contains("צבעים פחות חזקים") ||
                    text.contains("יותר נוח לעיניים") -> {
                BotIntent.SET_CONTRAST_LOW
            }

            // מסך צבעים
            text.contains("ניגודיות") ||
                    text.contains("צבעים") ||
                    text.contains("צבעי הממשק") ||
                    text.contains("הגדרות צבעים") -> {
                BotIntent.OPEN_CONTRAST_SETTINGS
            }

            // מסך צלילי רקע
            text.contains("צלילי רקע") ||
                    text.contains("מוזיקת רקע") ||
                    text.contains("מנגינה") ||
                    text.contains("רעש רקע") ||
                    text.contains("משהו מרגיע ברקע") -> {
                BotIntent.OPEN_BACKGROUND_SOUNDS
            }

            // מסך התאמה אישית
            text.contains("התאמה אישית") ||
                    text.contains("הגדרות נוחות") ||
                    text.contains("הגדרות נגישות") -> {
                BotIntent.OPEN_PERSONAL_SETTINGS
            }

            // קלט קולי / מיקרופון
            text.contains("דיבור") ||
                    text.contains("קלט קולי") ||
                    text.contains("לדבר במקום לכתוב") ||
                    text.contains("קשה לי להקליד") ||
                    text.contains("מיקרופון") -> {
                BotIntent.VOICE_INPUT_HELP
            }

            // בלבול
            text.contains("לא הבנתי") ||
                    text.contains("אני לא מבין") ||
                    text.contains("מסובך") ||
                    text.contains("מבולבל") -> {
                BotIntent.USER_CONFUSED
            }

            // לחץ / עומס
            text.contains("קשה לי") ||
                    text.contains("אין לי כוח") ||
                    text.contains("עמוס") ||
                    text.contains("לחוץ") ||
                    text.contains("מוצף") ||
                    text.contains("מעייף") -> {
                BotIntent.USER_STRESSED
            }

            else -> {
                BotIntent.GENERAL_HELP
            }
        }
    }
}