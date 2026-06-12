package com.example.easyfill_project.chatbot.personalization

import com.example.easyfill_project.R
import com.example.easyfill_project.screen.ContrastMode
import com.example.easyfill_project.screen.FontSizeMode

data class SoundOption(
    val key: String,
    val displayName: String,
    val soundRes: Int,
    val keywords: List<String>
)

data class FontSizeOption(
    val key: String,
    val displayName: String,
    val mode: FontSizeMode,
    val keywords: List<String>
)

data class ContrastOption(
    val key: String,
    val displayName: String,
    val mode: ContrastMode,
    val keywords: List<String>
)

object PersonalizationCatalog {

    val sounds = listOf(
        SoundOption(
            key = "nature",
            displayName = "צלילי טבע מרגיעים",
            soundRes = R.raw.nature_sound,
            keywords = listOf(
                "טבע",
                "צלילי טבע",
                "שים טבע",
                "תפעיל טבע"
            )
        ),
        SoundOption(
            key = "calm",
            displayName = "מוזיקה למדיטציה",
            soundRes = R.raw.calm_music,
            keywords = listOf(
                "מדיטציה",
                "מוזיקה רגועה",
                "מוזיקה למדיטציה",
                "שים מוזיקה",
                "שים לי מוזיקה",
                "תפעיל מוזיקה",
                "תפעיל צליל"
            )
        ),
        SoundOption(
            key = "instruments",
            displayName = "צלילי נגינה מרגיעים",
            soundRes = R.raw.violin_sound,
            keywords = listOf(
                "נגינה",
                "כינור",
                "צלילי נגינה",
                "מוזיקה עם נגינה"
            )
        )
    )

    val fontSizes = listOf(
        FontSizeOption(
            key = "small",
            displayName = "טקסט קטן",
            mode = FontSizeMode.SMALL,
            keywords = listOf(
                "טקסט קטן יותר",
                "כתב קטן יותר",
                "תקטין את הטקסט",
                "תקטין את הכתב"
            )
        ),
        FontSizeOption(
            key = "normal",
            displayName = "טקסט רגיל",
            mode = FontSizeMode.NORMAL,
            keywords = listOf(
                "טקסט רגיל",
                "כתב רגיל",
                "תחזיר גודל טקסט",
                "גודל טקסט רגיל"
            )
        ),
        FontSizeOption(
            key = "large",
            displayName = "טקסט גדול",
            mode = FontSizeMode.LARGE,
            keywords = listOf(
                "להגדיל טקסט",
                "תגדיל טקסט",
                "תגדיל את הטקסט",
                "כתב גדול",
                "טקסט גדול",
                "טקסט קטן",
                "קשה לי לקרוא",
                "קשה לקרוא",
                "אני לא מצליח לקרוא",
                "לא מצליח לקרוא",
                "הכתב לא ברור",
                "הטקסט לא ברור",
                "כתב קטן"
            )
        )
    )

    val contrastModes = listOf(
        ContrastOption(
            key = "default",
            displayName = "צבעים רגילים",
            mode = ContrastMode.DEFAULT,
            keywords = listOf(
                "צבעים רגילים",
                "תחזיר צבעים",
                "מצב צבעים רגיל",
                "ניגודיות רגילה"
            )
        ),
        ContrastOption(
            key = "high",
            displayName = "ניגודיות גבוהה",
            mode = ContrastMode.HIGH,
            keywords = listOf(
                "ניגודיות גבוהה",
                "צבעים חזקים",
                "קונטרסט גבוה",
                "קשה לי לראות"
            )
        ),
        ContrastOption(
            key = "low",
            displayName = "צבעים רגועים",
            mode = ContrastMode.LOW,
            keywords = listOf(
                "צבעים רגועים",
                "צבעים עדינים",
                "ניגודיות נמוכה",
                "צבעים פחות חזקים",
                "יותר נוח לעיניים"
            )
        )
    )

    val defaultCalmSound: SoundOption
        get() = sounds.first { it.key == "calm" }

    val largeFont: FontSizeOption
        get() = fontSizes.first { it.mode == FontSizeMode.LARGE }

    val lowContrast: ContrastOption
        get() = contrastModes.first { it.mode == ContrastMode.LOW }

    fun findSoundInText(text: String): SoundOption? {
        return sounds.firstOrNull { option ->
            option.keywords.any { keyword -> text.contains(keyword) }
        }
    }

    fun findFontSizeInText(text: String): FontSizeOption? {
        return fontSizes.firstOrNull { option ->
            option.keywords.any { keyword -> text.contains(keyword) }
        }
    }

    fun findContrastInText(text: String): ContrastOption? {
        return contrastModes.firstOrNull { option ->
            option.keywords.any { keyword -> text.contains(keyword) }
        }
    }
}