package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.components.SmartTextField
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager

@Composable
fun MailingAddressSection(
    formData: Map<String, String>,
    onFieldChange: (String, String) -> Unit
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
            text = "כתובת למשלוח דואר",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        SmartTextField(
            label = "רחוב/תא דואר",
            value = formData["mailingStreet"].orEmpty(),
            onValueChange = { onFieldChange("mailingStreet", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר בית",
            value = formData["mailingHouseNumber"].orEmpty(),
            onValueChange = { onFieldChange("mailingHouseNumber", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "כניסה",
            value = formData["mailingEntrance"].orEmpty(),
            onValueChange = { onFieldChange("mailingEntrance", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "דירה",
            value = formData["mailingApartment"].orEmpty(),
            onValueChange = { onFieldChange("mailingApartment", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "ישוב",
            value = formData["mailingCity"].orEmpty(),
            onValueChange = { onFieldChange("mailingCity", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מיקוד",
            value = formData["mailingZipCode"].orEmpty(),
            onValueChange = { onFieldChange("mailingZipCode", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )
    }
}