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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf

// Provides form-level validation messages to every SmartTextField.
internal val LocalFormValidationMessages =
    staticCompositionLocalOf<Map<String, String>> {
        emptyMap()
    }


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
    externalValidationMessage: String? = null,

    // Reports which field the user most recently selected.
    onFocusedFieldChange: (String) -> Unit = {}
) {

    val bringIntoViewRequester =
        remember { BringIntoViewRequester() }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    /*
 * Observe the global distress mode.
 *
 * A new recording must not begin while the previous
 * recording is still waiting for voice, face, or hand
 * processing to finish.
 */
    val currentDistressMode by
    DistressScoringManager
        .mode
        .collectAsState()

    var isListening by remember {
        mutableStateOf(false)
    }

    var showRecorderDialog by remember {
        mutableStateOf(false)
    }

    var isFocused by remember {
        mutableStateOf(false)
    }

    // Prevents validation errors from appearing before the field is visited.
    var hasReceivedFocus by remember(fieldId) {
        mutableStateOf(false)
    }

    // Controls when the validation error should be displayed.
    var shouldShowValidationError by remember(fieldId) {
        mutableStateOf(false)
    }

    // Validates the current value using the rule assigned to this field ID.
    val validationError = if (shouldShowValidationError) {
        FieldInputRules.validate(
            fieldId = fieldId,
            value = value
        )
    } else {
        null
    }

    // Converts the validation result into a message shown to the user.
    val validationMessage = validationError?.let { error ->
        FieldValidationMessages.getMessage(error)
    }

    // Reads a validation message produced by the full-form validator.
    val formValidationMessage =
        LocalFormValidationMessages.current[fieldId]

    // Form-level issues remain visible until their values become valid.
    // Regular field validation appears only after the user leaves the field.
    val displayedValidationMessage =
        formValidationMessage
            ?: if (shouldShowValidationError) {
                validationMessage ?: externalValidationMessage
            } else {
                null
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

            /*
             * If the user leaves this field while a voice-recording
             * session is still active, cancel the recording.
             */
            if (
                DistressScoringManager.mode.value ==
                DistressMode.VOICE_RECORDING
            ) {
                DistressScoringManager
                    .cancelVoiceRecordingSession()
            }

            /*
             * Release speech-recognition resources owned by this field.
             */
            speechManager.stopSpeechRecognition()
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

            onValueChange = { typedValue ->

                // Applies the input restrictions defined for this field ID.
                val sanitizedValue =
                    FieldInputRules.sanitizeTypedInput(
                        fieldId = fieldId,
                        input = typedValue
                    )

                if (sanitizedValue != value) {
                    FormBehaviorTrackingController
                        .onFieldValueChanged(
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

                    isFocused =
                        focusState.isFocused

                    if (focusState.isFocused) {

                        hasReceivedFocus = true

                        // Hides the current error while the user edits the value.
                        shouldShowValidationError = false

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

                        // Shows validation only after the user has left the field.
                        if (hasReceivedFocus) {
                            shouldShowValidationError = true
                        }

                        FormBehaviorTrackingController
                            .onFieldUnfocused(fieldId)
                    }
                },

            minLines = 1,
            maxLines = maxLines,

            // Marks the text field as invalid when a validation message exists.
            isError = displayedValidationMessage != null,

            // Displays the validation message below the field.
            supportingText =
                if (displayedValidationMessage != null) {
                    {
                        Text(displayedValidationMessage,
                        color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    null
                },

            // Uses the keyboard type assigned to this field in FieldInputRules.
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
                            // Change this line to make it unique for each field:
                            contentDescription = "השמעה עבור $label",
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
                        enabled =
                            !isListening &&
                                    currentDistressMode ==
                                    DistressMode.FORM_FILLING,

                        onClick = {
                            /*
                             * Defensive protection:
                             * do not start a new recording while the previous
                             * recording result is still being processed.
                             */
                            if (
                                DistressScoringManager.mode.value ==
                                DistressMode.VOICE_RECORDING
                            ) {
                                Log.d(
                                    "VOICE_RECORDING_SESSION",
                                    "New recording ignored because the previous recording is still being processed."
                                )

                                return@IconButton
                            }

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
                                        // Applies the same field rules to recognized speech.
                                        val sanitizedValue =
                                            FieldInputRules.sanitizeTypedInput(
                                                fieldId = fieldId,
                                                input = text
                                            )

                                        // Ignores speech that contains no valid value for this field.
                                        if (
                                            sanitizedValue.isNotBlank() &&
                                            sanitizedValue != value
                                        ) {
                                            FormBehaviorTrackingController
                                                .onFieldValueChanged(
                                                    fieldId = fieldId,
                                                    oldValue = value,
                                                    newValue = sanitizedValue
                                                )

                                            onValueChange(sanitizedValue)
                                        }
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
                                                "Voice modality unavailable for this recording. " +
                                                        "duration=${analysis.durationSeconds}, " +
                                                        "textBlank=${analysis.finalText.isBlank()}"
                                            )

                                            /*
                                             * Voice processing completed, but the recording was not
                                             * reliable enough to produce a voice score.
                                             *
                                             * Submit null instead of cancelling the whole multimodal
                                             * recording.
                                             *
                                             * Face and hand may still participate in the final score.
                                             */
                                            DistressScoringManager
                                                .submitVoiceRecordingVoiceScore(
                                                    score = null
                                                )

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
                                                "Voice modality unavailable because no user is signed in."
                                            )

                                            /*
                                             * Voice cannot be compared with a personal baseline,
                                             * but face and hand may still be available.
                                             */
                                            DistressScoringManager
                                                .submitVoiceRecordingVoiceScore(
                                                    score = null
                                                )

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
                                                        "Voice modality unavailable because the baseline document does not exist."
                                                    )

                                                    /*
                                                     * The voice component completed without a usable score.
                                                     *
                                                     * Keep the multimodal session active for face and hand.
                                                     */
                                                    DistressScoringManager
                                                        .submitVoiceRecordingVoiceScore(
                                                            score = null
                                                        )

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
                                                    baselineSpeechRate == null ||
                                                    baselineRmsVariation == null
                                                ) {

                                                    Log.e(
                                                        "VOICE_ANALYSIS",
                                                        "Voice modality unavailable because the baseline is missing required values."
                                                    )

                                                    DistressScoringManager
                                                        .submitVoiceRecordingVoiceScore(
                                                            score = null
                                                        )

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
                                            .addOnFailureListener { error ->

                                                Log.e(
                                                    "VOICE_ANALYSIS",
                                                    "Failed to load voice baseline. Voice modality will be unavailable.",
                                                    error
                                                )

                                                /*
                                                 * Firestore failure prevents voice scoring only.
                                                 *
                                                 * It does not prevent a multimodal result based on
                                                 * the available face and hand analyses.
                                                 */
                                                DistressScoringManager
                                                    .submitVoiceRecordingVoiceScore(
                                                        score = null
                                                    )
                                            }
                                    },
                                    onFailure = {

                                        Log.e(
                                            "VOICE_ANALYSIS",
                                            "Speech recognition failed. Voice modality will be unavailable."
                                        )

                                        /*
                                         * Mark voice processing as completed but unavailable.
                                         *
                                         * onFinished will still request the recording-stop event,
                                         * allowing hand and face to submit their final results.
                                         */
                                        DistressScoringManager
                                            .submitVoiceRecordingVoiceScore(
                                                score = null
                                            )
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
                            // Change this line to make it unique for each field:
                            contentDescription = "הקלטה עבור $label",
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

            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor =
                    MaterialTheme.colorScheme.onSurface,

                unfocusedTextColor =
                    MaterialTheme.colorScheme.onSurface,

                errorTextColor =
                    MaterialTheme.colorScheme.onSurface,

                focusedLabelColor =
                    MaterialTheme.colorScheme.onSurface,

                unfocusedLabelColor =
                    MaterialTheme.colorScheme.onSurface,

                errorLabelColor =
                    MaterialTheme.colorScheme.error,

                cursorColor =
                    MaterialTheme.colorScheme.onSurface,

                errorCursorColor =
                    MaterialTheme.colorScheme.error,

                focusedBorderColor =
                    MaterialTheme.colorScheme.onSurface,

                unfocusedBorderColor =
                    MaterialTheme.colorScheme.onSurface,

                // Controls the border whenever isError is true.
                errorBorderColor =
                    MaterialTheme.colorScheme.error,

                focusedContainerColor =
                    MaterialTheme.colorScheme.surface,

                unfocusedContainerColor =
                    MaterialTheme.colorScheme.surface,

                errorContainerColor =
                    MaterialTheme.colorScheme.surface,

                errorSupportingTextColor =
                    MaterialTheme.colorScheme.error
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