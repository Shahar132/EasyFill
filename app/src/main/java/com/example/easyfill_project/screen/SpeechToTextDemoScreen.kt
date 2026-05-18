package com.example.easyfill_project.screen

import android.Manifest
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.speechtotext.SpeechToTextManager

@Composable
fun SpeechToTextDemoScreen() {
    var text by remember { mutableStateOf("") }

    val context = LocalContext.current
    val sttManager = remember { SpeechToTextManager(context) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()

        if (spokenText.isNullOrBlank()) {
            Toast.makeText(
                context,
                "לא הצלחנו להבין. נסו לדבר ברור ובמקום שקט",
                Toast.LENGTH_LONG
            ).show()
        } else {
            text = spokenText
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sttManager.startSpeechRecognition(speechLauncher)
        } else {
            Toast.makeText(
                context,
                "יש לאשר גישה למיקרופון כדי להשתמש בזיהוי דיבור",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "מילוי שדה באמצעות קול",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("הקלידו או דברו") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "מילוי בקול"
                    )
                }
            }
        )
    }
}