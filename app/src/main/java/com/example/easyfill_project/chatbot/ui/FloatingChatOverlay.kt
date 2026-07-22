package com.example.easyfill_project.chatbot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.easyfill_project.R
import com.example.easyfill_project.chatbot.logic.BotActionMessageProvider
import com.example.easyfill_project.chatbot.logic.BotSuggestion
import com.example.easyfill_project.chatbot.logic.BotSuggestionBuilder
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.distress_scoring.DistressMode
import com.example.easyfill_project.distress_scoring.DistressUiEvent
import com.example.easyfill_project.distress_scoring.DistressConfirmationManager

import kotlinx.coroutines.delay

//SetContrast → color settings
//SetFontSize → font settings
//PlaySound → music settings
//ReadAloud / ReadCurrentField / emergency contacts → no settings button
private enum class SettingsDestination {
    COLOR,
    FONT_SIZE,
    MUSIC
}


@Composable
fun FloatingChatOverlay(
    modifier: Modifier = Modifier,
    distressSnapshot: DistressSnapshot = DistressSnapshot(),
    distressMode: DistressMode = DistressMode.FORM_FILLING,
    appState: BotAppState = BotAppState(),

    // Event created by DistressConfirmationManager.
    distressUiEvent: DistressUiEvent? = null,

    onBotAction: (BotAction) -> Unit = {},

    // Reports the exact suggestion whose action button was selected.
    onSuggestionAccepted: (BotSuggestion) -> Unit,

    // Reports that an action suggestion was actually displayed.
    // The manager stores its ID so the same action is not offered again.
    onSuggestionDisplayed: (BotSuggestion) -> Unit = {},

    // Reports that no valid unused alternative action was available.
// The manager can continue with a calming message instead of getting stuck.
    onAlternativeSuggestionUnavailable: () -> Unit = {},

// Reports that neither the default suggestion nor an unused
// alternative suggestion could be created.
    onDefaultSuggestionUnavailable: () -> Unit = {},

// Reports that the success message shown after an accepted action
// has been closed or completed.
    onAcceptedActionMessageClosed: () -> Unit = {},

    // Reports that the user pressed "לא עכשיו".
    onSuggestionDismissed: (BotSuggestion) -> Unit = {},

    // Reports that a general calming message was closed.
    onCalmingMessageClosed: () -> Unit = {},

    onNavigateToColorSettings: () -> Unit = {},
    onNavigateToFontSettings: () -> Unit = {},
    onNavigateToMusicSettings: () -> Unit = {},

    onUndoAction: (
        action: BotAction,
        previousState: BotAppState
    ) -> Unit = { _, _ -> }
) {
    var isChatOpen by remember {
        mutableStateOf(false)
    }

    val suggestionQueue = remember {
        mutableStateListOf<BotSuggestion>()
    }


    var successMessage by remember {
        mutableStateOf<String?>(null)
    }


    var settingsDestination by remember {
        mutableStateOf<SettingsDestination?>(null)
    }

    // Stores the application settings before the action was executed.
    var previousAppState by remember {
        mutableStateOf<BotAppState?>(null)
    }

// Stores the exact action that was executed.
    var lastExecutedAction by remember {
        mutableStateOf<BotAction?>(null)
    }
    val currentSuggestion =
        suggestionQueue.firstOrNull()

    /*
     * Once a chatbot suggestion exists, its distress level becomes
     * the UI severity until the user handles that suggestion.
     *
     * This prevents the alert color/text from disappearing when the
     * live distress score drops.
     */
    val severityLevel =
        currentSuggestion?.level ?: distressSnapshot.globalScore

    /**
     * Listen only to events created by DistressConfirmationManager.
     *
     * The overlay no longer decides whether the distress level is stable.
     * It only converts a manager event into something that can be displayed.
     */
    /**
     * Handle every manager event exactly once.
     *
     * eventId is unique, so a new event restarts this effect.
     * Changes to app settings should not process the same event again.
     */
    LaunchedEffect(distressUiEvent?.eventId) {
        when (val event = distressUiEvent) {

            /**
             * Build the normal default action belonging to the confirmed level.
             */
            is DistressUiEvent.ShowDefaultSuggestion -> {

                /*
                 * Use the source saved when the alert was created.
                 * Do not rely on the current live distressMode because the mode
                 * may already have returned to FORM_FILLING after recording stops.
                 */
                val suggestionMode =
                    when (event.source) {
                        DistressUiEvent.DistressAlertSource.FORM_FILLING ->
                            DistressMode.FORM_FILLING

                        DistressUiEvent.DistressAlertSource.VOICE_RECORDING ->
                            DistressMode.VOICE_RECORDING
                    }

                val suggestion =
                    BotSuggestionBuilder.buildSuggestion(
                        severityLevel = event.level,
                        distressMode = suggestionMode,
                        appState = appState,
                        excludedSuggestionIds = event.excludedSuggestionIds
                    )

                suggestionQueue.clear()

                if (suggestion != null) {
                    suggestionQueue.add(suggestion)
                    onSuggestionDisplayed(suggestion)
                } else {
                    onDefaultSuggestionUnavailable()
                }

                successMessage = null
                settingsDestination = null
                previousAppState = null
                lastExecutedAction = null
                isChatOpen = false
            }

            /**
             * Repeat the exact suggestion that was previously dismissed.
             */
            is DistressUiEvent.ShowExactSuggestion -> {
                suggestionQueue.clear()
                suggestionQueue.add(event.suggestion)
                onSuggestionDisplayed(event.suggestion)

                successMessage = null
                settingsDestination = null
                previousAppState = null
                lastExecutedAction = null
                isChatOpen = false
            }

            /**
             * Ask BotSuggestionBuilder for a different, unused action.
             *
             * excludedSuggestionIds contains every action already displayed,
             * accepted, dismissed, or reserved as the original default.
             */
            is DistressUiEvent.ShowAlternativeSuggestion -> {
                /*
                 * Use the source stored when the event was created.
                 *
                 * A recording may already be finished, so the current live
                 * distressMode may have returned to FORM_FILLING.
                 */
                val suggestionMode =
                    when (event.source) {
                        DistressUiEvent.DistressAlertSource.FORM_FILLING ->
                            DistressMode.FORM_FILLING

                        DistressUiEvent.DistressAlertSource.VOICE_RECORDING ->
                            DistressMode.VOICE_RECORDING
                    }

                val alternativeSuggestion =
                    BotSuggestionBuilder.buildAlternativeSuggestion(
                        severityLevel = event.level,
                        distressMode = suggestionMode,
                        appState = appState,
                        excludedSuggestionIds =
                            event.excludedSuggestionIds
                    )

                suggestionQueue.clear()

                if (alternativeSuggestion != null) {
                    suggestionQueue.add(alternativeSuggestion)
                    onSuggestionDisplayed(alternativeSuggestion)
                } else {
                    onAlternativeSuggestionUnavailable()
                }

                successMessage = null
                settingsDestination = null
                previousAppState = null
                lastExecutedAction = null
                isChatOpen = false
            }

            /**
             * Convert a calming message into a BotSuggestion without options.
             *
             * Empty options tell the UI that this is a message,
             * not an action suggestion.
             */
            is DistressUiEvent.ShowCalmingMessage -> {
                suggestionQueue.clear()

                suggestionQueue.add(
                    BotSuggestion(
                        id = "calming_${event.eventId}",
                        level = event.level,
                        message = event.message,
                        options = emptyList()
                    )
                )

                successMessage = null
                settingsDestination = null
                previousAppState = null
                lastExecutedAction = null
                isChatOpen = false
            }

            is DistressUiEvent.Reset -> {

                /*
                 * Never clear a pending suggestion automatically.
                 *
                 * DistressConfirmationManager now emits Reset only when
                 * there is no pending alert, but this keeps the UI safe.
                 */
                if (suggestionQueue.isEmpty()) {

                    successMessage = null
                    settingsDestination = null
                    previousAppState = null
                    lastExecutedAction = null
                    isChatOpen = false
                }
            }

            null -> Unit
        }
    }


    /**
     * Calming messages contain no action options.
     */
    val isCalmingMessage =
        currentSuggestion != null &&
                currentSuggestion.options.isEmpty()

    // Never keep the popup open without an available suggestion.
    LaunchedEffect(currentSuggestion) {
        if (currentSuggestion == null) {
            isChatOpen = false
        }
    }


    // After showing a successful action message,
    // close the popup and remove the completed suggestion.
    LaunchedEffect(
        successMessage,
        settingsDestination
    ) {

        if (
            successMessage != null &&
            settingsDestination == null
        ) {
            //meaning If the action is NOT configurable (for example, "Call emergency contact"), then:
            //successMessage != null
            //settingsDestination == null
            //The condition is true.
            //The success message is shown for 4 seconds, then the popup closes automatically.
            delay(4000)

            if (suggestionQueue.isNotEmpty()) {
                suggestionQueue.removeAt(0)
            }

            // Clear all temporary chatbot state.
            successMessage = null
            settingsDestination = null
            previousAppState = null
            lastExecutedAction = null
            isChatOpen = false


            /**
             * The success message finished automatically.
             * The manager may now begin the accepted-flow delay
             * before showing a calming message.
             */
            onAcceptedActionMessageClosed()
        }
        //If the action IS configurable (for example, changing the color, font size, or music), then:
        //successMessage != null
        //settingsDestination == SettingsDestination.COLOR (or FONT_SIZE / MUSIC)
        // The condition is false.
        //The delay(4000) is never executed.
    }

    val alertColor =
        when (severityLevel) {
            0 -> Color.Transparent
            1 -> Color(0xFF4CAF50)
            2 -> Color(0xFFE1CC13)
            3 -> Color(0xFFFF5722)
            else -> Color(0xFFB92014)
        }

    val alertText =
        when (severityLevel) {
            0 -> ""
            1 -> "יש לי הצעה קטנה"
            2 -> "\u202Bאפשר לעזור?\u202C"
            3 -> "\u202Bרוצה שאקל עליך\u202C"
            else -> "יש אפשרויות סיוע"
        }

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Chatbot icon.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary
                )
                .clickable {

                    val suggestion = currentSuggestion

                    if (suggestion != null) {

                        if (!isChatOpen) {
                            /*
                             * The alert is visible.
                             * Pressing the chatbot icon opens the suggestion popup.
                             */
                            isChatOpen = true

                        } else {
                            /*
                             * The popup is already open.
                             *
                             * Pressing the chatbot icon again is treated exactly
                             * like pressing "לא עכשיו".
                             */
                            onSuggestionDismissed(suggestion)

                            if (suggestionQueue.isNotEmpty()) {
                                suggestionQueue.removeAt(0)
                            }

                            successMessage = null
                            settingsDestination = null
                            previousAppState = null
                            lastExecutedAction = null

                            isChatOpen = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.chatbot_icon
                ),
                contentDescription =
                    "פתיחת צ'אטבוט EasyFill",
                modifier = Modifier.size(56.dp)
            )
        }

        // Alert layout — unchanged.
        if (
            !isChatOpen &&
            currentSuggestion != null &&
            severityLevel > 0 &&
            alertText.isNotBlank()
        ) {
            CompositionLocalProvider(
                LocalLayoutDirection provides
                        LayoutDirection.Ltr
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentSize(
                            align = Alignment.TopEnd,
                            unbounded = true
                        )
                        .absoluteOffset(
                            x = 0.dp,
                            y = (-26).dp
                        )
                        .zIndex(2f),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 190.dp)
                            .background(
                                color = Color.White,
                                shape =
                                    RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = alertColor,
                                shape =
                                    RoundedCornerShape(12.dp)
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                    ) {
                        Text(
                            text = alertText,
                            color = alertColor,
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Medium,
                            maxLines = 2
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(alertColor),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded =
                isChatOpen &&
                        currentSuggestion != null,

            onDismissRequest = {
                when {

                    /**
                     * Closing a calming message by tapping outside should
                     * behave exactly like pressing its "סגור" button.
                     */
                    isCalmingMessage -> {
                        if (suggestionQueue.isNotEmpty()) {
                            suggestionQueue.removeAt(0)
                        }

                        isChatOpen = false

                        onCalmingMessageClosed()
                    }

                    /**
                     * Closing a success card by tapping outside should
                     * behave exactly like closing the success card manually.
                     */
                    successMessage != null -> {
                        if (suggestionQueue.isNotEmpty()) {
                            suggestionQueue.removeAt(0)
                        }

                        successMessage = null
                        settingsDestination = null
                        previousAppState = null
                        lastExecutedAction = null
                        isChatOpen = false

                        /**
                         * Start the accepted-action cooldown only after
                         * the success card has finished.
                         */
                        onAcceptedActionMessageClosed()
                    }

                    /**
                     * Tapping outside a normal action suggestion should behave
                     * exactly like pressing the "לא עכשיו" button.
                     *
                     * The manager will:
                     * 1. Start the dismissed-support flow.
                     * 2. Show a calming message.
                     * 3. Later repeat this exact suggestion.
                     */
                    else -> {

                        /*
                         * Tapping outside a normal action suggestion has the same
                         * meaning as pressing "לא עכשיו".
                         *
                         * The suggestion is explicitly dismissed by the user, so:
                         *
                         * 1. Notify DistressConfirmationManager.
                         * 2. Remove the suggestion from the local queue.
                         * 3. Clear the temporary popup state.
                         * 4. Close the popup.
                         *
                         * Because the suggestion is removed, the colored alert will
                         * not appear again immediately after the popup closes.
                         */
                        val dismissedSuggestion = currentSuggestion

                        if (dismissedSuggestion != null) {
                            onSuggestionDismissed(dismissedSuggestion)
                        }

                        if (suggestionQueue.isNotEmpty()) {
                            suggestionQueue.removeAt(0)
                        }

                        successMessage = null
                        settingsDestination = null
                        previousAppState = null
                        lastExecutedAction = null

                        isChatOpen = false
                    }
                }
            },
            modifier = Modifier.width(260.dp),
            offset = DpOffset(
                x = 24.dp,
                y = 8.dp
            ),
            shape = RoundedCornerShape(20.dp),
            containerColor =
                MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            border = BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.secondary
            )
        ) {
            if (currentSuggestion != null) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (successMessage != null) {
                        // Display the success message after the chatbot action
                        // (for example: "Color changed successfully").
                        Text(
                            text = successMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Build an explanation message according to the action.
                        // If the action is not configurable, this stays null.
                        val settingsExplanation = when (settingsDestination) {

                            // User changed the color theme.
                            SettingsDestination.COLOR ->
                                "אם תרצה לשנות לצבע אחר, תוכל לעבור לדף הגדרות הצבע."

                            // User changed the font size.
                            SettingsDestination.FONT_SIZE ->
                                "אם תרצה לשנות לגודל טקסט אחר, תוכל לעבור לדף הגדרות גודל טקסט."

                            // User changed the music.
                            SettingsDestination.MUSIC ->
                                "אם תרצה לבחור במוזיקה אחרת או לבטל, תוכל לעבור לדף הגדרות מוזיקת רקע."

                            // No settings screen is related to this action.
                            null -> null
                        }

                        // Build the text that will appear on the navigation button.
                        // only configurable actions receive a button.
                        val settingsButtonText = when (settingsDestination) {

                            SettingsDestination.COLOR ->
                                "מעבר להגדרות צבע"

                            SettingsDestination.FONT_SIZE ->
                                "מעבר להגדרות טקסט"

                            SettingsDestination.MUSIC ->
                                "מעבר להגדרות מוזיקה"

                            null -> null
                        }

                        // Only show the extra explanation and navigation button
                        // when this action has a related settings screen.
                        if (
                            settingsExplanation != null &&
                            settingsButtonText != null
                        ) {

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            // Explain to the user that they can always
                            // change this setting later manually.
                            Text(
                                text = settingsExplanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            // Button that navigates directly to the relevant
                            // settings screen.
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),

                                onClick = {

                                    // Save the destination because we are about
                                    // to clear it before navigating.
                                    val destination = settingsDestination

                                    // Remove the completed chatbot suggestion.
                                    if (suggestionQueue.isNotEmpty()) {
                                        suggestionQueue.removeAt(0)
                                    }

                                    // Reset chatbot state.
                                    successMessage = null
                                    settingsDestination = null
                                    isChatOpen = false
                                    previousAppState = null
                                    lastExecutedAction = null

                                    /**
                                     * The accepted-action success card is now finished.
                                     */
                                    onAcceptedActionMessageClosed()


                                    // Navigate to the correct settings screen.
                                    when (destination) {

                                        SettingsDestination.COLOR ->
                                            onNavigateToColorSettings()

                                        SettingsDestination.FONT_SIZE ->
                                            onNavigateToFontSettings()

                                        SettingsDestination.MUSIC ->
                                            onNavigateToMusicSettings()

                                        null -> Unit
                                    }
                                },

                                border = BorderStroke(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                ),

                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.primary,
                                    contentColor =
                                        MaterialTheme.colorScheme.secondary
                                )
                            ) {

                                Text(
                                    text = settingsButtonText,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            // Use LTR only for physical placement:
                            // undo button on the left and close button on the right.
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Undo the action and restore the previous app state.
                                    OutlinedButton(
                                        onClick = {
                                            val action = lastExecutedAction
                                            val oldState = previousAppState

                                            // Undo only when both required values exist.
                                            if (
                                                action != null &&
                                                oldState != null
                                            ) {
                                                onUndoAction(
                                                    action,
                                                    oldState
                                                )
                                            }

                                            // Remove the completed suggestion.
                                            if (suggestionQueue.isNotEmpty()) {
                                                suggestionQueue.removeAt(0)
                                            }

                                            // Clear all temporary chatbot state.
                                            successMessage = null
                                            settingsDestination = null
                                            previousAppState = null
                                            lastExecutedAction = null
                                            isChatOpen = false

                                            /**
                                             * Even though the user undid the setting, the success card
                                             * has been handled and closed.
                                             */
                                            onAcceptedActionMessageClosed()

                                        },
                                        border = BorderStroke(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.secondary
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.primary,
                                            contentColor =
                                                MaterialTheme.colorScheme.secondary
                                        )
                                    ) {
                                        CompositionLocalProvider(
                                            LocalLayoutDirection provides LayoutDirection.Rtl
                                        ) {
                                            Text(
                                                text = "החזר לקודם",
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }

                                    // Close the popup without undoing the action.
                                    OutlinedButton(
                                        onClick = {
                                            // Remove the completed suggestion.
                                            if (suggestionQueue.isNotEmpty()) {
                                                suggestionQueue.removeAt(0)
                                            }

                                            // Keep the performed action, but clear chatbot state.
                                            successMessage = null
                                            settingsDestination = null
                                            previousAppState = null
                                            lastExecutedAction = null
                                            isChatOpen = false

                                            /**
                                             * The success card was closed manually.
                                             */
                                            onAcceptedActionMessageClosed()
                                        },
                                        border = BorderStroke(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.secondary
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.primary,
                                            contentColor =
                                                MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        CompositionLocalProvider(
                                            LocalLayoutDirection provides LayoutDirection.Rtl
                                        ) {
                                            Text(
                                                text = "סגור",
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    } else {

                        // This part is executed BEFORE the user chooses an action.
                        // Here you show:
                        // - the chatbot suggestion text
                        // - the action buttons
                        // - the "לא עכשיו" button
                        // After the user presses one of the action buttons,
                        // successMessage becomes non-null, so execution enters
                        // the first branch above instead.

                        Text(
                            text = currentSuggestion.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        // Action buttons.
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp)
                        ) {
                            currentSuggestion.options.forEach { option ->
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        // Determine whether the action supports settings and undo.
                                        settingsDestination = when (option.action) {
                                            is BotAction.SetContrast ->
                                                SettingsDestination.COLOR

                                            is BotAction.SetFontSize ->
                                                SettingsDestination.FONT_SIZE

                                            is BotAction.PlaySound ->
                                                SettingsDestination.MUSIC

                                            else -> null
                                        }

                                        if (settingsDestination != null) {
                                            // Save the state before changing it so undo can restore it.
                                            previousAppState = appState
                                            lastExecutedAction = option.action
                                        } else {
                                            // Normal actions do not need undo information.
                                            previousAppState = null
                                            lastExecutedAction = null
                                        }
                                        // Report the exact suggestion that was accepted.
                                        // Its ID is stored so this action will not be offered again.
                                        onSuggestionAccepted(currentSuggestion)

                                        // Perform the selected action.
                                        onBotAction(option.action)

                                        // Show the matching confirmation message.
                                        successMessage =
                                            BotActionMessageProvider.getMessage(option.action)
                                    },
                                    border = BorderStroke(
                                        width = 2.dp,
                                        color =
                                            MaterialTheme.colorScheme.secondary
                                    ),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.primary,
                                            contentColor =
                                                MaterialTheme.colorScheme.secondary
                                        )
                                ) {
                                    Text(
                                        text = option.label,
                                        color =
                                            MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        // Show "לא עכשיו" only for action suggestions.
                        // Calming messages receive a normal Close button instead.
                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        /**
                         * Normal action suggestion:
                         *
                         * Show "לא עכשיו" because the user is deciding
                         * whether to perform one of the suggested actions.
                         */
                        if (!isCalmingMessage) {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {

                                            /**
                                             * Report the exact dismissed suggestion
                                             * to DistressConfirmationManager.
                                             *
                                             * The manager will:
                                             * 1. Wait before the next support item.
                                             * 2. Show a calming message.
                                             * 3. Later repeat this exact suggestion.
                                             */
                                            currentSuggestion?.let { suggestion ->
                                                onSuggestionDismissed(suggestion)
                                            }

                                            // Remove the currently displayed suggestion.
                                            if (suggestionQueue.isNotEmpty()) {
                                                suggestionQueue.removeAt(0)
                                            }

                                            // Clear local UI state.
                                            successMessage = null
                                            settingsDestination = null
                                            previousAppState = null
                                            lastExecutedAction = null
                                            isChatOpen = false
                                        },
                                        border = BorderStroke(
                                            width = 2.dp,
                                            color =
                                                MaterialTheme.colorScheme.secondary
                                        ),
                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.primary,
                                                contentColor =
                                                    MaterialTheme.colorScheme.onPrimary
                                            )
                                    ) {
                                        CompositionLocalProvider(
                                            LocalLayoutDirection provides
                                                    LayoutDirection.Rtl
                                        ) {
                                            Text(
                                                text = "לא עכשיו",
                                                color =
                                                    MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        /**
                         * Calming message:
                         *
                         * Calming messages contain no BotAction options.
                         * Therefore, instead of "לא עכשיו", show one simple
                         * close button.
                         */
                        if (isCalmingMessage) {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {

                                            // Remove the calming message from the local queue.
                                            if (suggestionQueue.isNotEmpty()) {
                                                suggestionQueue.removeAt(0)
                                            }

                                            // Close the chatbot popup.
                                            isChatOpen = false

                                            /**
                                             * Tell DistressConfirmationManager that
                                             * the calming message was closed.
                                             *
                                             * The manager then decides what comes next:
                                             *
                                             * After an accepted action:
                                             * → wait and show another calming message.
                                             *
                                             * After "לא עכשיו":
                                             * → wait and repeat the dismissed action suggestion.
                                             */
                                            onCalmingMessageClosed()
                                        },
                                        border = BorderStroke(
                                            width = 2.dp,
                                            color =
                                                MaterialTheme.colorScheme.secondary
                                        ),
                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.primary,
                                                contentColor =
                                                    MaterialTheme.colorScheme.onPrimary
                                            )
                                    ) {
                                        CompositionLocalProvider(
                                            LocalLayoutDirection provides
                                                    LayoutDirection.Rtl
                                        ) {
                                            Text(
                                                text = "סגור",
                                                color =
                                                    MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}