package com.example.easyfill_project.chatbot.logic

import com.example.easyfill_project.chatbot.help.ScreenHelpCatalog
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
    fun getDistressSuggestion(context: BotContext): BotResponse? {
        val handScore = context.distressSnapshot.touchScore
        val appState = context.appState

        return when (handScore) {
            0 -> null//none

            //only supportive message when score is 1
            1 -> BotResponse(
                message = "שמנו לב שאולי קצת קשה לך. אפשר לקחת נשימה קצרה או הפסקה, במידה ואתה צריך עזרה - אנחנו כאן לעזור."
            )

            2 -> BotResponse(
                message = "רוצה שאגדיל את הטקסט כדי שיהיה קל יותר לקרוא?",
                action = BotAction.SetFontSize(PersonalizationCatalog.largeFont),
                requiresConfirmation = true
            )

            3 -> BotResponse(
                message = if (!appState.autoReadEnabled) {
                    "נראה שקשה לך כרגע. רוצה שאפעיל הקראה אוטומטית? אפשר גם להשתמש במיקרופון ולענות בקול במקום להקליד."
                } else {
                    "נראה שקשה לך כרגע. רוצה שאעביר את הצבעים למצב רגוע יותר? אפשר גם להשתמש במיקרופון ולענות בקול במקום להקליד."
                },
                action = if (!appState.autoReadEnabled) {
                    BotAction.EnableAutoRead
                } else {
                    BotAction.SetContrast(PersonalizationCatalog.lowContrast)
                },
                requiresConfirmation = true
            )
            4 -> BotResponse(
                message = "נראה שקשה לך מאוד כרגע. רוצה שאציג אפשרויות סיוע ויצירת קשר עם מוקדי עזרה?",
                action = BotAction.ShowEmergencyContacts,
                requiresConfirmation = true
            )

            else -> null
        }
    }

    private fun getScreenExplanation(currentScreen: String): String {
        return ScreenHelpCatalog.getExplanation(currentScreen)
    }
}