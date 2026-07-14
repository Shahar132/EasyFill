package com.example.easyfill_project.chatbot.logic

import com.example.easyfill_project.chatbot.model.BotAction

/**
 * Provides the message displayed after the user selects
 * an action from the chatbot suggestion card.
 * This class only creates text.
 * It does not perform the action itself.
 */
object BotActionMessageProvider {

    /**
     * Returns a user-friendly message that matches the selected action.
     */
    fun getMessage(action: BotAction): String {
        return when (action) {

            // Shown after the app starts reading the full screen.
            BotAction.ReadAloud ->
                "הקראתי עבורך את ההסבר של המסך הנוכחי."

            // Shown after the app starts reading the selected field.
            BotAction.ReadCurrentField ->
                "הקראתי עבורך את ההסבר של השדה הנוכחי."

            // Shown after the selected background sound starts playing.
            is BotAction.PlaySound ->
                "הפעלתי עבורך את ${action.option.displayName}. אפשר להמשיך בקצב שנוח לך."

            // Shown after the selected color theme is applied.
            is BotAction.SetContrast ->
                "שיניתי את צבעי המסך ל${action.option.displayName}. מקווה שעכשיו יהיה לך נוח יותר לקרוא."

            // Shown after the selected font size is applied.
            is BotAction.SetFontSize ->
                "שיניתי את גודל הטקסט ל${action.option.displayName}. מקווה שעכשיו הטקסט ברור ונוח יותר."

            // Displays support information inside the chatbot card.
            BotAction.ShowEmergencyContacts ->
                """
אפשר לעצור לרגע ולפנות לאדם שאת/ה סומך/ת עליו.

אפשר גם לפנות אל:
• בן משפחה או חבר קרוב
• יועץ/ת, עובד/ת סוציאלי/ת או רופא/ה
• מוקד חירום ער"ן במספר 1201 במקרה של צורך דחוף


אפשר להמשיך להשתמש גם באפשרויות הסיוע באפליקציה:
• הקראת המסך או השדה הנוכחי
• שינוי גודל הטקסט
• שינוי צבעי המסך
• הפעלת מוזיקת רקע רגועה
                """.trimIndent()

            // No action was performed, so no message is required.
            BotAction.None ->
                ""
        }
    }
}