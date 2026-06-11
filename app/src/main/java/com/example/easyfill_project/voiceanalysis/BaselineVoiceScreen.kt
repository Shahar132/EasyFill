package com.example.easyfill_project.voiceanalysis

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun BaselineVoiceScreen(
    speechManager: SpeechToTextManager,
    onBaselineFinished: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
                    כדי שנוכל להבין טוב יותר את קצב הדיבור הרגיל שלך, 
                    נבקש ממך לדבר באופן חופשי במשך 20 עד 30 שניות.

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
                            isRecording = true
                            transcript = ""

                            speechManager.startSpeechRecognition(
                                onResult = { text ->
                                    transcript = text
                                },
                                onAnalysisResult = { analysis ->
                                    Log.d("BASELINE_ANALYSIS", analysis.toString())
                                },
                                onFinished = {
                                    isRecording = false
                                    onBaselineFinished()
                                }
                            )
                        } else {
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
                text = if (isRecording) "מקליט עכשיו... לחץ/י שוב לעצירה" else "לחץ/י על המיקרופון להתחלה",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (transcript.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = transcript,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}