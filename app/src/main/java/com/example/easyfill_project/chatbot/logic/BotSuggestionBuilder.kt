package com.example.easyfill_project.chatbot.logic

import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog
import com.example.easyfill_project.distress_scoring.DistressMode



//this class decides: what message suggestion to show? which buttons? what action for each button?

// One button option shown to the user.
//One button = text + action
data class BotSuggestionOption(
    val label: String,
    val action: BotAction
)

// One full suggestion card.
//This represents the whole popup - meaning the suggested message + option buttons.
data class BotSuggestion(
    val message: String,
    val options: List<BotSuggestionOption>//this is the other data class.
)

object BotSuggestionBuilder {


    //this function decide what suggestion to show the user.
    //later maybe we will decide not using the severity level only.
    // Decides which suggestion to build according to the distress severity.
    /**
     * Chooses which suggestion to show.
     * severityLevel:
     * Determines how strong the detected distress is.

     * distressMode:
     * Determines whether the user is currently recording
     * or filling the form normally.

     * appState:
     * Prevents offering the setting already active.
     */
    fun buildSuggestion(
        severityLevel: Int,
        distressMode: DistressMode,
        appState: BotAppState
    ): BotSuggestion? {

        // totalScore 0 means that no distress was detected.
        // Therefore, do not create an alert or suggestion.
        if (severityLevel <= 0) {
            return null
        }

        return when (severityLevel) {

            // Low distress:
            // Use the mode to give relevant interaction advice.
            1 -> buildLowSeveritySuggestion(distressMode)

            // Medium distress:
            // Offer two color themes different from the current one.
            2 -> buildColorSuggestion(appState)

            // High distress:
            // Offer two background sounds different from the current one.
            3 -> buildMusicSuggestion(appState)

            // Very high distress:
            // Offer font alternatives and an additional support option.
            else -> buildHighSeveritySuggestion(appState)
        }
    }

    //Check Current font state
    //Offer other options.
    private fun buildFontSuggestion(appState: BotAppState): BotSuggestion {

        // All font size options that exist in the app:
        // small, normal, large.
        val allFontOptions = PersonalizationCatalog.fontSizes

        // Remove the font size the user already has now.
        // This prevents offering the same current size again.
        val alternativeFontOptions = allFontOptions.filter { fontOption ->
            fontOption.mode.name != appState.fontSizeMode
        }

        // Turn each available alternative into a button.
        // Example:
        // label = "טקסט גדול"
        // action = change font to LARGE
        val buttons = alternativeFontOptions.map { fontOption ->
            BotSuggestionOption(
                label = fontOption.displayName,
                action = BotAction.SetFontSize(fontOption)
            )
        }

        return BotSuggestion(
            message = "רוצה לשנות את גודל הטקסט? תבחר באפשרות שנוחה לך",
            options = buttons
        )
    }


    //Check Current color state
    //Offer other options.
    private fun buildColorSuggestion(appState: BotAppState): BotSuggestion {

        // Get all color modes that exist in the app:
        // Default, Purple, Black & White.
        val allColorOptions = PersonalizationCatalog.contrastModes

        // Remove the color mode the user is already using.
        // This leaves only the two alternative themes.
        val alternativeColorOptions = allColorOptions.filter { colorOption ->
            colorOption.mode.name != appState.contrastMode
        }

        // Convert every remaining color into a button.
        val buttons = alternativeColorOptions.map { colorOption ->
            BotSuggestionOption(
                label = colorOption.displayName,
                action = BotAction.SetContrast(colorOption)
            )
        }

        return BotSuggestion(
            message = "רוצה לשנות את צבעי המסך לצבעים אחרים? תוכל/י לבחור באופציה הרצויה",
            options = buttons
        )
    }



    // Checks the current music state
    // and offers up to two suitable sound options.
    private fun buildMusicSuggestion(appState: BotAppState): BotSuggestion {

        // Remove the sound that is currently playing.
        // If no music is playing, selectedSound is "none",
        // so all catalog sounds remain available.
        val availableSounds = PersonalizationCatalog.sounds.filter {
            it.key != appState.selectedSound
        }

        // Randomly select up to two sounds.
        val randomSounds = availableSounds
            .shuffled()
            .take(2)

        // Create one button for every selected sound.
        val buttons = randomSounds.map { sound ->
            BotSuggestionOption(
                label = sound.displayName,
                action = BotAction.PlaySound(sound)
            )
        }
        // Change the message according to whether music
        // is already playing.
        val message = if (appState.isMusicPlaying) {
            "מוזיקת רקע כבר פועלת. רוצה להחליף למוזיקה אחרת?"
        } else {
            "רוצה שאפעיל מוזיקת רקע רגועה?"
        }

        return BotSuggestion(
            message = message,
            options = buttons
        )
    }

    /**
     * Creates a low-severity suggestion.

     * The wording changes according to whether the user is recording
     * or filling the form normally.

     * Both modes offer:
     * 1. Reading the current field.
     * 2. Reading the whole screen.
     */
    private fun buildLowSeveritySuggestion(
        distressMode: DistressMode
    ): BotSuggestion {

        val message = when (distressMode) {

            DistressMode.VOICE_RECORDING -> {
                """
נראה שאולי קצת קשה לך כרגע.
אפשר לעבור להקלדה, לשמוע הסבר על השדה הנוכחי,
או להפעיל הקראה של המסך.
            """.trimIndent()
            }

            DistressMode.FORM_FILLING -> {
                """
נראה שאולי קצת קשה לך כרגע.
אפשר להשתמש בהקלטה קולית במקום להקליד,
לשמוע הסבר על השדה הנוכחי או להקריא את המסך.
            """.trimIndent()
            }
        }

        return BotSuggestion(
            message = message,
            options = listOf(
                BotSuggestionOption(
                    label = "הקרא את השדה",
                    action = BotAction.ReadCurrentField
                ),
                BotSuggestionOption(
                    label = "הקרא את המסך",
                    action = BotAction.ReadAloud
                )
            )
        )
    }

    /**
     * Creates options for very high severity.
     *
     * The user can still choose a less intrusive UI change,
     * or explicitly open the support information.
     */
    private fun buildHighSeveritySuggestion(
        appState: BotAppState
    ): BotSuggestion {

        // Build font buttons that are different from the current font.
        val fontSuggestion = buildFontSuggestion(appState)

        val options = fontSuggestion.options.toMutableList()

        // Add an additional support-information button.
        options.add(
            BotSuggestionOption(
                label = "הצג אפשרויות סיוע",
                action = BotAction.ShowEmergencyContacts
            )
        )

        return BotSuggestion(
            message = """
            נראה שקשה לך כרגע.
            אפשר לשנות את גודל הטקסט או להציג אפשרויות סיוע.
        """.trimIndent(),
            options = options
        )
    }
}