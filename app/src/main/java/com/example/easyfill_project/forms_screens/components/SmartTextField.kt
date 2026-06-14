package com.example.easyfill_project.forms_screens.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.content.ContextCompat
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

@Composable
fun SmartTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ttsManager: TextToSpeechManager,
    speechManager: SpeechToTextManager,
    maxLines: Int = 1
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    var isListening by remember { mutableStateOf(false) }
    var showRecorderDialog by remember { mutableStateOf(false) }

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

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
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
                                    onValueChange(text)
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