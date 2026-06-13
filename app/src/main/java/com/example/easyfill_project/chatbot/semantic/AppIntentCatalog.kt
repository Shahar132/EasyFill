package com.example.easyfill_project.chatbot.semantic

import com.example.easyfill_project.chatbot.model.BotIntent

data class AppIntentExample(
    val text: String,
    val intent: BotIntent
)

data class AppIntentGroup(
    val intent: BotIntent,
    val examples: List<String>
)

object AppIntentCatalog {

    val groups = listOf(

        intentGroup(
            intent = BotIntent.ExplainScreen,
            examples = listOf(
                "מה עושים פה",
                "אני לא מבין את המסך הזה",
                "תסביר לי איפה אני נמצא",
                "מה השלב הבא",
                "המסך הזה לא ברור לי"
            )
        ),

        intentGroup(
            intent = BotIntent.ExplainField,
            examples = listOf(
                "מה צריך למלא בשדה הזה",
                "אני לא יודע מה לרשום פה",
                "תסביר לי את השדה הזה",
                "מה לרשום כאן"
            )
        ),

        intentGroup(
            intent = BotIntent.OpenHome,
            examples = listOf(
                "תחזיר אותי לדף הבית",
                "אני רוצה לחזור למסך הראשי",
                "פתח את דף הבית",
                "לך לבית"
            )
        ),

        intentGroup(
            intent = BotIntent.OpenFormOptions,
            examples = listOf(
                "איפה הטפסים",
                "אני מחפש טופס",
                "אני רוצה להתחיל למלא טופס",
                "איפה מוצאים טופס סיוע בדיור",
                "פתח לי את רשימת הטפסים"
            )
        ),

        intentGroup(
            intent = BotIntent.OpenFormsProgress,
            examples = listOf(
                "איפה ההתקדמות שלי",
                "איזה טפסים כבר התחלתי",
                "אני רוצה לראות מה כבר מילאתי",
                "מה מצב הטפסים שלי",
                "כמה מילאתי"
            )
        ),

        intentGroup(
            intent = BotIntent.OpenProfile,
            examples = listOf(
                "איפה הפרטים האישיים שלי",
                "אני רוצה לעדכן פרופיל",
                "פתח את החשבון האישי שלי",
                "איפה הפרופיל שלי"
            )
        ),

        intentGroup(
            intent = BotIntent.OpenGuidance,
            examples = listOf(
                "איך משתמשים באפליקציה",
                "אני רוצה לראות מדריך",
                "תפתח לי הסבר כללי על האפליקציה",
                "איך האפליקציה עובדת"
            )
        ),

        intentGroup(
            intent = BotIntent.OpenUploadPdf,
            examples = listOf(
                "איך מעלים טופס",
                "אני רוצה להעלות קובץ",
                "איפה מעלים pdf",
                "אני רוצה להעלות טופס חדש",
                "איך מוסיפים טופס חדש"
            )
        ),

        intentGroup(
            intent = BotIntent.GeneralHelp,
            examples = listOf(
                "מה אתה יודע לעשות",
                "איך אתה יכול לעזור לי",
                "במה אתה יכול לעזור",
                "מה האפשרויות שלך"
            )
        )
    )

    val examples: List<AppIntentExample> =
        groups.flatMap { group ->
            group.examples.map { exampleText ->
                AppIntentExample(
                    text = exampleText,
                    intent = group.intent
                )
            }
        }

    fun findIntentByRule(userText: String): BotIntent? {
        val cleanUserText = normalize(userText)

        return examples.firstOrNull { example ->
            val cleanExample = normalize(example.text)

            cleanUserText.contains(cleanExample) ||
                    cleanExample.contains(cleanUserText)
        }?.intent
    }

    private fun normalize(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace("?", "")
            .replace("!", "")
            .replace(".", "")
            .replace(",", "")
    }

    private fun intentGroup(
        intent: BotIntent,
        examples: List<String>
    ): AppIntentGroup {
        return AppIntentGroup(
            intent = intent,
            examples = examples
        )
    }


    fun findIntentByRuleWithScore(userText: String): IntentMatch? {
        val cleanUserText = normalize(userText)

        val match = examples.firstOrNull { example ->
            val cleanExample = normalize(example.text)

            cleanUserText.contains(cleanExample) ||
                    cleanExample.contains(cleanUserText)
        }

        return match?.let {
            IntentMatch(
                intent = it.intent,
                score = 1.0f,
                matchedText = it.text
            )
        }
    }
}

