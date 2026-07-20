package com.example.easyfill_project.voiceanalysis

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import kotlinx.coroutines.delay

@Composable
fun BaselineVoiceScreen(
    speechManager: SpeechToTextManager,
    onBaselineFinished: () -> Unit
) {
    val context = LocalContext.current

    /*
     * This value comes directly from SpeechAudioAnalyzer.
     *
     * Therefore, when you change the analyzer minimum from
     * 15 seconds to 10 seconds, this screen changes automatically.
     */
    val minimumRecordingSeconds =
        SpeechAudioAnalyzer
            .MIN_RELIABLE_DURATION_SECONDS
            .toInt()

    var isRecording by remember {
        mutableStateOf(false)
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    var showSuccessDialog by remember {
        mutableStateOf(false)
    }

    var recordingSeconds by remember {
        mutableIntStateOf(0)
    }

    var speechDetected by remember {
        mutableStateOf(false)
    }

    var canStopRecording by remember {
        mutableStateOf(false)
    }

    var noSpeechToastShown by remember {
        mutableStateOf(false)
    }

    val baselineRepository =
        remember {
            VoiceBaselineRepository()
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (!granted) {
                Log.d(
                    "BASELINE",
                    "Microphone permission denied"
                )

                Toast.makeText(
                    context,
                    "נדרשת הרשאה למיקרופון לצורך יצירת הפרופיל הקולי",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    val infiniteTransition =
        rememberInfiniteTransition(
            label = "micAnimation"
        )

    val micScale by
    infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue =
            if (isRecording) {
                1.25f
            } else {
                1f
            },
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(700),
                repeatMode =
                    RepeatMode.Reverse
            ),
        label = "micScale"
    )

    /*
     * Stop speech recognition if this screen leaves
     * the Compose hierarchy.
     */
    DisposableEffect(Unit) {

        onDispose {
            speechManager.stopSpeechRecognition()
        }
    }

    /*
     * Notify the user if Android has not detected speech
     * after five seconds.
     */
    LaunchedEffect(
        isRecording,
        speechDetected
    ) {
        if (
            isRecording &&
            !speechDetected &&
            !noSpeechToastShown
        ) {
            delay(5000)

            if (
                isRecording &&
                !speechDetected
            ) {
                noSpeechToastShown = true

                Toast.makeText(
                    context,
                    "לא זוהה דיבור. דבר/י בקול ברור ליד המיקרופון.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /*
     * Count the number of seconds from the moment Android
     * detects actual speech.
     */
    LaunchedEffect(
        isRecording,
        speechDetected
    ) {
        if (
            isRecording &&
            speechDetected
        ) {
            recordingSeconds = 0
            canStopRecording = false

            while (
                isRecording &&
                speechDetected
            ) {
                delay(1000)

                recordingSeconds++

                if (
                    recordingSeconds >=
                    minimumRecordingSeconds
                ) {
                    canStopRecording = true
                }
            }
        }
    }

    Surface(
        modifier =
            Modifier.fillMaxSize(),
        color =
            MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {
            Text(
                text = "היכרות קולית קצרה",
                style =
                    MaterialTheme.typography
                        .headlineMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurface,
                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text =
                    """
                    כדי שנוכל להבין טוב יותר את קצב הדיבור שלך,
                    נבקש ממך לדבר באופן חופשי במשך לפחות $minimumRecordingSeconds שניות.

                    אפשר לספר על עצמך, על תחביבים, דברים שאת/ה אוהב/ת לעשות בזמן הפנוי,
                    או כל דבר כללי שנוח לך לדבר עליו.
                    """.trimIndent(),
                style =
                    MaterialTheme.typography
                        .bodyLarge,
                color =
                    MaterialTheme.colorScheme
                        .onSurface,
                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(32.dp)
            )

            Surface(
                modifier =
                    Modifier
                        .size(140.dp)
                        .scale(micScale),
                shape =
                    CircleShape,
                color =
                    if (isRecording) {
                        MaterialTheme.colorScheme
                            .secondary
                    } else {
                        MaterialTheme.colorScheme
                            .primary
                    },
                shadowElevation =
                    8.dp
            ) {
                IconButton(
                    modifier =
                        Modifier.fillMaxSize(),
                    enabled =
                        !isSaving,
                    onClick = {

                        val hasPermission =
                            ContextCompat
                                .checkSelfPermission(
                                    context,
                                    Manifest.permission
                                        .RECORD_AUDIO
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

                        if (!isRecording) {

                            /*
                             * Start a new baseline recording.
                             */
                            isRecording = true
                            isSaving = false
                            speechDetected = false
                            canStopRecording = false
                            recordingSeconds = 0
                            noSpeechToastShown = false

                            speechManager
                                .startSpeechRecognition(

                                    /*
                                     * The transcript is not displayed,
                                     * but SpeechAudioAnalyzer receives
                                     * the recognized text internally.
                                     */
                                    onResult = { text ->

                                        Log.d(
                                            "BASELINE_TEXT",
                                            "Recognized text: $text"
                                        )
                                    },

                                    /*
                                     * Android detected actual speech.
                                     *
                                     * Start both:
                                     *
                                     * 1. The analyzer duration.
                                     * 2. The visible UI timer.
                                     */
                                    onSpeechStarted = {

                                        Log.d(
                                            "BASELINE",
                                            "Speech detected"
                                        )

                                        if (!speechDetected) {
                                            speechDetected = true
                                            recordingSeconds = 0
                                            canStopRecording = false

                                            speechManager
                                                .markReliableSpeechStart()
                                        }
                                    },

                                    /*
                                     * The completed analysis is received
                                     * after the user stops recording.
                                     */
                                    onAnalysisResult =
                                        analysisCallback@{ analysis ->

                                            Log.d(
                                                "BASELINE_ANALYSIS",
                                                analysis.toString()
                                            )

                                            /*
                                             * The baseline must meet the
                                             * same reliability requirement
                                             * as normal voice recordings.
                                             */
                                            if (!analysis.isReliable) {

                                                Log.w(
                                                    "BASELINE",
                                                    "Unreliable analysis: " +
                                                            "duration=${analysis.durationSeconds}, " +
                                                            "textBlank=${analysis.finalText.isBlank()}"
                                                )

                                                isSaving = false
                                                isRecording = false
                                                speechDetected = false
                                                canStopRecording = false
                                                recordingSeconds = 0

                                                Toast.makeText(
                                                    context,
                                                    "ההקלטה לא הייתה מספקת. יש לדבר במשך לפחות $minimumRecordingSeconds שניות בקול ברור.",
                                                    Toast.LENGTH_LONG
                                                ).show()

                                                return@analysisCallback
                                            }

                                            isSaving = true

                                            baselineRepository
                                                .saveBaseline(
                                                    analysis =
                                                        analysis,

                                                    /*
                                                     * Use the analyzer duration,
                                                     * because it is the duration
                                                     * used for reliability and
                                                     * speech-rate calculation.
                                                     */
                                                    validSpeechSeconds =
                                                        analysis
                                                            .durationSeconds
                                                            .toInt(),

                                                    onSuccess = {

                                                        isSaving = false
                                                        showSuccessDialog =
                                                            true
                                                    },

                                                    onFailure = { error ->

                                                        Log.e(
                                                            "BASELINE",
                                                            "Failed to save baseline",
                                                            error
                                                        )

                                                        isSaving = false

                                                        Toast.makeText(
                                                            context,
                                                            "שמירת הפרופיל הקולי נכשלה. נסה/י שוב.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                )
                                        },

                                    onFailure = {

                                        Log.e(
                                            "BASELINE",
                                            "Speech recognition could not start or failed"
                                        )

                                        isSaving = false
                                        isRecording = false
                                        speechDetected = false
                                        canStopRecording = false
                                        recordingSeconds = 0

                                        Toast.makeText(
                                            context,
                                            "זיהוי הדיבור נכשל. אפשר לנסות להקליט מחדש.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },

                                    onFinished = {
                                        isRecording = false
                                        speechDetected = false
                                        canStopRecording = false
                                        recordingSeconds = 0
                                    }
                                )

                        } else {

                            /*
                             * Do not allow a baseline recording to stop
                             * before the required duration.
                             */
                            if (!canStopRecording) {

                                Toast.makeText(
                                    context,
                                    "יש להמשיך לדבר עד להשלמת $minimumRecordingSeconds שניות.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@IconButton
                            }

                            Log.d(
                                "BASELINE_CLICK",
                                "Stopping recording"
                            )

                            /*
                             * Hide the recording state immediately.
                             *
                             * isSaving remains true until the analysis
                             * is saved or fails.
                             */
                            isRecording = false
                            isSaving = true

                            speechManager.stopAndAnalyze()
                        }
                    }
                ) {
                    Icon(
                        imageVector =
                            if (
                                isRecording &&
                                canStopRecording
                            ) {
                                Icons.Default.Stop
                            } else {
                                Icons.Default.Mic
                            },
                        contentDescription =
                            "record baseline",
                        tint =
                            if (isRecording) {
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onPrimary
                            },
                        modifier =
                            Modifier.size(70.dp)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Text(
                text =
                    when {
                        isSaving ->
                            "שומר את הנתונים..."

                        isRecording &&
                                !speechDetected ->
                            "מחכה לזיהוי דיבור... דבר/י בקול ברור ליד המיקרופון"

                        isRecording &&
                                !canStopRecording ->
                            "זוהה דיבור. המשך/י לדבר בקול ברור"

                        isRecording ->
                            "אפשר לעצור את ההקלטה"

                        else ->
                            "לחץ/י על המיקרופון להתחלה"
                    },
                style =
                    MaterialTheme.typography
                        .bodyLarge,
                color =
                    MaterialTheme.colorScheme
                        .onSurface,
                textAlign =
                    TextAlign.Center
            )

            if (
                isRecording &&
                speechDetected
            ) {
                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.Center
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Timer,
                        contentDescription =
                            "timer",
                        tint =
                            MaterialTheme.colorScheme
                                .onSurface
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text(
                        text =
                            "$recordingSeconds שניות",
                        style =
                            MaterialTheme.typography
                                .bodyLarge,
                        color =
                            MaterialTheme.colorScheme
                                .onSurface
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        if (
                            recordingSeconds >=
                            minimumRecordingSeconds
                        ) {
                            "אפשר לעצור את ההקלטה"
                        } else {
                            "יש לדבר לפחות $minimumRecordingSeconds שניות בקול ברור כדי ליצור פרופיל קולי אמין"
                        },
                    color =
                        if (
                            recordingSeconds >=
                            minimumRecordingSeconds
                        ) {
                            MaterialTheme.colorScheme
                                .secondary
                        } else {
                            MaterialTheme.colorScheme
                                .onSurface
                        },
                    style =
                        MaterialTheme.typography
                            .bodyLarge,
                    textAlign =
                        TextAlign.Center
                )
            }

            if (isSaving) {
                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                CircularProgressIndicator()
            }

            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = {},
                    containerColor =
                        MaterialTheme.colorScheme
                            .surface,
                    titleContentColor =
                        MaterialTheme.colorScheme
                            .onSurface,
                    textContentColor =
                        MaterialTheme.colorScheme
                            .onSurface,
                    title = {
                        Text(
                            "הפרופיל הקולי נוצר בהצלחה"
                        )
                    },
                    text = {
                        Text(
                            "תודה! יצרנו עבורך פרופיל קולי בסיסי. עכשיו נמשיך לשלב העלאת הקובץ."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSuccessDialog = false
                                onBaselineFinished()
                            }
                        ) {
                            Text("לחץ/י להמשך")
                        }
                    }
                )
            }
        }
    }
}