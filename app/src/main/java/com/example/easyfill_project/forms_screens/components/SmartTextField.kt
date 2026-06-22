package com.example.easyfill_project.forms_screens.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.easyfill_project.form_behavior_analysis.FormBehaviorTrackingController
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

@Composable
fun SmartTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ttsManager: TextToSpeechManager,
    speechManager: SpeechToTextManager,
    maxLines: Int = 1,

    // Unique identifier for form behavior tracking.
    // Default is label so existing calls will not break.
    // Later, it is better to pass stable IDs like "firstName", "idNumber", etc.
    fieldId: String = label
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    var isListening by remember { mutableStateOf(false) }
    var showRecorderDialog by remember { mutableStateOf(false) }

    // Keeps track of the previous focus state.
    // This prevents starting the same field session more than once.
    var wasFocused by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.d("MIC_PERMISSION", "Microphone permission denied")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopSpeechRecognition()
        }
    }

    // Wrapper for text changes.
    // It reports the change to the form behavior tracker and then updates the real field value.
    val trackedOnValueChange: (String) -> Unit = { newValue ->
        FormBehaviorTrackingController.onFieldValueChanged(
            fieldId = fieldId,
            oldValue = value,
            newValue = newValue
        )

        onValueChange(newValue)
    }

// Checks if the user stays inside the current field without typing or deleting.
// This runs only while the field is focused, once per second.
// It does not save to Firebase and does not calculate distress yet.
// It only updates the current FieldBehaviorSession and writes logs.
    LaunchedEffect(wasFocused, fieldId) {
        if (wasFocused) {
            while (true) {
                delay(1000)

                FormBehaviorTrackingController.checkCurrentFieldIdle(
                    fieldId = fieldId
                )
            }
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = trackedOnValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focusState ->
                    val isFocusedNow = focusState.isFocused

                    // Field measurement starts when the user enters the text field.
                    if (isFocusedNow && !wasFocused) {
                        FormBehaviorTrackingController.onFieldFocused(
                            fieldId = fieldId,
                            currentValue = value
                        )

                        scope.launch {
                            delay(300)
                            bringIntoViewRequester.bringIntoView()
                        }
                    }

                    // Field measurement ends when the user leaves the text field.
                    if (!isFocusedNow && wasFocused) {
                        FormBehaviorTrackingController.onFieldUnfocused(
                            fieldId = fieldId
                        )
                    }

                    wasFocused = isFocusedNow
                },
            minLines = 1,
            maxLines = maxLines,
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
                        enabled = !isListening,
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                speechManager.context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@IconButton
                            }

                            isListening = true
                            showRecorderDialog = true

                            speechManager.startSpeechRecognition(
                                onResult = { text ->
                                    // Speech-to-text also changes the field value,
                                    // so it should be tracked like normal typing.
                                    trackedOnValueChange(text)
                                },
                                onAnalysisResult = { analysis ->
                                    Log.d("STT_ANALYSIS", analysis.toString())

                                    if (!analysis.isReliable) {
                                        Log.d(
                                            "VOICE_ANALYSIS",
                                            "Recording ignored because it is shorter than 15 seconds. Duration = ${analysis.durationSeconds}"
                                        )
                                    } else {
                                        Log.d("VOICE_ANALYSIS", "Recording is reliable, compare to baseline")
                                        Log.d("VOICE_ANALYSIS", analysis.toString())

                                        // Next step later:
                                        // get baseline from Firestore
                                        // compare current analysis to baseline
                                        // calculate stress score
                                    }
                                },
                                onFinished = {
                                    isListening = false
                                    showRecorderDialog = false
                                }
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "הקלטה",
                            tint = if (isListening)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
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

        if (showRecorderDialog) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = {
                    Text("מקליט ...")
                },
                text = {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.25f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "micScale"
                        )

                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "recording",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(90.dp)
                                .scale(scale)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("מקשיב ...")
                        Text("הקלט/י עכשיו, לסיום ההקלטה לחץ/י לעצירה")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            Log.d("STT_UI", "Stop button clicked")
                            speechManager.stopAndAnalyze()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "stop")
                        Text("עצירה")
                    }
                }
            )
        }
    }
}