package com.example.easyfill_project.chatbot.logic

import android.content.Context
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.screen.ContrastMode
import com.example.easyfill_project.screen.FontSizeMode
import com.example.easyfill_project.screen.SoundManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager

/**
 * Performs the action selected by the user.
 * This class does not choose suggestions and does not display UI.
 */
object BotSupportActionHandler {

    fun handle(
        action: BotAction,
        context: Context,
        ttsManager: TextToSpeechManager,

        // Text prepared for the entire current screen.
        screenTextToRead: String,

        // Explanation of the field currently focused by the user.
        currentFieldTextToRead: String,

        // Updates the TTS state stored by the parent composable.
        onTtsSpeakingChange: (Boolean) -> Unit,

        // Updates the real app color state.
        onContrastModeChange: (ContrastMode) -> Unit,

        // Updates the real app font-size state.
        onFontSizeModeChange: (FontSizeMode) -> Unit
    ) {
        when (action) {

            // Reads the whole current screen.
            BotAction.ReadAloud -> {
                if (screenTextToRead.isNotBlank()) {
                    ttsManager.speak(screenTextToRead)
                    onTtsSpeakingChange(true)
                }
            }

            // Reads only the currently focused field.
            BotAction.ReadCurrentField -> {

                // Inside the housing form, use the selected field explanation.
                // Outside the form, or when no field explanation exists,
                // use the full current-screen explanation instead.
                val textToRead = currentFieldTextToRead
                    .ifBlank { screenTextToRead }

                if (textToRead.isNotBlank()) {
                    ttsManager.speak(textToRead)
                    onTtsSpeakingChange(true)
                }
            }

            // Starts the exact sound selected in the suggestion button.
            is BotAction.PlaySound -> {
                SoundManager.play(
                    context = context,
                    soundName = action.option.key,
                    soundRes = action.option.soundRes
                )
            }

            // Applies the exact color mode selected by the user.
            is BotAction.SetContrast -> {
                onContrastModeChange(action.option.mode)
            }

            // Applies the exact font size selected by the user.
            is BotAction.SetFontSize -> {
                onFontSizeModeChange(action.option.mode)
            }

            // No external operation is required.
            // FloatingChatOverlay displays the information returned by
            // BotActionMessageProvider.
            BotAction.ShowEmergencyContacts -> Unit

            BotAction.None -> Unit
        }
    }
}