package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun PersonalDetailsSection(
    autofill: Map<String, String?> = emptyMap()
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }

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
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "שם פרטי",
            valueFromAzure = autofill["firstName"],
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "מספר תעודת זהות",
            valueFromAzure = autofill["idNumber"],
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "רחוב",
            valueFromAzure = autofill["street"],
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "מספר בית",
            valueFromAzure = autofill["houseNumber"],
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "יישוב",
            valueFromAzure = autofill["city"],
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "מיקוד",
            valueFromAzure = autofill["zipCode"],
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "טלפון נייד",
            valueFromAzure = autofill["phone"],
            ttsManager = ttsManager
        )

        SmartTextField(
            label = "דואר אלקטרוני",
            valueFromAzure = autofill["email"],
            ttsManager = ttsManager
        )
    }
}

@Composable
fun SmartTextField(
    label: String,
    valueFromAzure: String?,
    ttsManager: TextToSpeechManager
) {
    var value by remember { mutableStateOf("") }
    var showSuggestion by remember { mutableStateOf(false) }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = { ttsManager.speak(label) }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "השמעה")
            }

            IconButton(onClick = { /* speech to text later */ }) {
                Icon(Icons.Default.Mic, contentDescription = "הקלטה")
            }

            IconButton(onClick = { showSuggestion = true }) {
                Icon(Icons.Default.Edit, contentDescription = "מילוי אוטומטי")
            }
        }

        if (showSuggestion && !valueFromAzure.isNullOrBlank()) {
            AssistChip(
                onClick = {
                    value = valueFromAzure
                    showSuggestion = false
                },
                label = {
                    Text("מילוי אוטומטי: $valueFromAzure")
                }
            )
        }
    }
}