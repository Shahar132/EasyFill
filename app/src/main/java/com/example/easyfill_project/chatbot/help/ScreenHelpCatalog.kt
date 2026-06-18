package com.example.easyfill_project.chatbot.help

data class ScreenHelp(
    val routes: List<String>,
    val title: String,
    val explanation: String
)

object ScreenHelpCatalog {

    private val screens = listOf(
        ScreenHelp(
            routes = listOf("home"),
            title = "דף הבית",
            explanation = "אתה נמצא בדף הבית. מכאן אפשר להתחיל תהליך חדש או לעקוב אחרי ההתקדמות שלך."
        ),
        ScreenHelp(
            routes = listOf("demoFormOptions"),
            title = "בחירת טופס",
            explanation = "כאן אפשר לבחור איזה טופס אתה רוצה למלא."
        ),
        ScreenHelp(
            routes = listOf(
                "housingAssistanceForm",
                "housingAssistanceForm/{startStep}"
            ),
            title = "טופס סיוע בדיור",
            explanation = "אתה נמצא בטופס סיוע בדיור. כאן ממלאים את הטופס שלב אחר שלב. אם שדה לא ברור, אפשר להשתמש בכפתור השמע ליד השדה."
        ),
        ScreenHelp(
            routes = listOf("myFormsProgress"),
            title = "התקדמות הטפסים",
            explanation = "כאן אפשר לראות את ההתקדמות שלך בטפסים שכבר התחלת."
        ),
        ScreenHelp(
            routes = listOf("profile"),
            title = "פרופיל אישי",
            explanation = "כאן אפשר לנהל את הפרטים האישיים שלך, שישמשו בהמשך למילוי אוטומטי של טפסים."
        ),
        ScreenHelp(
            routes = listOf("Guidance"),
            title = "מדריך למשתמש",
            explanation = "זהו מסך מדריך למשתמש. כאן אפשר להבין איך להשתמש באפליקציה."
        ),
        ScreenHelp(
            routes = listOf("Personal Settings"),
            title = "התאמה אישית",
            explanation = "כאן אפשר להתאים את האפליקציה לצרכים שלך, כמו גודל טקסט, ניגודיות, הקראה וצלילי רקע."
        ),
        ScreenHelp(
            routes = listOf("contrastSettings"),
            title = "הגדרות צבעים",
            explanation = "כאן אפשר לבחור מצב ניגודיות או צבעים שיהיו נוחים יותר לקריאה."
        ),
        ScreenHelp(
            routes = listOf("fontSizeSettings"),
            title = "גודל טקסט",
            explanation = "כאן אפשר לשנות את גודל הטקסט באפליקציה."
        ),
        ScreenHelp(
            routes = listOf("backgroundSounds"),
            title = "צלילי רקע",
            explanation = "כאן אפשר לבחור צלילי רקע שיכולים לעזור ליצור סביבה רגועה יותר."
        ),
        ScreenHelp(
            routes = listOf("uploadPdf"),
            title = "העלאת טופס",
            explanation = "כאן אפשר להעלות טופס כדי שהמערכת תזהה את השדות ותעזור במילוי שלו."
        ),
        ScreenHelp(
            routes = listOf("bankDetailsForm"),
            title = "טופס פרטי בנק",
            explanation = "כאן אפשר למלא פרטי חשבון בנק בצורה מסודרת, שלב אחר שלב."
        )
    )

    fun getExplanation(currentRoute: String): String {
        val cleanRoute = currentRoute.substringBefore("?")

        val screenHelp = screens.firstOrNull { screen ->
            screen.routes.any { route ->
                routeMatches(
                    currentRoute = cleanRoute,
                    catalogRoute = route
                )
            }
        }

        return screenHelp?.explanation
            ?: "אתה נמצא במסך: $currentRoute. בהמשך אוכל להסביר כל מסך בצורה מפורטת יותר."
    }

    private fun routeMatches(
        currentRoute: String,
        catalogRoute: String
    ): Boolean {
        if (currentRoute == catalogRoute) {
            return true
        }

        val baseRoute = catalogRoute.substringBefore("/{")

        return baseRoute.isNotBlank() &&
                currentRoute.startsWith("$baseRoute/")
    }
}