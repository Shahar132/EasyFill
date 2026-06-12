package com.example.easyfill_project.chatbot.logic

import com.example.easyfill_project.chatbot.intent.IntentDetector
import com.example.easyfill_project.chatbot.intent.RuleBasedIntentDetector
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotContext
import com.example.easyfill_project.chatbot.model.BotIntent
import com.example.easyfill_project.chatbot.model.BotResponse
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog

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

            BotIntent.UserConfused -> {
                BotResponse(
                    message = if (globalDistressScore >= 60) {
                        "זה בסדר, נתקדם לאט. נראה שהשלב הזה קצת עמוס. אפשר לפתוח את מסך ההתאמה האישית כדי לשנות גודל טקסט, צבעים או צלילי רקע. לפתוח?"
                    } else {
                        "זה בסדר, נתקדם לאט. אפשר לשאול אותי מה עושים במסך הזה, או להשתמש בכפתורי ההשמעה והמיקרופון ליד השדות."
                    },
                    action = if (globalDistressScore >= 60) {
                        BotAction.OpenPersonalSettings
                    } else {
                        BotAction.None
                    },
                    requiresConfirmation = globalDistressScore >= 60
                )
            }

            BotIntent.UserStressed -> {
                BotResponse(
                    message = if (globalDistressScore >= 60) {
                        "אני שם לב שהשימוש כרגע קצת עמוס. רוצה שאפתח את מסך ההתאמה האישית כדי שיהיה נוח יותר?"
                    } else {
                        "אני מבין שזה יכול להיות עמוס. אפשר להמשיך לאט, שלב אחד בכל פעם."
                    },
                    action = if (globalDistressScore >= 60) {
                        BotAction.OpenPersonalSettings
                    } else {
                        BotAction.None
                    },
                    requiresConfirmation = globalDistressScore >= 60
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
                    message = "אני כאן כדי לעזור לך להשתמש באפליקציה. אפשר לבקש הסבר, הקראה, מוזיקה, שינוי צבעים, שינוי גודל טקסט או התאמה אישית."
                )
            }
        }
    }

    fun getDistressSuggestion(
        context: BotContext
    ): BotResponse? {
        val distress = context.distressSnapshot
        val appState = context.appState

        return when {
            distress.formBehaviorScore >= 70 && !appState.isMusicPlaying -> {
                BotResponse(
                    message = "שמתי לב שאתה נמצא הרבה זמן בשלב הזה. רוצה שאפעיל מוזיקת רקע מרגיעה?",
                    action = BotAction.PlaySound(PersonalizationCatalog.defaultCalmSound),
                    requiresConfirmation = true
                )
            }

            distress.formBehaviorScore >= 70 &&
                    appState.isMusicPlaying &&
                    !appState.autoReadEnabled -> {
                BotResponse(
                    message = "שמתי לב שאתה נמצא הרבה זמן בשלב הזה. מוזיקת הרקע כבר פועלת. רוצה שאפעיל הקראה אוטומטית כדי להקל עליך במסכים הבאים?",
                    action = BotAction.EnableAutoRead,
                    requiresConfirmation = true
                )
            }

            distress.semanticTextScore >= 70 -> {
                BotResponse(
                    message = "נראה שהשלב הזה קצת לא ברור. ליד כל שדה יש כפתור השמעה להסבר, ואפשר גם להשתמש במיקרופון כדי לדבר את התשובה במקום לכתוב.",
                    action = BotAction.None
                )
            }

            (distress.faceScore >= 70 ||
                    distress.voiceScore >= 70 ||
                    distress.touchScore >= 70) &&
                    appState.contrastMode != PersonalizationCatalog.lowContrast.mode.name -> {
                BotResponse(
                    message = "נראה שהשימוש כרגע קצת עמוס. רוצה שאעביר את הצבעים למצב רגוע יותר?",
                    action = BotAction.SetContrast(PersonalizationCatalog.lowContrast),
                    requiresConfirmation = true
                )
            }

            distress.globalScore >= 60 &&
                    appState.fontSizeMode != PersonalizationCatalog.largeFont.mode.name -> {
                BotResponse(
                    message = "נראה שהשלב הזה קצת עמוס. רוצה שאגדיל את הטקסט כדי שיהיה קל יותר לקרוא?",
                    action = BotAction.SetFontSize(PersonalizationCatalog.largeFont),
                    requiresConfirmation = true
                )
            }

            distress.globalScore >= 60 -> {
                BotResponse(
                    message = "נראה שהשלב הזה קצת עמוס. רוצה שאפתח את מסך ההתאמה האישית כדי שתוכל לבחור מה יעזור לך?",
                    action = BotAction.OpenPersonalSettings,
                    requiresConfirmation = true
                )
            }

            else -> {
                null
            }
        }
    }

    private fun getScreenExplanation(currentScreen: String): String {
        return when (currentScreen) {
            "home" -> {
                "אתה נמצא בדף הבית. מכאן אפשר להתחיל תהליך חדש או לעקוב אחרי ההתקדמות שלך."
            }

            "demoFormOptions" -> {
                "כאן אפשר לבחור איזה טופס אתה רוצה למלא."
            }

            "housingAssistanceForm" -> {
                "אתה נמצא בטופס סיוע בדיור. כאן ממלאים את הטופס שלב אחר שלב. אם שדה לא ברור, אפשר להשתמש בכפתור השמע ליד השדה."
            }

            "myFormsProgress" -> {
                "כאן אפשר לראות את ההתקדמות שלך בטפסים שכבר התחלת."
            }

            "profile" -> {
                "כאן אפשר לנהל את הפרטים האישיים שלך, שישמשו בהמשך למילוי אוטומטי של טפסים."
            }

            "Guidance" -> {
                "זהו מסך מדריך למשתמש. כאן אפשר להבין איך להשתמש באפליקציה."
            }

            "Personal Settings" -> {
                "כאן אפשר להתאים את האפליקציה לצרכים שלך, כמו גודל טקסט, ניגודיות, הקראה וצלילי רקע."
            }

            "contrastSettings" -> {
                "כאן אפשר לבחור מצב ניגודיות או צבעים שיהיו נוחים יותר לקריאה."
            }

            "fontSizeSettings" -> {
                "כאן אפשר לשנות את גודל הטקסט באפליקציה."
            }

            "backgroundSounds" -> {
                "כאן אפשר לבחור צלילי רקע שיכולים לעזור ליצור סביבה רגועה יותר."
            }

            else -> {
                "אתה נמצא במסך: $currentScreen. בהמשך אוכל להסביר כל מסך בצורה מפורטת יותר."
            }
        }
    }
}