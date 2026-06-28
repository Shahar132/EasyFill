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


import com.example.easyfill_project.distress_scoring.DistressScoringManager
import com.example.easyfill_project.voiceanalysis.SpeechRateScorer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.easyfill_project.voiceanalysis.VoiceRmsScorer

import com.example.easyfill_project.form_behavior_analysis.FormBehaviorTrackingController


@Composable
fun SmartTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ttsManager: TextToSpeechManager,
    speechManager: SpeechToTextManager,
    maxLines: Int = 1,
    fieldId: String = label
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    var isListening by remember { mutableStateOf(false) }
    var showRecorderDialog by remember { mutableStateOf(false) }
    var isFieldFocused by remember { mutableStateOf(false) }


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
    LaunchedEffect(isFieldFocused, fieldId) {
        while (isFieldFocused) {
            delay(5000)// Maybe change it to 10 seconds.
            FormBehaviorTrackingController.checkCurrentFieldIdle(fieldId)
        }
    }


    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                FormBehaviorTrackingController.onFieldValueChanged(
                    fieldId = fieldId,
                    oldValue = value,
                    newValue = newValue
                )

                onValueChange(newValue)
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        if (!isFieldFocused) {
                            isFieldFocused = true

                            FormBehaviorTrackingController.onFieldFocused(
                                fieldId = fieldId,
                                currentValue = value
                            )
                        }

                        scope.launch {
                            delay(300)
                            bringIntoViewRequester.bringIntoView()
                        }
                    } else {
                        if (isFieldFocused) {
                            isFieldFocused = false

                            FormBehaviorTrackingController.onFieldUnfocused(
                                fieldId = fieldId
                            )
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
                            // Clear the previous voice distress score before starting a new recording.
                            // The baseline remains saved in Firebase.
                            DistressScoringManager.updateVoiceScore(0)



                            speechManager.startSpeechRecognition(
                                onResult = { text ->
                                    FormBehaviorTrackingController.onFieldValueChanged(
                                        fieldId = fieldId,
                                        oldValue = value,
                                        newValue = text
                                    )

                                    onValueChange(text)
                                },
                                onSpeechStarted = {
                                    Log.d("STT_UI", "Speech started in SmartTextField")
                                },
                                onAnalysisResult = { analysis ->
                                    Log.d("STT_ANALYSIS", analysis.toString())

                                    val currentSpeechRate = analysis.speechRateWordsPerSecond

                                    val userId = FirebaseAuth.getInstance().currentUser?.uid

                                    if (userId == null) {
                                        Log.d("VOICE_ANALYSIS", "No logged in user")
                                        return@startSpeechRecognition
                                    }

                                    FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(userId)
                                        .collection("voiceParameters")
                                        .document("baseline")
                                        .get()
                                        .addOnSuccessListener { document ->

                                            val baselineSpeechRate =
                                                document.getDouble("speechRateWordsPerSecond")

                                            val baselineRmsVariation =
                                                document.getDouble("rmsVariation")

                                            val speechRateScore = SpeechRateScorer.calculateVoiceScore(
                                                baselineSpeechRate = baselineSpeechRate,
                                                currentSpeechRate = currentSpeechRate
                                            )

                                            val rmsScore = VoiceRmsScorer.calculateScore(
                                                baselineVariation = baselineRmsVariation,
                                                currentVariation = analysis.rmsVariation.toDouble()
                                            )

                                            val voiceScore = speechRateScore + rmsScore

                                            //SpeechRateScorer returns:
                                            //0 → normal (weighted deviation < 0.30)
                                            //1 → moderate deviation (0.30–0.49)
                                            //2 → high deviation (≥ 0.50)
                                            //VoiceRmsScorer returns :
                                            //0 → RMS variation close to baseline
                                            //1 → current RMS variation ≥ 1.5 × baseline
                                            //2 → current RMS variation ≥ 2.0 × baseline

                                            //->voiceScore = 0..4

                                            //send to distress scoring manager
                                            DistressScoringManager.updateVoiceScore(voiceScore)

                                            Log.d(
                                                "VOICE_ANALYSIS",
                                                "baselineRate=$baselineSpeechRate currentRate=$currentSpeechRate " +
                                                        "baselineRmsVariation=$baselineRmsVariation currentRmsVariation=${analysis.rmsVariation} " +
                                                        "speechRateScore=$speechRateScore rmsScore=$rmsScore totalVoiceScore=$voiceScore"
                                            )
                                        }
                                        .addOnFailureListener { error ->
                                            Log.e("VOICE_ANALYSIS", "Failed to get baseline", error)
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