package com.example.easyfill_project.chatbot.logic

import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog
import com.example.easyfill_project.distress_scoring.DistressMode

/**
 * One button displayed inside a chatbot suggestion.
 *
 * Every button contains:
 * - the text displayed to the user;
 * - the action that should run after the button is selected.
 */
data class BotSuggestionOption(
    val label: String,
    val action: BotAction
)

/**
 * One complete chatbot suggestion card.
 *
 * @property id Stable identifier used to remember whether this suggestion
 * was displayed, accepted, or dismissed.
 * @property level Confirmed distress level for which the suggestion was built.
 * @property message Text displayed above the action buttons.
 * @property options Buttons available to the user. An empty list is used only
 * for calming messages created outside this builder.
 */
data class BotSuggestion(
    val id: String,
    val level: Int,
    val message: String,
    val options: List<BotSuggestionOption>
)

/**
 * Creates the default and alternative action suggestions shown by the chatbot.
 *
 * Responsibilities:
 * 1. Build the default action for a newly confirmed distress level.
 * 2. Build a different alternative action when requested by
 *    DistressConfirmationManager.
 * 3. Avoid suggesting settings that are already active.
 * 4. Avoid returning action categories that were already displayed,
 *    accepted, dismissed, or used as the original default suggestion.
 */
object BotSuggestionBuilder {

    /*
     * Stable IDs for the default suggestions.
     *
     * These values are also used when checking whether an alternative action
     * is equivalent to the original default suggestion.
     */
    private const val DEFAULT_READING_ID =
        "level_1_reading_support"

    private const val DEFAULT_COLOR_ID =
        "level_2_color_support"

    private const val DEFAULT_MUSIC_ID =
        "level_3_music_support"

    private const val DEFAULT_HIGH_SUPPORT_ID =
        "level_4_font_and_emergency_support"

    /*
     * Stable IDs for alternative action categories.
     *
     * These IDs must never contain timestamps or random values because
     * DistressConfirmationManager stores them to prevent repetition.
     */
    private const val ALTERNATIVE_FONT_ID =
        "alternative_font_size"

    private const val ALTERNATIVE_MUSIC_ID =
        "alternative_background_music"

    private const val ALTERNATIVE_CONTRAST_ID =
        "alternative_contrast"

    private const val ALTERNATIVE_READING_ID =
        "alternative_reading_support"

    private const val ALTERNATIVE_EMERGENCY_ID =
        "alternative_emergency_support"

    /**
     * Builds the normal default suggestion for a newly confirmed level.
     *
     * Level 1:
     * Reading assistance.
     *
     * Level 2:
     * Color-theme assistance.
     *
     * Level 3:
     * Background-music assistance.
     *
     * Level 4:
     * Font-size and support-information assistance.
     */
    fun buildSuggestion(
        severityLevel: Int,
        distressMode: DistressMode,
        appState: BotAppState,
        excludedSuggestionIds: Set<String> = emptySet()
    ): BotSuggestion? {

        if (severityLevel <= 0) {
            return null
        }

        /*
         * First build the normal default suggestion for this level.
         */
        val defaultSuggestion =
            when (severityLevel) {
                1 -> buildLowSeveritySuggestion(distressMode)
                2 -> buildColorSuggestion(appState)
                3 -> buildMusicSuggestion(appState)
                else -> buildHighSeveritySuggestion(appState)
            }

        /*
         * Return the normal default only when neither its exact ID nor
         * an equivalent action category was accepted previously.
         */
        val defaultIsExcluded =
            defaultSuggestion.id in excludedSuggestionIds ||
                    isDefaultCategoryExcluded(
                        suggestionId = defaultSuggestion.id,
                        excludedSuggestionIds = excludedSuggestionIds
                    )

        if (!defaultIsExcluded && defaultSuggestion.options.isNotEmpty()) {
            return defaultSuggestion
        }

        /*
         * The default was already accepted or is no longer usable.
         * Select a different unused action instead.
         */
        return buildAlternativeSuggestion(
            severityLevel = severityLevel,
            distressMode = distressMode,
            appState = appState,
            excludedSuggestionIds = excludedSuggestionIds +
                    defaultSuggestion.id
        )
    }

    /**
     * Builds an alternative action that has not already been used during
     * the current confirmed distress event.
     *
     * The manager supplies excludedSuggestionIds containing suggestions that
     * were already:
     * - displayed;
     * - accepted;
     * - dismissed as alternatives;
     * - saved as the original dismissed default.
     *
     * This function also blocks equivalent action categories. For example,
     * if the original default was the level-3 music suggestion, an alternative
     * music suggestion is not returned under a different ID.
     */
    fun buildAlternativeSuggestion(
        severityLevel: Int,
        distressMode: DistressMode,
        appState: BotAppState,
        excludedSuggestionIds: Set<String>
    ): BotSuggestion? {

        if (severityLevel <= 0) {
            return null
        }

        /*
         * The order controls which unused alternative is preferred first.
         *
         * The list is intentionally different for every severity level so
         * the offered help remains suitable for the user's current state.
         */
        val possibleSuggestions =
            when (severityLevel) {

                1 -> listOfNotNull(
                    buildAlternativeFontSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeMusicSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeContrastSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeEmergencySuggestion(
                        level = severityLevel
                    )
                )

                2 -> listOfNotNull(
                    buildAlternativeMusicSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeFontSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeReadingSuggestion(
                        distressMode = distressMode,
                        level = severityLevel
                    ),
                    buildAlternativeEmergencySuggestion(
                        level = severityLevel
                    )
                )

                3 -> listOfNotNull(
                    buildAlternativeReadingSuggestion(
                        distressMode = distressMode,
                        level = severityLevel
                    ),
                    buildAlternativeContrastSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeFontSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeEmergencySuggestion(
                        level = severityLevel
                    )
                )

                else -> listOfNotNull(
                    buildAlternativeReadingSuggestion(
                        distressMode = distressMode,
                        level = severityLevel
                    ),
                    buildAlternativeContrastSuggestion(
                        appState = appState,
                        level = severityLevel
                    ),
                    buildAlternativeMusicSuggestion(
                        appState = appState,
                        level = severityLevel
                    )
                )
            }

        return possibleSuggestions.firstOrNull { suggestion ->
            suggestion.id !in excludedSuggestionIds &&
                    !isEquivalentCategoryExcluded(
                        suggestionId = suggestion.id,
                        excludedSuggestionIds = excludedSuggestionIds
                    )
        }
    }

    /**
     * Prevents the same action category from returning under a different ID.
     *
     * Example:
     * - default suggestion ID: level_3_music_support
     * - alternative suggestion ID: alternative_background_music
     *
     * Although the IDs differ, both represent background-music assistance,
     * so the alternative must be blocked when the default was already shown.
     */
    private fun isEquivalentCategoryExcluded(
        suggestionId: String,
        excludedSuggestionIds: Set<String>
    ): Boolean {

        return when (suggestionId) {

            ALTERNATIVE_READING_ID -> {
                DEFAULT_READING_ID in excludedSuggestionIds ||
                        ALTERNATIVE_READING_ID in excludedSuggestionIds
            }

            ALTERNATIVE_CONTRAST_ID -> {
                DEFAULT_COLOR_ID in excludedSuggestionIds ||
                        ALTERNATIVE_CONTRAST_ID in excludedSuggestionIds
            }

            ALTERNATIVE_MUSIC_ID -> {
                DEFAULT_MUSIC_ID in excludedSuggestionIds ||
                        ALTERNATIVE_MUSIC_ID in excludedSuggestionIds
            }

            ALTERNATIVE_FONT_ID -> {
                DEFAULT_HIGH_SUPPORT_ID in excludedSuggestionIds ||
                        ALTERNATIVE_FONT_ID in excludedSuggestionIds
            }

            ALTERNATIVE_EMERGENCY_ID -> {
                DEFAULT_HIGH_SUPPORT_ID in excludedSuggestionIds ||
                        ALTERNATIVE_EMERGENCY_ID in excludedSuggestionIds
            }

            else -> false
        }
    }


    /**
     * Prevents a default suggestion from returning when an equivalent
     * alternative category was already accepted.
     *
     * Example:
     * alternative_background_music was accepted earlier.
     * The normal level-3 music default must not appear afterward.
     */
    private fun isDefaultCategoryExcluded(
        suggestionId: String,
        excludedSuggestionIds: Set<String>
    ): Boolean {

        return when (suggestionId) {

            DEFAULT_READING_ID -> {
                DEFAULT_READING_ID in excludedSuggestionIds ||
                        ALTERNATIVE_READING_ID in excludedSuggestionIds
            }

            DEFAULT_COLOR_ID -> {
                DEFAULT_COLOR_ID in excludedSuggestionIds ||
                        ALTERNATIVE_CONTRAST_ID in excludedSuggestionIds
            }

            DEFAULT_MUSIC_ID -> {
                DEFAULT_MUSIC_ID in excludedSuggestionIds ||
                        ALTERNATIVE_MUSIC_ID in excludedSuggestionIds
            }

            DEFAULT_HIGH_SUPPORT_ID -> {
                DEFAULT_HIGH_SUPPORT_ID in excludedSuggestionIds ||
                        ALTERNATIVE_FONT_ID in excludedSuggestionIds ||
                        ALTERNATIVE_EMERGENCY_ID in excludedSuggestionIds
            }

            else -> false
        }
    }

    /**
     * Creates a font-size suggestion.
     *
     * The currently active font size is removed from the offered buttons.
     */
    private fun buildFontSuggestion(
        appState: BotAppState,
        level: Int = 4
    ): BotSuggestion {

        val alternativeFontOptions =
            PersonalizationCatalog.fontSizes.filter { fontOption ->
                fontOption.mode.name != appState.fontSizeMode
            }

        val buttons =
            alternativeFontOptions.map { fontOption ->
                BotSuggestionOption(
                    label = fontOption.displayName,
                    action = BotAction.SetFontSize(fontOption)
                )
            }

        return BotSuggestion(
            id = "level_${level}_font_support",
            level = level,
            message =
                "רוצה לשנות את גודל הטקסט? תבחר/י באפשרות שנוחה לך",
            options = buttons
        )
    }

    /**
     * Creates the default color-theme suggestion for level 2.
     *
     * The currently active contrast/theme mode is removed.
     */
    private fun buildColorSuggestion(
        appState: BotAppState
    ): BotSuggestion {

        val alternativeColorOptions =
            PersonalizationCatalog.contrastModes.filter { colorOption ->
                colorOption.mode.name != appState.contrastMode
            }

        val buttons =
            alternativeColorOptions.map { colorOption ->
                BotSuggestionOption(
                    label = colorOption.displayName,
                    action = BotAction.SetContrast(colorOption)
                )
            }

        return BotSuggestion(
            id = DEFAULT_COLOR_ID,
            level = 2,
            message =
                "רוצה לשנות את צבעי המסך? תוכל/י לבחור באפשרות הרצויה",
            options = buttons
        )
    }

    /**
     * Creates the default music suggestion for level 3.
     *
     * The currently selected sound is removed, and up to two other sounds
     * are offered.
     */
    private fun buildMusicSuggestion(
        appState: BotAppState
    ): BotSuggestion {

        val availableSounds =
            PersonalizationCatalog.sounds.filter { sound ->
                sound.key != appState.selectedSound
            }

        val selectedSounds =
            availableSounds
                .shuffled()
                .take(2)

        val buttons =
            selectedSounds.map { sound ->
                BotSuggestionOption(
                    label = sound.displayName,
                    action = BotAction.PlaySound(sound)
                )
            }

        val message =
            if (appState.isMusicPlaying) {
                "מוזיקת רקע כבר פועלת. רוצה להחליף למוזיקה אחרת?"
            } else {
                "רוצה שאפעיל מוזיקת רקע רגועה?"
            }

        return BotSuggestion(
            id = DEFAULT_MUSIC_ID,
            level = 3,
            message = message,
            options = buttons
        )
    }

    /**
     * Creates the default level-1 reading suggestion.
     *
     * The wording changes according to whether the user is recording
     * or filling the form normally.
     */
    private fun buildLowSeveritySuggestion(
        distressMode: DistressMode
    ): BotSuggestion {

        val message =
            when (distressMode) {

                DistressMode.VOICE_RECORDING -> {
                    """
                    נראה שאולי קצת קשה לך כרגע.
                    אפשר לשמוע הסבר על השדה הנוכחי או להפעיל הקראה של המסך.
                    """.trimIndent()
                }

                DistressMode.FORM_FILLING -> {
                    """
                    נראה שאולי קצת קשה לך כרגע.
                    אפשר לשמוע הסבר על השדה הנוכחי או להקריא את המסך.
                    """.trimIndent()
                }
            }

        return BotSuggestion(
            id = DEFAULT_READING_ID,
            level = 1,
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
     * Creates the default level-4 suggestion.
     *
     * It combines font-size options with the option to display support
     * information.
     */
    private fun buildHighSeveritySuggestion(
        appState: BotAppState
    ): BotSuggestion {

        val fontSuggestion =
            buildFontSuggestion(
                appState = appState,
                level = 4
            )

        val options =
            fontSuggestion.options.toMutableList()

        options.add(
            BotSuggestionOption(
                label = "הצג אפשרויות סיוע",
                action = BotAction.ShowEmergencyContacts
            )
        )

        return BotSuggestion(
            id = DEFAULT_HIGH_SUPPORT_ID,
            level = 4,
            message =
                """
                נראה שקשה לך כרגע.
                אפשר לשנות את גודל הטקסט או להציג אפשרויות סיוע.
                """.trimIndent(),
            options = options
        )
    }

    /**
     * Creates an alternative font-size action.
     *
     * Returns null only if no other font-size option exists.
     */
    private fun buildAlternativeFontSuggestion(
        appState: BotAppState,
        level: Int
    ): BotSuggestion? {

        val alternativeFontOptions =
            PersonalizationCatalog.fontSizes.filter { fontOption ->
                fontOption.mode.name != appState.fontSizeMode
            }

        if (alternativeFontOptions.isEmpty()) {
            return null
        }

        val buttons =
            alternativeFontOptions.map { fontOption ->
                BotSuggestionOption(
                    label = fontOption.displayName,
                    action = BotAction.SetFontSize(fontOption)
                )
            }

        return BotSuggestion(
            id = ALTERNATIVE_FONT_ID,
            level = level,
            message =
                "אולי שינוי גודל הטקסט יקל על הקריאה. רוצה לבחור גודל אחר?",
            options = buttons
        )
    }

    /**
     * Creates an alternative background-music action.
     *
     * Returns null if no different sound is available.
     */
    private fun buildAlternativeMusicSuggestion(
        appState: BotAppState,
        level: Int
    ): BotSuggestion? {

        val availableSounds =
            PersonalizationCatalog.sounds.filter { sound ->
                sound.key != appState.selectedSound
            }

        if (availableSounds.isEmpty()) {
            return null
        }

        val selectedSounds =
            availableSounds
                .shuffled()
                .take(2)

        val buttons =
            selectedSounds.map { sound ->
                BotSuggestionOption(
                    label = sound.displayName,
                    action = BotAction.PlaySound(sound)
                )
            }

        val message =
            if (appState.isMusicPlaying) {
                "אולי מוזיקה אחרת תהיה נעימה יותר. רוצה להחליף?"
            } else {
                "אפשר להפעיל מוזיקת רקע רגועה. רוצה לנסות?"
            }

        return BotSuggestion(
            id = ALTERNATIVE_MUSIC_ID,
            level = level,
            message = message,
            options = buttons
        )
    }

    /**
     * Creates an alternative color/contrast action.
     *
     * Returns null if no different color mode exists.
     */
    private fun buildAlternativeContrastSuggestion(
        appState: BotAppState,
        level: Int
    ): BotSuggestion? {

        val alternativeColorOptions =
            PersonalizationCatalog.contrastModes.filter { colorOption ->
                colorOption.mode.name != appState.contrastMode
            }

        if (alternativeColorOptions.isEmpty()) {
            return null
        }

        val buttons =
            alternativeColorOptions.map { colorOption ->
                BotSuggestionOption(
                    label = colorOption.displayName,
                    action = BotAction.SetContrast(colorOption)
                )
            }

        return BotSuggestion(
            id = ALTERNATIVE_CONTRAST_ID,
            level = level,
            message =
                "אולי צבעי תצוגה אחרים יהיו נוחים יותר. רוצה לבחור ערכת צבעים?",
            options = buttons
        )
    }

    /**
     * Creates an alternative reading-support action.
     *
     * The actions already exist in the application:
     * - read the current field;
     * - read the whole screen.
     */
    private fun buildAlternativeReadingSuggestion(
        distressMode: DistressMode,
        level: Int
    ): BotSuggestion {

        val message =
            when (distressMode) {

                DistressMode.VOICE_RECORDING ->
                    "אפשר לעצור לרגע ולהקשיב להסבר. מה תרצה/י שאקריא?"

                DistressMode.FORM_FILLING ->
                    "אפשר להקל על הקריאה באמצעות הקראה קולית. מה תרצה/י לשמוע?"
            }

        return BotSuggestion(
            id = ALTERNATIVE_READING_ID,
            level = level,
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
     * Creates an alternative support-information action.
     */
    private fun buildAlternativeEmergencySuggestion(
        level: Int
    ): BotSuggestion {

        return BotSuggestion(
            id = ALTERNATIVE_EMERGENCY_ID,
            level = level,
            message =
                "נוכל להציג לך גם  אפשרויות הסיוע ומספרי חירום שיוכלו לעזור לך.",
            options = listOf(
                BotSuggestionOption(
                    label = "הצג אפשרויות סיוע",
                    action = BotAction.ShowEmergencyContacts
                )
            )
        )
    }
}