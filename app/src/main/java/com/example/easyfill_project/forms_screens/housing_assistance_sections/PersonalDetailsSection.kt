package com.example.easyfill_project.forms_screens.housing_assistance_sections

import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.texttospeech.TextToSpeechManager

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



@Composable
fun PersonalDetailsSection(
    autofill: Map<String, String?> = emptyMap()
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val speechManager = remember { SpeechToTextManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmartTextField(
            label = "שם משפחה",
            valueFromAzure = autofill["lastName"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "שם פרטי",
            valueFromAzure = autofill["firstName"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר תעודת זהות",
            valueFromAzure = autofill["idNumber"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "רחוב",
            valueFromAzure = autofill["street"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר בית",
            valueFromAzure = autofill["houseNumber"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "יישוב",
            valueFromAzure = autofill["city"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מיקוד",
            valueFromAzure = autofill["zipCode"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "טלפון נייד",
            valueFromAzure = autofill["phone"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "דואר אלקטרוני",
            valueFromAzure = autofill["email"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )
    }
}

@Composable
fun SmartTextField(
    label: String,
    valueFromAzure: String?,
    ttsManager: TextToSpeechManager,
    speechManager: SpeechToTextManager
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
            value = spokenText
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