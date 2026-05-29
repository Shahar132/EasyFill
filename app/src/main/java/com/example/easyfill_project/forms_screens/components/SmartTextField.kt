package com.example.easyfill_project.forms_screens.components

import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun SmartTextField(
    label: String,
    valueFromAzure: String?,
    ttsManager: TextToSpeechManager,
    speechManager: SpeechToTextManager,
    maxLines: Int = 1 // default
) {

    var value by remember { mutableStateOf("") }

    LaunchedEffect(valueFromAzure) {
        if (!valueFromAzure.isNullOrBlank()) {
            value = valueFromAzure
        }
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText =
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

        if (!spokenText.isNullOrBlank()) {

            // Normalize numbers here
            val normalizedText = speechManager.normalizeHebrewNumbers(spokenText)

            value = normalizedText
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(300)
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },

            // Icons INSIDE the TextField
            trailingIcon = {
                Row {
                    IconButton(
                        onClick = {
                            val textToRead = if (value.isBlank()) {
                                "נא למלא $label"
                            } else {
                                "$label, $value"
                            }

                            ttsManager.speak(textToRead)
                        }
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "השמעה",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            speechManager.startSpeechRecognition(speechLauncher)
                        }
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "הקלטה",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },

            //  Colors for text feilds
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

    }
}