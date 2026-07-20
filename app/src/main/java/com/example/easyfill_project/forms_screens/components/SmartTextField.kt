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
import androidx.compose.runtime.LaunchedEffect
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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext


@Composable
fun SmartTextField(
    fieldId: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ttsManager: TextToSpeechManager,
    speechManager: SpeechToTextManager,
    maxLines: Int = 1,

    // Reports which field the user most recently selected.
    onFocusedFieldChange: (String) -> Unit = {}
) {

    val bringIntoViewRequester =
        remember { BringIntoViewRequester() }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isListening by remember {
        mutableStateOf(false)
    }

    var showRecorderDialog by remember {
        mutableStateOf(false)
    }

    var isFocused by remember {
        mutableStateOf(false)
    }

    /*
     * Request microphone permission when needed.
     */
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (!isGranted) {
                Log.d(
                    "MIC_PERMISSION",
                    "Microphone permission denied"
                )
            }
        }

    /*
     * Clean up speech recognition when this field leaves
     * the Compose hierarchy.
     */
    DisposableEffect(Unit) {

        onDispose {

            speechManager.stopSpeechRecognition()

            /*
             * If the composable disappears while a recording
             * session is still active, cancel that session.
             *
             * We do not directly call:
             *
             * setMode(FORM_FILLING)
             *
             * because the recording manager may still be waiting
             * for its final hand or voice result.
             */
            if (
                DistressScoringManager.mode.value ==
                DistressMode.VOICE_RECORDING
            ) {
                DistressScoringManager
                    .cancelVoiceRecordingSession()
            }
        }
    }

    /*
     * While the field is focused, check once per second
     * whether the user has become idle.
     */
    LaunchedEffect(isFocused) {

        while (isFocused) {

            delay(1000)

            FormBehaviorTrackingController
                .checkCurrentFieldIdle(fieldId)
        }
    }

    Column {

        OutlinedTextField(
            value = value,

            onValueChange = { newValue ->

                FormBehaviorTrackingController
                    .onFieldValueChanged(
                        fieldId = fieldId,
                        oldValue = value,
                        newValue = newValue
                    )

                onValueChange(newValue)
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

                    isFocused =
                        focusState.isFocused

                    if (focusState.isFocused) {

                        /*
                         * Do not interrupt a voice-recording
                         * session that is still waiting for its
                         * final hand and voice results.
                         *
                         * During ordinary field interaction,
                         * keep the manager in FORM_FILLING mode.
                         */
                        if (
                            DistressScoringManager.mode.value !=
                            DistressMode.VOICE_RECORDING
                        ) {
                            DistressScoringManager.setMode(
                                DistressMode.FORM_FILLING
                            )
                        }

                        /*
                         * Send the selected field ID to the
                         * parent screen.
                         */
                        onFocusedFieldChange(fieldId)

                        FormBehaviorTrackingController
                            .onFieldFocused(
                                fieldId = fieldId,
                                currentValue = value
                            )

                        scope.launch {

                            delay(300)

                            bringIntoViewRequester
                                .bringIntoView()
                        }

                    } else {

                        FormBehaviorTrackingController
                            .onFieldUnfocused(fieldId)
                    }
                },

            minLines = 1,
            maxLines = maxLines,

            trailingIcon = {

                Row {

                    /*
                     * Text-to-speech button.
                     */
                    IconButton(
                        onClick = {

                            val textToRead =
                                if (value.isBlank()) {
                                    "נא למלא $label"
                                } else {
                                    "$label, $value"
                                }

                            ttsManager.speak(textToRead)
                        }
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.VolumeUp,
                            contentDescription = "השמעה",
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                        )
                    }

                    /*
                     * Speech-to-text recording button.
                     */
                    IconButton(
                        enabled = !isListening,

                        onClick = {

                            val hasPermission =
                                ContextCompat
                                    .checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) ==
                                        PackageManager
                                            .PERMISSION_GRANTED

                            if (!hasPermission) {

                                permissionLauncher.launch(
                                    Manifest.permission
                                        .RECORD_AUDIO
                                )

                                return@IconButton
                            }

                            isListening = true
                            showRecorderDialog = true

                            /*
                             * Stop the normal field-idle loop while
                             * the user is using voice recording.
                             */
                            isFocused = false

                            /*
                             * CHANGE A:
                             *
                             * Start a completely new recording
                             * distress session.
                             *
                             * This replaces:
                             *
                             * setMode(VOICE_RECORDING)
                             *
                             * beginVoiceRecordingSession():
                             *
                             * 1. Clears old recording values.
                             * 2. Changes the mode to VOICE_RECORDING.
                             * 3. Tells MotionTrackingController to
                             *    begin collecting hand windows.
                             */
                            DistressScoringManager
                                .beginVoiceRecordingSession()

                            speechManager
                                .startSpeechRecognition(

                                    /*
                                     * Receives recognized speech
                                     * and writes it into the field.
                                     */
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

                                        /*
                                         * Starts the 10-second reliability timer when Android
                                         * detects actual speech.
                                         */
                                        speechManager.markReliableSpeechStart()
                                    },

                                    onAnalysisResult = { analysis ->

                                        Log.d(
                                            "STT_ANALYSIS",
                                            analysis.toString()
                                        )

                                        Log.d(
                                            "STT_RELIABILITY",
                                            "duration=${analysis.durationSeconds}, " +
                                                    "isReliable=${analysis.isReliable}, " +
                                                    "text=${analysis.finalText}"
                                        )

                                        /*
                                         * Do not use a recording that:
                                         *
                                         * 1. Contains less than 10 seconds of detected speech, or
                                         * 2. Does not contain recognized words.
                                         */
                                        if (!analysis.isReliable) {

                                            Log.d(
                                                "VOICE_ANALYSIS",
                                                "Recording excluded from distress analysis. " +
                                                        "duration=${analysis.durationSeconds}, " +
                                                        "textBlank=${analysis.finalText.isBlank()}"
                                            )

                                            /*
                                             * The recording may still be used to fill the text field,
                                             * but it is not used for distress analysis.
                                             */
                                            DistressScoringManager
                                                .cancelVoiceRecordingSession()

                                            return@startSpeechRecognition
                                        }

                                        /*
                                         * Only a reliable recording continues to voice scoring.
                                         */
                                        val currentSpeechRate =
                                            analysis
                                                .speechRateWordsPerSecond

                                        val userId =
                                            FirebaseAuth
                                                .getInstance()
                                                .currentUser
                                                ?.uid

                                        /*
                                         * Without an authenticated
                                         * user, the saved baseline
                                         * cannot be loaded.
                                         *
                                         * Cancel the session before
                                         * leaving this callback.
                                         */
                                        if (userId == null) {

                                            Log.e(
                                                "VOICE_ANALYSIS",
                                                "Cannot analyze voice because no user is signed in."
                                            )

                                            DistressScoringManager
                                                .cancelVoiceRecordingSession()

                                            return@startSpeechRecognition
                                        }

                                        FirebaseFirestore
                                            .getInstance()
                                            .collection("users")
                                            .document(userId)
                                            .collection(
                                                "voiceParameters"
                                            )
                                            .document("baseline")
                                            .get()
                                            .addOnSuccessListener {
                                                    document ->

                                                /*
                                                 * The recording cannot
                                                 * be compared if the
                                                 * baseline document does
                                                 * not exist.
                                                 */
                                                if (!document.exists()) {

                                                    Log.e(
                                                        "VOICE_ANALYSIS",
                                                        "Voice baseline document does not exist."
                                                    )

                                                    DistressScoringManager
                                                        .cancelVoiceRecordingSession()

                                                    return@addOnSuccessListener
                                                }

                                                val baselineSpeechRate =
                                                    document.getDouble(
                                                        "speechRateWordsPerSecond"
                                                    )

                                                val baselineRmsVariation =
                                                    document.getDouble(
                                                        "rmsVariation"
                                                    )

                                                /*
                                                 * Protect against an
                                                 * incomplete baseline
                                                 * document.
                                                 */
                                                if (
                                                    baselineSpeechRate ==
                                                    null ||
                                                    baselineRmsVariation ==
                                                    null
                                                ) {

                                                    Log.e(
                                                        "VOICE_ANALYSIS",
                                                        "Voice baseline is missing required values."
                                                    )

                                                    DistressScoringManager
                                                        .cancelVoiceRecordingSession()

                                                    return@addOnSuccessListener
                                                }

                                                val speechRateScore =
                                                    SpeechRateScorer
                                                        .calculateVoiceScore(
                                                            baselineSpeechRate =
                                                                baselineSpeechRate,
                                                            currentSpeechRate =
                                                                currentSpeechRate
                                                        )

                                                val rmsScore =
                                                    VoiceRmsScorer
                                                        .calculateScore(
                                                            baselineVariation =
                                                                baselineRmsVariation,
                                                            currentVariation =
                                                                analysis
                                                                    .rmsVariation
                                                                    .toDouble()
                                                        )

                                                /*
                                                 * SpeechRateScorer:
                                                 *
                                                 * 0 → normal
                                                 * 1 → moderate deviation
                                                 * 2 → high deviation
                                                 *
                                                 * VoiceRmsScorer:
                                                 *
                                                 * 0 → close to baseline
                                                 * 1 → at least 1.5 × baseline
                                                 * 2 → at least 2.0 × baseline
                                                 *
                                                 * Combined voice score:
                                                 * 0–4
                                                 */
                                                val voiceScore =
                                                    (
                                                            speechRateScore +
                                                                    rmsScore
                                                            ).coerceIn(0, 4)

                                                /*
                                                 * CHANGE B:
                                                 *
                                                 * Submit one final voice
                                                 * score for this complete
                                                 * recording.
                                                 *
                                                 * Do not call:
                                                 *
                                                 * updateVoiceScore()
                                                 *
                                                 * The manager will wait
                                                 * until it also receives
                                                 * the final hand average
                                                 * from MotionTrackingController.
                                                 */
                                                DistressScoringManager
                                                    .submitVoiceRecordingVoiceScore(
                                                        score =
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
                                            .addOnFailureListener {
                                                    error ->

                                                Log.e(
                                                    "VOICE_ANALYSIS",
                                                    "Failed to get baseline",
                                                    error
                                                )

                                                /*
                                                 * Firestore failed, so a
                                                 * complete recording result
                                                 * cannot be produced.
                                                 */
                                                DistressScoringManager
                                                    .cancelVoiceRecordingSession()
                                            }
                                    },
                                    onFailure = {
                                        DistressScoringManager
                                            .cancelVoiceRecordingSession()
                                    },
                                    /*
                                     * CHANGE C:
                                     *
                                     * The audio-recording part has
                                     * finished.
                                     *
                                     * MotionTrackingController must now:
                                     *
                                     * 1. Stop collecting hand windows.
                                     * 2. Calculate their average.
                                     * 3. Submit the average to
                                     *    DistressScoringManager.
                                     *
                                     * Do not change the mode to
                                     * FORM_FILLING here.
                                     *
                                     * The manager returns to
                                     * FORM_FILLING only after both
                                     * values are available:
                                     *
                                     * - final voice score
                                     * - final hand average
                                     */
                                    onFinished = {

                                        isListening = false
                                        showRecorderDialog = false

                                        /*
                                         * If reliability checking or another error already cancelled
                                         * the session, do not emit a second recording-stop event.
                                         */
                                        if (
                                            DistressScoringManager.mode.value ==
                                            DistressMode.VOICE_RECORDING
                                        ) {
                                            DistressScoringManager
                                                .requestVoiceRecordingStop()
                                        }
                                    }
                                )
                        }
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Mic,
                            contentDescription =
                                "הקלטה",
                            tint =
                                if (isListening) {
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                }
                        )
                    }
                }
            },

            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,

                    unfocusedTextColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,

                    focusedLabelColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,

                    unfocusedLabelColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,

                    cursorColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,

                    focusedBorderColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,

                    unfocusedBorderColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface,

                    focusedContainerColor =
                        MaterialTheme
                            .colorScheme
                            .surface,

                    unfocusedContainerColor =
                        MaterialTheme
                            .colorScheme
                            .surface
                )
        )

        /*
         * Recording dialog.
         */
        if (showRecorderDialog) {

            AlertDialog(
                onDismissRequest = {},

                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface,

                titleContentColor =
                    MaterialTheme
                        .colorScheme
                        .onSurface,

                textContentColor =
                    MaterialTheme
                        .colorScheme
                        .onSurface,

                title = {
                    Text("מקליט ...")
                },

                text = {

                    Column(
                        horizontalAlignment =
                            androidx.compose.ui
                                .Alignment
                                .CenterHorizontally,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        val infiniteTransition =
                            rememberInfiniteTransition(
                                label = "pulse"
                            )

                        val scale by
                        infiniteTransition
                            .animateFloat(
                                initialValue = 1f,
                                targetValue = 1.25f,

                                animationSpec =
                                    infiniteRepeatable(
                                        animation =
                                            tween(700),

                                        repeatMode =
                                            RepeatMode
                                                .Reverse
                                    ),

                                label = "micScale"
                            )

                        Icon(
                            imageVector =
                                Icons.Default.Mic,

                            contentDescription =
                                "recording",

                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,

                            modifier =
                                Modifier
                                    .size(90.dp)
                                    .scale(scale)
                        )

                        Spacer(
                            modifier =
                                Modifier.height(16.dp)
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

                            /*
                             * SpeechToTextManager will eventually
                             * call onFinished(), and onFinished()
                             * will call requestVoiceRecordingStop().
                             */
                            speechManager.stopAndAnalyze()
                        },

                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                )
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Stop,
                            contentDescription =
                                "stop"
                        )

                        Text("עצירה")
                    }
                }
            )
        }
    }
}