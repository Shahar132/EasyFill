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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//premission for audio record
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

// import of audio record
import com.example.easyfill_project.voiceanalysis.AudioRecordManager
//import volume collection
import com.example.easyfill_project.voiceanalysis.VolumeAnalysisCollector

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

    // Creates AudioRecordManager once for this text field
    val audioRecordManager = remember {
        AudioRecordManager(speechManager.context)
    }

    val volumeCollector = remember {
        VolumeAnalysisCollector()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            println("Microphone permission denied")
        }
    }
    // Stops recording if this composable leaves the screen
    DisposableEffect(Unit) {
        onDispose {
            audioRecordManager.stopRecording()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->

        // Stop AudioRecord when Google speech-to-text finishes
        audioRecordManager.stopRecording()

        // Analyze all volume values collected during recording
        val volumeResult = volumeCollector.stopAndAnalyze()
        println("Volume analysis result: $volumeResult")

        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()

        if (!spokenText.isNullOrBlank()) {
            val normalizedText = speechManager.normalizeHebrewNumbers(spokenText)
            onValueChange(normalizedText)
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
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                speechManager.context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@IconButton
                            }

                            volumeCollector.start()

                            audioRecordManager.startRecording { volumeDb ->
                                volumeCollector.addVolume(volumeDb)
                                println("Volume dB: $volumeDb")
                            }

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