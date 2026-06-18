package com.example.easyfill_project.chatbot.intent

import com.example.easyfill_project.chatbot.model.BotIntent
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog

import com.example.easyfill_project.chatbot.semantic.AppIntentCatalog

class RuleBasedIntentDetector : IntentDetector {

    override fun detectIntent(userMessage: String): BotIntent {
        val text = userMessage.trim().lowercase()

        val soundOption = PersonalizationCatalog.findSoundInText(text)
        val fontSizeOption = PersonalizationCatalog.findFontSizeInText(text)
        val contrastOption = PersonalizationCatalog.findContrastInText(text)
        val catalogIntent = AppIntentCatalog.findIntentByRule(text)
        return when {
            // עזרה כללית / יכולות הבוט
            text.contains("מה אתה יודע לעשות") ||
                    text.contains("איך אתה יכול לעזור") ||
                    text.contains("במה אתה יכול לעזור") ||
                    text.contains("מה האפשרויות שלך") ||
                    text.contains("מה היכולות שלך") ||
                    text == "עזרה" -> {
                BotIntent.GeneralHelp
            }

            // הסבר מסך
            text.contains("מה עושים") ||
                    text.contains("איפה אני") ||
                    text.contains("תסביר") ||
                    text.contains("המסך הזה") ||
                    text.contains("לא הבנתי") ||
                    text.contains("אני לא מבין") -> {
                BotIntent.ExplainScreen
            }

            // הסבר שדה
            text.contains("מה לרשום") ||
                    text.contains("מה צריך למלא") ||
                    text.contains("השדה הזה") ||
                    text.contains("לא יודע מה לכתוב") -> {
                BotIntent.ExplainField
            }

            // ניווט לדף הבית
            text.contains("דף הבית") ||
                    text.contains("מסך הבית") ||
                    text.contains("תחזיר אותי לבית") ||
                    text.contains("חזור לבית") ||
                    text.contains("לך לבית") -> {
                BotIntent.OpenHome
            }

            // ניווט להתקדמות טפסים
            text.contains("התקדמות") ||
                    text.contains("התקדמות הטפסים") ||
                    text.contains("מצב הטפסים") ||
                    text.contains("טפסים שהתחלתי") ||
                    text.contains("מה כבר מילאתי") ||
                    text.contains("כמה מילאתי") ||
                    text.contains("איפה אני רואה את ההתקדמות") -> {
                BotIntent.OpenFormsProgress
            }

            // ניווט לפרופיל
            text.contains("פרופיל") ||
                    text.contains("הפרופיל שלי") ||
                    text.contains("פרטים אישיים") ||
                    text.contains("הפרטים שלי") ||
                    text.contains("חשבון אישי") ||
                    text.contains("לעדכן פרטים") -> {
                BotIntent.OpenProfile
            }

            // ניווט למדריך
            text.contains("מדריך") ||
                    text.contains("הדרכה") ||
                    text.contains("איך משתמשים") ||
                    text.contains("איך האפליקציה עובדת") ||
                    text.contains("הסבר כללי על האפליקציה") -> {
                BotIntent.OpenGuidance
            }

            // ניווט להעלאת PDF
            // ניווט להעלאת PDF
            text.contains("העלאת pdf") ||
                    text.contains("להעלות pdf") ||
                    text.contains("מעלים pdf") ||
                    text.contains("איך מעלים pdf") ||
                    text.contains("להעלות טופס") ||
                    text.contains("מעלים טופס") ||
                    text.contains("איך מעלים טופס") ||
                    text.contains("העלאת טופס") ||
                    text.contains("להעלות קובץ") ||
                    text.contains("מעלים קובץ") ||
                    text.contains("איך מעלים קובץ") ||
                    text.contains("סריקת טופס") ||
                    text.contains("טופס חדש מהמחשב") -> {
                BotIntent.OpenUploadPdf
            }

            // ניווט למסך בחירת טפסים
            text.contains("איפה הטפסים") ||
                    text.contains("בחירת טפסים") ||
                    text.contains("רשימת טפסים") ||
                    text.contains("רשימת הטפסים") ||
                    text.contains("אני מחפש טופס") ||
                    text.contains("אני רוצה למלא טופס") ||
                    text.contains("להתחיל טופס") ||
                    text.contains("טופס סיוע") ||
                    text.contains("טופס דיור") ||
                    text.contains("סיוע בדיור") -> {
                BotIntent.OpenFormOptions
            }

            // כיבוי הקראה אוטומטית
            text.contains("תכבה הקראה אוטומטית") ||
                    text.contains("כבה הקראה אוטומטית") ||
                    text.contains("תבטל הקראה אוטומטית") ||
                    text.contains("בלי הקראה אוטומטית") -> {
                BotIntent.DisableAutoRead
            }

            // הפעלת הקראה אוטומטית
            text.contains("תפעיל הקראה אוטומטית") ||
                    text.contains("הפעל הקראה אוטומטית") ||
                    text.contains("אני רוצה הקראה אוטומטית") ||
                    text.contains("תקריא כל מסך") -> {
                BotIntent.EnableAutoRead
            }

            // עצירת הקראה רגילה
            text.contains("תפסיק להקריא") ||
                    text.contains("עצור הקראה") ||
                    text.contains("תעצור את ההקראה") ||
                    text.contains("די להקריא") -> {
                BotIntent.StopReading
            }

            // הקראה רגילה
            text.contains("תקריא") ||
                    text.contains("להקריא") ||
                    text.contains("תקרא לי") ||
                    text.contains("תשמיע לי") -> {
                BotIntent.ReadAloud
            }

            // כיבוי מוזיקה
            text.contains("תכבה מוזיקה") ||
                    text.contains("תפסיק מוזיקה") ||
                    text.contains("עצור מוזיקה") ||
                    text.contains("כבה צליל") ||
                    text.contains("תכבה את המוזיקה") ||
                    text.contains("בלי מוזיקה") -> {
                BotIntent.StopBackgroundMusic
            }

            // הפעלת צליל מתוך הקטלוג
            soundOption != null -> {
                BotIntent.PlaySound(soundOption)
            }

            // שינוי גודל טקסט מתוך הקטלוג
            fontSizeOption != null -> {
                BotIntent.SetFontSize(fontSizeOption)
            }

            // פתיחת מסך גודל טקסט
            text.contains("גודל טקסט") ||
                    text.contains("הגדרות טקסט") ||
                    text.contains("הגדרות כתב") -> {
                BotIntent.OpenFontSizeSettings
            }

            // שינוי ניגודיות מתוך הקטלוג
            contrastOption != null -> {
                BotIntent.SetContrast(contrastOption)
            }

            // פתיחת מסך ניגודיות
            text.contains("ניגודיות") ||
                    text.contains("צבעים") ||
                    text.contains("צבעי הממשק") ||
                    text.contains("הגדרות צבעים") -> {
                BotIntent.OpenContrastSettings
            }

            // פתיחת מסך צלילי רקע
            text.contains("צלילי רקע") ||
                    text.contains("מוזיקת רקע") ||
                    text.contains("מנגינה") ||
                    text.contains("רעש רקע") ||
                    text.contains("משהו מרגיע ברקע") -> {
                BotIntent.OpenBackgroundSounds
            }

            // פתיחת מסך התאמה אישית
            text.contains("התאמה אישית") ||
                    text.contains("הגדרות נוחות") ||
                    text.contains("הגדרות נגישות") -> {
                BotIntent.OpenPersonalSettings
            }

            // קלט קולי
            text.contains("דיבור") ||
                    text.contains("קלט קולי") ||
                    text.contains("לדבר במקום לכתוב") ||
                    text.contains("קשה לי להקליד") ||
                    text.contains("מיקרופון") -> {
                BotIntent.VoiceInputHelp
            }

            // בלבול / עומס
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
            catalogIntent != null -> {
                catalogIntent
            }

            else -> {
                BotIntent.GeneralHelp
            }
        }
    }
}