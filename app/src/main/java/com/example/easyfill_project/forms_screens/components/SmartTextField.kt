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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.easyfill_project.distress_scoring.DistressMode
import com.example.easyfill_project.distress_scoring.DistressScoringManager
import com.example.easyfill_project.form_behavior_analysis.FormBehaviorTrackingController
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import com.example.easyfill_project.voiceanalysis.SpeechRateScorer
import com.example.easyfill_project.voiceanalysis.VoiceRmsScorer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SmartTextField(
    fieldId: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ttsManager: TextToSpeechManager,
    speechManager: SpeechToTextManager,
    maxLines: Int = 1,

    // Receives validation messages that depend on other form fields.
    // Example: the number of entered ages does not match the number of children.
    externalValidationMessage: String? = null,

    // Reports which field the user most recently selected.
    onFocusedFieldChange: (String) -> Unit = {}
) {
    val bringIntoViewRequester = remember {
        BringIntoViewRequester()
    }

    val scope = rememberCoroutineScope()

    var isListening by remember {
        mutableStateOf(false)
    }

    var showRecorderDialog by remember {
        mutableStateOf(false)
    }

    var isFocused by remember {
        mutableStateOf(false)
    }

    // Prevents validation messages from appearing before the user
    // has interacted with the field.
    var hasReceivedFocus by remember(fieldId) {
        mutableStateOf(false)
    }

    // Controls whether validation messages should currently be displayed.
    var shouldShowValidationError by remember(fieldId) {
        mutableStateOf(false)
    }

    // Checks validation rules that depend only on the current field.
    val validationError = if (shouldShowValidationError) {
        FieldInputRules.validate(
            fieldId = fieldId,
            value = value
        )
    } else {
        null
    }

    // Converts the internal validation error into a user-facing message.
    val validationMessage = validationError?.let { error ->
        FieldValidationMessages.getMessage(error)
    }

    // Prefers the field's own validation error and then checks
    // for an external error that depends on other form fields.
    val displayedValidationMessage =
        if (shouldShowValidationError) {
            validationMessage ?: externalValidationMessage
        } else {
            null
        }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.d(
                "MIC_PERMISSION",
                "Microphone permission denied"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopSpeechRecognition()
            DistressScoringManager.setMode(
                DistressMode.FORM_FILLING
            )
        }
    }

    LaunchedEffect(isFocused) {
        while (isFocused) {
            delay(1000)

            FormBehaviorTrackingController.checkCurrentFieldIdle(
                fieldId
            )
        }
    }

    Column {
        OutlinedTextField(
            value = value,

            onValueChange = { typedValue ->
                val sanitizedValue =
                    FieldInputRules.sanitizeTypedInput(
                        fieldId = fieldId,
                        input = typedValue
                    )

                if (sanitizedValue != value) {
                    FormBehaviorTrackingController.onFieldValueChanged(
                        fieldId = fieldId,
                        oldValue = value,
                        newValue = sanitizedValue
                    )

                    onValueChange(sanitizedValue)
                }
            },

            label = {
                Text(label)
            },

            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(
                    bringIntoViewRequester
                )
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused

                    if (focusState.isFocused) {
                        hasReceivedFocus = true

                        // Hides validation while the user is correcting the value.
                        shouldShowValidationError = false

                        DistressScoringManager.setMode(
                            DistressMode.FORM_FILLING
                        )

                        // Sends the current field ID to the parent screen.
                        // Example: "firstName", "idNumber", "mailingCity".
                        onFocusedFieldChange(fieldId)

                        FormBehaviorTrackingController.onFieldFocused(
                            fieldId = fieldId,
                            currentValue = value
                        )

                        scope.launch {
                            delay(300)
                            bringIntoViewRequester.bringIntoView()
                        }
                    } else {
                        // Displays validation only after the user has visited
                        // and then left the field.
                        if (hasReceivedFocus) {
                            shouldShowValidationError = true
                        }

                        FormBehaviorTrackingController.onFieldUnfocused(
                            fieldId
                        )
                    }
                },

            minLines = 1,
            maxLines = maxLines,

            // Displays either an internal field error or an external
            // cross-field validation error.
            isError = displayedValidationMessage != null,

            supportingText =
                if (displayedValidationMessage != null) {
                    {
                        Text(displayedValidationMessage)
                    }
                } else {
                    null
                },

            keyboardOptions = KeyboardOptions(
                keyboardType =
                    FieldInputRules.getKeyboardType(fieldId),

                imeAction = if (maxLines == 1) {
                    ImeAction.Next
                } else {
                    ImeAction.Default
                }
            ),

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
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "השמעה",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        enabled = !isListening,

                        onClick = {
                            val hasPermission =
                                ContextCompat.checkSelfPermission(
                                    speechManager.context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPermission) {
                                permissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )

                                return@IconButton
                            }

                            isListening = true
                            showRecorderDialog = true
                            isFocused = false

                            DistressScoringManager.setMode(
                                DistressMode.VOICE_RECORDING
                            )

                            speechManager.startSpeechRecognition(
                                onResult = { text ->
                                    FormBehaviorTrackingController
                                        .onFieldValueChanged(
                                            fieldId = fieldId,
                                            oldValue = value,
                                            newValue = text
                                        )

                                    onValueChange(text)
                                },

                                onSpeechStarted = {
                                    Log.d(
                                        "STT_UI",
                                        "Speech started in SmartTextField"
                                    )
                                },

                                onAnalysisResult = { analysis ->
                                    Log.d(
                                        "STT_ANALYSIS",
                                        analysis.toString()
                                    )

                                    val currentSpeechRate =
                                        analysis.speechRateWordsPerSecond

                                    val userId =
                                        FirebaseAuth.getInstance()
                                            .currentUser
                                            ?.uid

                                    if (userId == null) {
                                        Log.d(
                                            "VOICE_ANALYSIS",
                                            "No logged in user"
                                        )

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
                                                document.getDouble(
                                                    "speechRateWordsPerSecond"
                                                )

                                            val baselineRmsVariation =
                                                document.getDouble(
                                                    "rmsVariation"
                                                )

                                            val speechRateScore =
                                                SpeechRateScorer
                                                    .calculateVoiceScore(
                                                        baselineSpeechRate =
                                                            baselineSpeechRate,
                                                        currentSpeechRate =
                                                            currentSpeechRate
                                                    )

                                            val rmsScore =
                                                VoiceRmsScorer.calculateScore(
                                                    baselineVariation =
                                                        baselineRmsVariation,
                                                    currentVariation =
                                                        analysis
                                                            .rmsVariation
                                                            .toDouble()
                                                )

                                            val voiceScore =
                                                speechRateScore + rmsScore

                                            // SpeechRateScorer returns:
                                            // 0 -> normal
                                            // 1 -> moderate deviation
                                            // 2 -> high deviation
                                            //
                                            // VoiceRmsScorer returns:
                                            // 0 -> close to baseline
                                            // 1 -> moderate deviation
                                            // 2 -> high deviation
                                            //
                                            // voiceScore = 0..4

                                            DistressScoringManager
                                                .updateVoiceScore(
                                                    voiceScore
                                                )

                                            Log.d(
                                                "VOICE_ANALYSIS",
                                                "baselineRate=$baselineSpeechRate " +
                                                        "currentRate=$currentSpeechRate " +
                                                        "baselineRmsVariation=$baselineRmsVariation " +
                                                        "currentRmsVariation=${analysis.rmsVariation} " +
                                                        "speechRateScore=$speechRateScore " +
                                                        "rmsScore=$rmsScore " +
                                                        "totalVoiceScore=$voiceScore"
                                            )
                                        }
                                        .addOnFailureListener { error ->
                                            Log.e(
                                                "VOICE_ANALYSIS",
                                                "Failed to get baseline",
                                                error
                                            )
                                        }
                                },

                                onFinished = {
                                    isListening = false
                                    showRecorderDialog = false

                                    DistressScoringManager.setMode(
                                        DistressMode.FORM_FILLING
                                    )
                                }
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "הקלטה",
                            tint = if (isListening) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            },

            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor =
                    MaterialTheme.colorScheme.onSurface,

                unfocusedTextColor =
                    MaterialTheme.colorScheme.onSurface,

                focusedLabelColor =
                    MaterialTheme.colorScheme.onSurface,

                unfocusedLabelColor =
                    MaterialTheme.colorScheme.onSurface,

                cursorColor =
                    MaterialTheme.colorScheme.onSurface,

                focusedBorderColor =
                    MaterialTheme.colorScheme.onSurface,

                unfocusedBorderColor =
                    MaterialTheme.colorScheme.onSurface,

                focusedContainerColor =
                    MaterialTheme.colorScheme.surface,

                unfocusedContainerColor =
                    MaterialTheme.colorScheme.surface
            )
        )

        if (showRecorderDialog) {
            AlertDialog(
                onDismissRequest = {},

                containerColor =
                    MaterialTheme.colorScheme.surface,

                titleContentColor =
                    MaterialTheme.colorScheme.onSurface,

                textContentColor =
                    MaterialTheme.colorScheme.onSurface,

                title = {
                    Text("מקליט ...")
                },

                text = {
                    Column(
                        horizontalAlignment =
                            androidx.compose.ui.Alignment.CenterHorizontally,

                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val infiniteTransition =
                            rememberInfiniteTransition(
                                label = "pulse"
                            )

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

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        Text("מקשיב ...")

                        Text(
                            "הקלט/י עכשיו, לסיום ההקלטה לחץ/י לעצירה"
                        )
                    }
                },

                confirmButton = {
                    Button(
                        onClick = {
                            Log.d(
                                "STT_UI",
                                "Stop button clicked"
                            )

                            speechManager.stopAndAnalyze()
                        },

                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "stop"
                        )

                        Text("עצירה")
                    }
                }
            )
        }
    }
}