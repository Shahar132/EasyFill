package com.example.easyfill_project.chatbot.logic

import com.example.easyfill_project.chatbot.help.ScreenHelpCatalog
import com.example.easyfill_project.chatbot.intent.IntentDetector
import com.example.easyfill_project.chatbot.intent.RuleBasedIntentDetector
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotContext
import com.example.easyfill_project.chatbot.model.BotIntent
import com.example.easyfill_project.chatbot.model.BotResponse
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog
import android.util.Log

class ChatBotManager(
    private val intentDetector: IntentDetector = RuleBasedIntentDetector()
) {

    fun getResponse(
        userMessage: String,
        context: BotContext
    ): BotResponse {
        val intent = intentDetector.detectIntent(userMessage)
        val currentScreen = context.currentScreen
        val globalDistressScore = context.distressSnapshot.globalScore
        val appState = context.appState

        return when (intent) {

            BotIntent.ExplainScreen -> {
                BotResponse(
                    message = getScreenExplanation(currentScreen)
                )
            }

            BotIntent.ExplainField -> {
                BotResponse(
                    message = "הכול בסדר. ליד כל שדה יש כפתור השמעה שאפשר ללחוץ עליו כדי לשמוע הסבר על השדה. אפשר גם ללחוץ על המיקרופון כדי לדבר את התשובה במקום לכתוב."
                )
            }

            BotIntent.UserConfused -> {//if the user is confused
                BotResponse(
                    message = "אני יודע שזה יכול להיות מבלבל, נתקדם לאט. אפשר לשאול אותי מה עושים במסך הזה, או להשתמש בכפתורי ההשמעה והמיקרופון ליד השדות."
                )
            }

            BotIntent.UserStressed -> {//when user is in stress
                BotResponse(
                    message = "אני מבין שזה יכול להיות עמוס ומלחיץ. אפשר לעצור רגע, לקחת נשימה קצרה, ולהמשיך שלב אחד בכל פעם. אני כאן כדי לעזור."

                )
            }

            BotIntent.ReadAloud -> {
                if (appState.isTtsSpeaking) {
                    BotResponse(
                        message = "ההקראה כבר פועלת.",
                        action = BotAction.None
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל הקראה של הטקסט במסך.",
                        action = BotAction.ReadAloud
                    )
                }
            }

            BotIntent.StopReading -> {
                if (appState.isTtsSpeaking) {
                    BotResponse(
                        message = "בסדר, אני מפסיק את ההקראה.",
                        action = BotAction.StopReading
                    )
                } else {
                    BotResponse(
                        message = "אין כרגע הקראה פעילה.",
                        action = BotAction.None
                    )
                }
            }

            BotIntent.EnableAutoRead -> {
                if (appState.autoReadEnabled) {
                    BotResponse(
                        message = "ההקראה האוטומטית כבר פועלת.",
                        action = BotAction.None
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל הקראה אוטומטית לכל מסך.",
                        action = BotAction.EnableAutoRead
                    )
                }
            }

            BotIntent.DisableAutoRead -> {
                if (appState.autoReadEnabled) {
                    BotResponse(
                        message = "בסדר, אני מכבה את ההקראה האוטומטית.",
                        action = BotAction.DisableAutoRead
                    )
                } else {
                    BotResponse(
                        message = "ההקראה האוטומטית כבר כבויה.",
                        action = BotAction.None
                    )
                }
            }

            BotIntent.OpenHome -> {
                BotResponse(
                    message = "אפשר לחזור לדף הבית. להעביר אותך לשם?",
                    action = BotAction.OpenHome,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenFormOptions -> {
                BotResponse(
                    message = "נראה שאתה מחפש טופס או רוצה להתחיל מילוי. רוצה שאעביר אותך למסך בחירת הטפסים?",
                    action = BotAction.OpenFormOptions,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenFormsProgress -> {
                BotResponse(
                    message = "אפשר לראות את ההתקדמות שלך בטפסים שכבר התחלת. להעביר אותך למסך ההתקדמות?",
                    action = BotAction.OpenFormsProgress,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenProfile -> {
                BotResponse(
                    message = "אפשר לפתוח את הפרופיל האישי, שם נמצאים הפרטים שמשמשים למילוי אוטומטי. לפתוח?",
                    action = BotAction.OpenProfile,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenGuidance -> {
                BotResponse(
                    message = "אפשר לפתוח את מדריך המשתמש כדי לראות הסבר כללי על האפליקציה. לפתוח?",
                    action = BotAction.OpenGuidance,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenUploadPdf -> {
                BotResponse(
                    message = "אפשר לעבור למסך העלאת טופס PDF. להעביר אותך לשם?",
                    action = BotAction.OpenUploadPdf,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenPersonalSettings -> {
                BotResponse(
                    message = "אפשר לפתוח את מסך ההתאמה האישית, ושם לבחור מוזיקה, צבעים, גודל טקסט והקראה. לפתוח?",
                    action = BotAction.OpenPersonalSettings,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenContrastSettings -> {
                BotResponse(
                    message = "אפשר לפתוח את הגדרות הצבעים והניגודיות. לפתוח?",
                    action = BotAction.OpenContrastSettings,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenFontSizeSettings -> {
                BotResponse(
                    message = "אפשר לפתוח את הגדרות גודל הטקסט. לפתוח?",
                    action = BotAction.OpenFontSizeSettings,
                    requiresConfirmation = true
                )
            }

            BotIntent.OpenBackgroundSounds -> {
                BotResponse(
                    message = "אפשר לפתוח את מסך צלילי הרקע ולבחור צליל. לפתוח?",
                    action = BotAction.OpenBackgroundSounds,
                    requiresConfirmation = true
                )
            }

            is BotIntent.PlaySound -> {
                if (appState.selectedSound == intent.option.key) {
                    BotResponse(
                        message = "${intent.option.displayName} כבר פועל.",
                        action = BotAction.None
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל ${intent.option.displayName}.",
                        action = BotAction.PlaySound(intent.option)
                    )
                }
            }

            BotIntent.StopBackgroundMusic -> {
                if (appState.isMusicPlaying) {
                    BotResponse(
                        message = "בסדר, אני מפסיק את מוזיקת הרקע.",
                        action = BotAction.StopBackgroundMusic
                    )
                } else {
                    BotResponse(
                        message = "מוזיקת הרקע כבר כבויה.",
                        action = BotAction.None
                    )
                }
            }

            is BotIntent.SetContrast -> {
                if (appState.contrastMode == intent.option.mode.name) {
                    BotResponse(
                        message = "${intent.option.displayName} כבר פעיל.",
                        action = BotAction.None
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני משנה את הצבעים ל${intent.option.displayName}.",
                        action = BotAction.SetContrast(intent.option)
                    )
                }
            }

            is BotIntent.SetFontSize -> {
                if (appState.fontSizeMode == intent.option.mode.name) {
                    BotResponse(
                        message = "גודל הטקסט כבר מוגדר ל${intent.option.displayName}.",
                        action = BotAction.None
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני משנה את גודל הטקסט ל${intent.option.displayName}.",
                        action = BotAction.SetFontSize(intent.option)
                    )
                }
            }

            BotIntent.VoiceInputHelp -> {
                BotResponse(
                    message = "ליד שדות מתאימים אפשר ללחוץ על המיקרופון ולדבר את התשובה במקום להקליד. אפשר גם להשתמש בכפתור ההשמעה ליד השדה כדי לשמוע הסבר."
                )
            }

            BotIntent.GeneralHelp -> {
                BotResponse(
                    message = """
                        אני יכול לעזור לך בכמה דברים:
                        
                        • להסביר מה עושים במסך הנוכחי
                        • להסביר שדות בטופס
                        • להעביר אותך למסך בחירת טפסים
                        • להראות את התקדמות הטפסים שלך
                        • לפתוח את הפרופיל האישי
                        • לפתוח את מדריך המשתמש
                        • לפתוח את מסך העלאת הטפסים
                        • להקריא את הטקסט במסך
                        • להפעיל או לכבות הקראה אוטומטית
                        • להפעיל או לכבות צלילי רקע
                        • לשנות גודל טקסט
                        • לשנות צבעים וניגודיות
                        • לעזור כשמשהו לא ברור או מרגיש עמוס
                    """.trimIndent()
                )
            }
        }
    }
    // support logic - based on detected score.
//    fun getDistressSuggestion(context: BotContext): BotResponse? {
//        val handScore = context.distressSnapshot.touchScore
//        val voiceScore = context.distressSnapshot.voiceScore
//        val combinedScore = handScore + voiceScore
//
//        val severityLevel = when (combinedScore) {
//            0 -> 0
//            in 1..2 -> 1
//            in 3..4 -> 2
//            in 5..6 -> 3
//            else -> 4
//        }
//        //return boolean
//        val voiceIsDominant = voiceScore > handScore
//        val appState = context.appState
//
//        return when (severityLevel) {
//            0 -> null
//
//            1 -> BotResponse(
//                message = if (voiceIsDominant) {//voice true
//                    "רמה 1 של קול ."
//                } else {//false - hand is stronger
//                    "רמה 1 של ידים."
//                }
//            )
//
//            2 -> BotResponse(
//                message = if (voiceIsDominant) {//voice true
//                    "רמה 2 של קול?"
//                } else { //hand true
//                    "רמה 2 של ידים?"
//                },
//                action = if (voiceIsDominant) {
//                    BotAction.EnableAutoRead
//                } else {
//                    BotAction.SetFontSize(PersonalizationCatalog.largeFont)
//                },
//                requiresConfirmation = true
//            )
//
//            3 -> BotResponse(
//                message = if (voiceIsDominant) {//voice true
//                    "רמה 3 של קול?"
//                } else {//hand true
//                    "רמה 3 של ידים?"
//                },
//                action = BotAction.SetContrast(PersonalizationCatalog.lowContrast),
//                requiresConfirmation = true
//            )
//
//            4 -> BotResponse(
//                message = if (voiceIsDominant) {//voice true
//                    "רמה 4 של קול??"
//                } else {//hand true
//                    "רמה 4 של ידים?"
//                },
//                action = BotAction.ShowEmergencyContacts,
//                requiresConfirmation = true
//            )
//
//            else -> null
//        }
//    }


//    fun getDistressSuggestion(context: BotContext): BotResponse? {
//        val snapshot = context.distressSnapshot
//
//        val totalScore = snapshot.globalScore
//
//        val severityLevel = when (totalScore) {
//            0 -> 0
//            1 -> 1
//            2 -> 2
//            3 -> 3
//            else -> 4
//        }
//
//        if (severityLevel == 0) {
//            return null
//        }
//
//        val scoresBySource = listOf(
//            "HAND" to snapshot.touchScore,
//            "VOICE" to snapshot.voiceScore,
//            "FACE" to snapshot.faceScore,
//            "TEXT" to snapshot.semanticTextScore,
//            "FORM" to snapshot.formBehaviorScore
//        )
//
//        val maxScore = scoresBySource.maxOf { it.second }
//
//        val dominantSources = scoresBySource
//            .filter { it.second == maxScore && it.second > 0 }
//            .map { it.first }
//
//        val dominantSource = when {
//            dominantSources.isEmpty() -> "NONE"
//            dominantSources.size > 1 -> "MULTIPLE"
//            else -> dominantSources.first()
//        }
//
//        val message = when (severityLevel) {
//
//            1 -> when (dominantSource) {
//                "HAND" -> "רמה 1 - ידיים"
//                "VOICE" -> "רמה 1 - קול"
//                "FACE" -> "רמה 1 - פנים"
//                "TEXT" -> "רמה 1 - טקסט"
//                "FORM" -> "רמה 1 - טופס"
//                "MULTIPLE" -> "רמה 1 - כמה מדדים"
//                else -> "רמה 1 - לא ידוע"
//            }
//
//            2 -> when (dominantSource) {
//                "HAND" -> "רמה 2 - ידיים"
//                "VOICE" -> "רמה 2 - קול"
//                "FACE" -> "רמה 2 - פנים"
//                "TEXT" -> "רמה 2 - טקסט"
//                "FORM" -> "רמה 2 - טופס"
//                "MULTIPLE" -> "רמה 2 - כמה מדדים"
//                else -> "רמה 2 - לא ידוע"
//            }
//
//            3 -> when (dominantSource) {
//                "HAND" -> "רמה 3 - ידיים"
//                "VOICE" -> "רמה 3 - קול"
//                "FACE" -> "רמה 3 - פנים"
//                "TEXT" -> "רמה 3 - טקסט"
//                "FORM" -> "רמה 3 - טופס"
//                "MULTIPLE" -> "רמה 3 - כמה מדדים"
//                else -> "רמה 3 - לא ידוע"
//            }
//
//            else -> when (dominantSource) {
//                "HAND" -> "רמה 4 - ידיים"
//                "VOICE" -> "רמה 4 - קול"
//                "FACE" -> "רמה 4 - פנים"
//                "TEXT" -> "רמה 4 - טקסט"
//                "FORM" -> "רמה 4 - טופס"
//                "MULTIPLE" -> "רמה 4 - כמה מדדים"
//                else -> "רמה 4 - לא ידוע"
//            }
//        }
//        return BotResponse(
//            message = message,
//            action = BotAction.None,
//            requiresConfirmation = false
//        )
//    }



    fun getDistressSuggestion(context: BotContext): BotResponse? {
        val snapshot = context.distressSnapshot

        val scoresBySource = listOf(
            "HAND" to snapshot.touchScore,
            "VOICE" to snapshot.voiceScore,
            "FACE" to snapshot.faceScore,
            "TEXT" to snapshot.semanticTextScore,
            "FORM" to snapshot.formBehaviorScore
        )

        val maxSourceScore = scoresBySource.maxOf { it.second }

        val severityLevel = getDistressSeverityLevelForMessage(
            totalScore = snapshot.globalScore,
            maxSourceScore = maxSourceScore
        )

        if (severityLevel == 0) {
            return null
        }

        val activeSources = scoresBySource
            .filter { it.second > 0 }

        val dominantSources = activeSources
            .filter { it.second == maxSourceScore }
            .map { it.first }

        val dominantSource = when {
            activeSources.isEmpty() -> "NONE"
            activeSources.size > 1 -> "MULTIPLE"
            dominantSources.isEmpty() -> "NONE"
            else -> dominantSources.first()
        }

        val sourceText = when (dominantSource) {
            "HAND" -> "ידיים"
            "VOICE" -> "קול"
            "FACE" -> "פנים"
            "TEXT" -> "טקסט"
            "FORM" -> "טופס"
            "MULTIPLE" -> "כמה מדדים"
            else -> "לא ידוע"
        }

        return BotResponse(
            message = "רמה $severityLevel - $sourceText",
            action = BotAction.None,
            requiresConfirmation = false
        )
    }



    private fun getDistressSeverityLevelForMessage(
        totalScore: Int,
        maxSourceScore: Int
    ): Int {
        val levelBySingleSource = when (maxSourceScore) {
            0 -> 0
            1 -> 1
            2 -> 2
            3 -> 3
            else -> 4
        }

        val levelByTotalScore = when (totalScore) {
            0 -> 0
            in 1..2 -> 1
            in 3..4 -> 2
            in 5..6 -> 3
            else -> 4
        }

        return maxOf(levelBySingleSource, levelByTotalScore)
    }
    private fun getScreenExplanation(currentScreen: String): String {
        return ScreenHelpCatalog.getExplanation(currentScreen)
    }
}