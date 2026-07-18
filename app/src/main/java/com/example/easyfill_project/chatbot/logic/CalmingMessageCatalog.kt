package com.example.easyfill_project.chatbot.logic

/**
 * Stores general calming messages.
 *
 * These messages do not perform an action.
 * They are different from BotSuggestion because they do not:
 *
 * - Change font size
 * - Change colors
 * - Start music
 * - Start TTS
 * - Display emergency information
 *
 * They only provide supportive text.
 */
object CalmingMessageCatalog {

    private val level1Messages = listOf(
        "קח/י את הזמן, אין צורך למהר.",
        "אפשר להמשיך בקצב שנוח לך.",
        "את/ה מתקדם/ת יפה.",
        "אפשר לעצור לרגע ולהמשיך כשנוח."
    )

    private val level2Messages = listOf(
        "אפשר לקחת נשימה איטית ולהמשיך בהדרגה.",
        "אין צורך לסיים הכול מיד.",
        "אפשר להתמקד רק בשדה אחד בכל פעם.",
        "אני כאן כדי לעזור לך להמשיך."
    )

    private val level3Messages = listOf(
        "כדאי לעצור לרגע ולבחור את אפשרות הסיוע שנוחה לך.",
        "אפשר להאט ולהיעזר באחת מאפשרויות התמיכה.",
        "אין צורך להתמודד עם הכול בבת אחת.",
        "אפשר לקחת הפסקה קצרה לפני שממשיכים."
    )

    private val level4Messages = listOf(
        "מומלץ לעצור ולבחור באפשרות הסיוע המתאימה לך.",
        "אפשר לפנות לאדם תומך או להשתמש בפרטי הסיוע.",
        "אין צורך להמשיך לפני שאת/ה מרגיש/ה מוכן/ה.",
        "אפשר לעצור כעת ולבקש עזרה."
    )

    /**
     * Returns the calming-message list belonging to a distress level.
     */
    fun getMessagesForLevel(level: Int): List<String> {
        return when (level) {
            1 -> level1Messages
            2 -> level2Messages
            3 -> level3Messages
            4 -> level4Messages
            else -> emptyList()
        }
    }
}