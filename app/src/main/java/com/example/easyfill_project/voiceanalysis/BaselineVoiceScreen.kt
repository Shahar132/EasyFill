package com.example.easyfill_project.voiceanalysis

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.easyfill_project.speechtotext.SpeechToTextManager

import androidx.compose.material.icons.filled.Timer
import kotlinx.coroutines.delay

@Composable
fun BaselineVoiceScreen(
    speechManager: SpeechToTextManager,
    onBaselineFinished: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }



    // Saves baseline data to Firebase
    val baselineRepository = remember {
        VoiceBaselineRepository()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.d("BASELINE", "Microphone permission denied")
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "micAnimation")

    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopSpeechRecognition()
        }
    }


    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0

            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "היכרות קולית קצרה",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = """
                    כדי שנוכל להבין טוב יותר את קצב הדיבור שלך,
                    נבקש ממך לדבר באופן חופשי במשך לפחות 15 שניות.

                    אפשר לספר על עצמך, על תחביבים, דברים שאת/ה אוהב/ת לעשות בזמן הפנוי,
                    או כל דבר כללי שנוח לך לדבר עליו.

                    ההקלטה הזו תשמש כבסיס להשוואה בהמשך, בזמן מילוי הטפסים.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .scale(micScale),
                shape = CircleShape,
                color = if (isRecording)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp
            ) {
                IconButton(
                    modifier = Modifier.fillMaxSize(),
                    enabled = !isSaving,
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            speechManager.context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@IconButton
                        }

                        if (!isRecording) {
                            // Start baseline recording
                            isRecording = true
                            recordingSeconds = 0

                            speechManager.startSpeechRecognition(
                                // We do not show transcript to the user
                                onResult = {},

                                // When analysis is ready, save it to Firestore
                                onAnalysisResult = { analysis ->
                                    Log.d("BASELINE_ANALYSIS", analysis.toString())

                                    //check if is reliable - more tan 15 seconds speaking
                                    if (!analysis.isReliable) {

                                        Toast.makeText(
                                            speechManager.context,
                                            "יש לדבר לפחות 15 שניות כדי ליצור פרופיל קולי אמין",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        Log.d(
                                            "BASELINE",
                                            "Recording too short: ${analysis.durationSeconds}"
                                        )

                                        isSaving = false
                                        isRecording = false
                                    } else {
                                        isSaving = true

                                        baselineRepository.saveBaseline(
                                            analysis = analysis,
                                            onSuccess = {
                                                isSaving = false
                                                showSuccessDialog = true
                                            },
                                            onFailure = { error ->
                                                Log.e("BASELINE", "Failed to save baseline", error)
                                                isSaving = false
                                            }
                                        )
                                    }
                                },

                                // STT finished, but navigation happens only after Firebase save succeeds
                                onFinished = {
                                    isRecording = false
                                }
                            )
                        } else {
                            // Stop recording and trigger analysis
                            speechManager.stopAndAnalyze()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "record baseline",
                        tint = if (isRecording)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(70.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = when {
                    isSaving -> "שומר את הנתונים..."
                    isRecording -> "מקליט עכשיו... לחץ/י שוב לעצירה"
                    else -> "לחץ/י על המיקרופון להתחלה"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (isRecording) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "timer",
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$recordingSeconds שניות",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (recordingSeconds >= 15)
                        "אפשר לעצור את ההקלטה"
                    else
                        "יש לדבר לפחות 15 שניות",
                    color = if (recordingSeconds >= 15)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }

            if (isSaving) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = {},
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurface,
                    title = {
                        Text("הפרופיל הקולי נוצר בהצלחה")
                    },
                    text = {
                        Text("תודה! יצרנו עבורך פרופיל קולי בסיסי. עכשיו נמשיך לשלב העלאת הקובץ.")
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