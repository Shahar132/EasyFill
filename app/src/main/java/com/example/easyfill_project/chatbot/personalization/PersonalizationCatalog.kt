package com.example.easyfill_project.chatbot.personalization

import com.example.easyfill_project.R
import com.example.easyfill_project.screen.ContrastMode
import com.example.easyfill_project.screen.FontSizeMode

data class SoundOption(
    val key: String,
    val displayName: String,
    val soundRes: Int
)

data class FontSizeOption(
    val key: String,
    val displayName: String,
    val mode: FontSizeMode,
)

data class ContrastOption(
    val key: String,
    val displayName: String,
    val mode: ContrastMode,
)

object PersonalizationCatalog {

    val sounds = listOf(
        SoundOption(
            key = "nature",
            displayName = "צלילי טבע מרגיעים",
            soundRes = R.raw.nature_sound,

        ),
        SoundOption(
            key = "calm",
            displayName = "מוזיקה למדיטציה",
            soundRes = R.raw.calm_music,

        ),
        SoundOption(
            key = "instruments",
            displayName = "צלילי נגינה מרגיעים",
            soundRes = R.raw.violin_sound,
        )
    )

    val fontSizes = listOf(
        FontSizeOption(
            key = "small",
            displayName = "טקסט קטן",
            mode = FontSizeMode.SMALL,
        ),
        FontSizeOption(
            key = "normal",
            displayName = "טקסט רגיל",
            mode = FontSizeMode.NORMAL,
        ),
        FontSizeOption(
            key = "large",
            displayName = "טקסט גדול",
            mode = FontSizeMode.LARGE,

        )
    )

    val contrastModes = listOf(
        ContrastOption(
            key = "default",
            displayName = "גווני כחול רגילים",
            mode = ContrastMode.DEFAULT,
        ),
        ContrastOption(
            key = "high",
            displayName = "צבעי שחור-לבן",
            mode = ContrastMode.HIGH,
        ),
        ContrastOption(
            key = "low",
            displayName = "צבעי סגול לילך",
            mode = ContrastMode.LOW,
        )
    )

    val defaultCalmSound: SoundOption
        get() = sounds.first { it.key == "calm" }

    val largeFont: FontSizeOption
        get() = fontSizes.first { it.mode == FontSizeMode.LARGE }

    val lowContrast: ContrastOption
        get() = contrastModes.first { it.mode == ContrastMode.LOW }

}