package com.example.easyfill_project.chatbot.logic

import com.example.easyfill_project.chatbot.intent.IntentDetector
import com.example.easyfill_project.chatbot.intent.RuleBasedIntentDetector
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotContext
import com.example.easyfill_project.chatbot.model.BotIntent
import com.example.easyfill_project.chatbot.model.BotResponse

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

            BotIntent.EXPLAIN_SCREEN -> {
                BotResponse(
                    message = getScreenExplanation(currentScreen)
                )
            }

            BotIntent.EXPLAIN_FIELD -> {
                BotResponse(
                    message = "הכול בסדר. ליד כל שדה יש כפתור השמעה שאפשר ללחוץ עליו כדי לשמוע הסבר על השדה. אפשר גם ללחוץ על המיקרופון כדי לדבר את התשובה במקום לכתוב."
                )
            }

            BotIntent.USER_CONFUSED -> {
                BotResponse(
                    message = if (globalDistressScore >= 60) {
                        "זה בסדר, נתקדם לאט. נראה שהשלב הזה קצת עמוס. אפשר לפתוח את מסך ההתאמה האישית כדי לשנות גודל טקסט, צבעים או צלילי רקע. לפתוח?"
                    } else {
                        "זה בסדר, נתקדם לאט. אפשר לשאול אותי מה עושים במסך הזה, או להשתמש בכפתורי ההשמעה והמיקרופון ליד השדות."
                    },
                    action = if (globalDistressScore >= 60) {
                        BotAction.OPEN_PERSONAL_SETTINGS
                    } else {
                        BotAction.NONE
                    },
                    requiresConfirmation = globalDistressScore >= 60
                )
            }

            BotIntent.USER_STRESSED -> {
                BotResponse(
                    message = if (globalDistressScore >= 60) {
                        "אני שם לב שהשימוש כרגע קצת עמוס. רוצה שאפתח את מסך ההתאמה האישית כדי שיהיה נוח יותר?"
                    } else {
                        "אני מבין שזה יכול להיות עמוס. אפשר להמשיך לאט, שלב אחד בכל פעם."
                    },
                    action = if (globalDistressScore >= 60) {
                        BotAction.OPEN_PERSONAL_SETTINGS
                    } else {
                        BotAction.NONE
                    },
                    requiresConfirmation = globalDistressScore >= 60
                )
            }

            BotIntent.READ_ALOUD -> {
                if (appState.isTtsSpeaking) {
                    BotResponse(
                        message = "ההקראה כבר פועלת.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל הקראה של הטקסט במסך.",
                        action = BotAction.READ_ALOUD
                    )
                }
            }

            BotIntent.STOP_READING -> {
                if (appState.isTtsSpeaking) {
                    BotResponse(
                        message = "בסדר, אני מפסיק את ההקראה.",
                        action = BotAction.STOP_READING
                    )
                } else {
                    BotResponse(
                        message = "אין כרגע הקראה פעילה.",
                        action = BotAction.NONE
                    )
                }
            }

            BotIntent.ENABLE_AUTO_READ -> {
                if (appState.autoReadEnabled) {
                    BotResponse(
                        message = "ההקראה האוטומטית כבר פועלת.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל הקראה אוטומטית לכל מסך.",
                        action = BotAction.ENABLE_AUTO_READ
                    )
                }
            }

            BotIntent.DISABLE_AUTO_READ -> {
                if (appState.autoReadEnabled) {
                    BotResponse(
                        message = "בסדר, אני מכבה את ההקראה האוטומטית.",
                        action = BotAction.DISABLE_AUTO_READ
                    )
                } else {
                    BotResponse(
                        message = "ההקראה האוטומטית כבר כבויה.",
                        action = BotAction.NONE
                    )
                }
            }

            BotIntent.OPEN_PERSONAL_SETTINGS -> {
                BotResponse(
                    message = "אפשר לפתוח את מסך ההתאמה האישית, ושם לבחור מוזיקה, צבעים, גודל טקסט והקראה. לפתוח?",
                    action = BotAction.OPEN_PERSONAL_SETTINGS,
                    requiresConfirmation = true
                )
            }

            BotIntent.OPEN_CONTRAST_SETTINGS -> {
                BotResponse(
                    message = "אפשר לפתוח את הגדרות הצבעים והניגודיות. לפתוח?",
                    action = BotAction.OPEN_CONTRAST_SETTINGS,
                    requiresConfirmation = true
                )
            }

            BotIntent.OPEN_FONT_SIZE_SETTINGS -> {
                BotResponse(
                    message = "אפשר לפתוח את הגדרות גודל הטקסט. לפתוח?",
                    action = BotAction.OPEN_FONT_SIZE_SETTINGS,
                    requiresConfirmation = true
                )
            }

            BotIntent.OPEN_BACKGROUND_SOUNDS -> {
                BotResponse(
                    message = "אפשר לפתוח את מסך צלילי הרקע ולבחור צליל. לפתוח?",
                    action = BotAction.OPEN_BACKGROUND_SOUNDS,
                    requiresConfirmation = true
                )
            }

            BotIntent.PLAY_NATURE_SOUND -> {
                if (appState.selectedSound == "nature") {
                    BotResponse(
                        message = "צלילי הטבע כבר פועלים.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל צלילי טבע מרגיעים.",
                        action = BotAction.PLAY_NATURE_SOUND
                    )
                }
            }

            BotIntent.PLAY_CALM_MUSIC -> {
                if (appState.selectedSound == "calm") {
                    BotResponse(
                        message = "מוזיקת המדיטציה כבר פועלת.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל מוזיקה למדיטציה.",
                        action = BotAction.PLAY_CALM_MUSIC
                    )
                }
            }

            BotIntent.PLAY_INSTRUMENT_SOUND -> {
                if (appState.selectedSound == "instruments") {
                    BotResponse(
                        message = "צלילי הנגינה כבר פועלים.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל צלילי נגינה מרגיעים.",
                        action = BotAction.PLAY_INSTRUMENT_SOUND
                    )
                }
            }

            BotIntent.STOP_BACKGROUND_MUSIC -> {
                if (appState.isMusicPlaying) {
                    BotResponse(
                        message = "בסדר, אני מפסיק את מוזיקת הרקע.",
                        action = BotAction.STOP_BACKGROUND_MUSIC
                    )
                } else {
                    BotResponse(
                        message = "מוזיקת הרקע כבר כבויה.",
                        action = BotAction.NONE
                    )
                }
            }

            BotIntent.SET_CONTRAST_DEFAULT -> {
                if (appState.contrastMode == "DEFAULT") {
                    BotResponse(
                        message = "הצבעים כבר מוגדרים למצב רגיל.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מחזיר את הצבעים למצב רגיל.",
                        action = BotAction.SET_CONTRAST_DEFAULT
                    )
                }
            }

            BotIntent.SET_CONTRAST_HIGH -> {
                if (appState.contrastMode == "HIGH") {
                    BotResponse(
                        message = "מצב ניגודיות גבוהה כבר פעיל.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מפעיל מצב ניגודיות גבוהה.",
                        action = BotAction.SET_CONTRAST_HIGH
                    )
                }
            }

            BotIntent.SET_CONTRAST_LOW -> {
                if (appState.contrastMode == "LOW") {
                    BotResponse(
                        message = "מצב הצבעים הרגועים כבר פעיל.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מעביר את הממשק לצבעים רגועים יותר.",
                        action = BotAction.SET_CONTRAST_LOW
                    )
                }
            }

            BotIntent.SET_FONT_SMALL -> {
                if (appState.fontSizeMode == "SMALL") {
                    BotResponse(
                        message = "גודל הטקסט כבר מוגדר לקטן.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני משנה את גודל הטקסט לקטן.",
                        action = BotAction.SET_FONT_SMALL
                    )
                }
            }

            BotIntent.SET_FONT_NORMAL -> {
                if (appState.fontSizeMode == "NORMAL") {
                    BotResponse(
                        message = "גודל הטקסט כבר מוגדר לרגיל.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מחזיר את גודל הטקסט לרגיל.",
                        action = BotAction.SET_FONT_NORMAL
                    )
                }
            }

            BotIntent.SET_FONT_LARGE -> {
                if (appState.fontSizeMode == "LARGE") {
                    BotResponse(
                        message = "גודל הטקסט כבר מוגדר לגדול.",
                        action = BotAction.NONE
                    )
                } else {
                    BotResponse(
                        message = "בסדר, אני מגדיל את גודל הטקסט.",
                        action = BotAction.SET_FONT_LARGE
                    )
                }
            }

            BotIntent.VOICE_INPUT_HELP -> {
                BotResponse(
                    message = "ליד שדות מתאימים אפשר ללחוץ על המיקרופון ולדבר את התשובה במקום להקליד. אפשר גם להשתמש בכפתור ההשמעה ליד השדה כדי לשמוע הסבר."
                )
            }

            BotIntent.GENERAL_HELP -> {
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
            // המשתמש הרבה זמן בשלב הזה והמוזיקה לא פועלת
            distress.formBehaviorScore >= 70 && !appState.isMusicPlaying -> {
                BotResponse(
                    message = "שמתי לב שאתה נמצא הרבה זמן בשלב הזה. רוצה שאפעיל מוזיקת רקע מרגיעה?",
                    action = BotAction.PLAY_CALM_MUSIC,
                    requiresConfirmation = true
                )
            }

            // המשתמש הרבה זמן בשלב הזה אבל מוזיקה כבר פועלת
            distress.formBehaviorScore >= 70 && appState.isMusicPlaying && !appState.autoReadEnabled -> {
                BotResponse(
                    message = "שמתי לב שאתה נמצא הרבה זמן בשלב הזה. מוזיקת הרקע כבר פועלת. רוצה שאפעיל הקראה אוטומטית כדי להקל עליך במסכים הבאים?",
                    action = BotAction.ENABLE_AUTO_READ,
                    requiresConfirmation = true
                )
            }

            // מצוקה לפי טקסט / בלבול
            distress.semanticTextScore >= 70 -> {
                BotResponse(
                    message = "נראה שהשלב הזה קצת לא ברור. ליד כל שדה יש כפתור השמעה להסבר, ואפשר גם להשתמש במיקרופון כדי לדבר את התשובה במקום לכתוב.",
                    action = BotAction.NONE,
                    requiresConfirmation = false
                )
            }

            // מצוקה פיזית / קול / מגע, ואם הצבעים עדיין רגילים
            (distress.faceScore >= 70 || distress.voiceScore >= 70 || distress.touchScore >= 70) &&
                    appState.contrastMode == "DEFAULT" -> {
                BotResponse(
                    message = "נראה שהשימוש כרגע קצת עמוס. רוצה שאעביר את הצבעים למצב רגוע יותר?",
                    action = BotAction.SET_CONTRAST_LOW,
                    requiresConfirmation = true
                )
            }

            // מצוקה כללית, ואם הטקסט עדיין לא גדול
            distress.globalScore >= 60 && appState.fontSizeMode != "LARGE" -> {
                BotResponse(
                    message = "נראה שהשלב הזה קצת עמוס. רוצה שאגדיל את הטקסט כדי שיהיה קל יותר לקרוא?",
                    action = BotAction.SET_FONT_LARGE,
                    requiresConfirmation = true
                )
            }

            // מצוקה כללית, אם כבר יש התאמות בסיסיות
            distress.globalScore >= 60 -> {
                BotResponse(
                    message = "נראה שהשלב הזה קצת עמוס. רוצה שאפתח את מסך ההתאמה האישית כדי שתוכל לבחור מה יעזור לך?",
                    action = BotAction.OPEN_PERSONAL_SETTINGS,
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