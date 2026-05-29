package com.example.easyfill_project.forms_screens.housing_assistance_sections

import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.texttospeech.TextToSpeechManager


import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.forms_screens.components.SmartTextField



@Composable
fun PersonalDetailsSection(
    autofill: Map<String, String?> = emptyMap()
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val speechManager = remember { SpeechToTextManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "פרטים אישיים",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        SmartTextField(
            label = "שם משפחה",
            valueFromAzure = autofill["lastName"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "שם פרטי",
            valueFromAzure = autofill["firstName"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר תעודת זהות",
            valueFromAzure = autofill["idNumber"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "רחוב",
            valueFromAzure = autofill["street"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר בית",
            valueFromAzure = autofill["houseNumber"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "יישוב",
            valueFromAzure = autofill["city"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מיקוד",
            valueFromAzure = autofill["zipCode"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "טלפון נייד",
            valueFromAzure = autofill["phone"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "דואר אלקטרוני",
            valueFromAzure = autofill["email"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )
    }
}
