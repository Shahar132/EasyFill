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
fun PersonalDetailsSection(
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
            text = "פרטים אישיים",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        SmartTextField(
            fieldId = "lastName",
            label = "שם משפחה",
            value = formData["lastName"].orEmpty(),
            onValueChange = { onFieldChange("lastName", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "firstName",
            label = "שם פרטי",
            value = formData["firstName"].orEmpty(),
            onValueChange = { onFieldChange("firstName", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "idNumber",
            label = "מספר תעודת זהות",
            value = formData["idNumber"].orEmpty(),
            onValueChange = { onFieldChange("idNumber", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "street",
            label = "רחוב",
            value = formData["street"].orEmpty(),
            onValueChange = { onFieldChange("street", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "houseNumber",
            label = "מספר בית",
            value = formData["houseNumber"].orEmpty(),
            onValueChange = { onFieldChange("houseNumber", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "city",
            label = "יישוב",
            value = formData["city"].orEmpty(),
            onValueChange = { onFieldChange("city", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "postcode",
            label = "מיקוד",
            value = formData["zipCode"].orEmpty(),
            onValueChange = { onFieldChange("zipCode", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "phoneNumber",
            label = "טלפון נייד",
            value = formData["phone"].orEmpty(),
            onValueChange = { onFieldChange("phone", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "email",
            label = "דואר אלקטרוני",
            value = formData["email"].orEmpty(),
            onValueChange = { onFieldChange("email", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )
    }
}