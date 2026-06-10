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
fun IncomeDetailsSection(
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
            text = "פירוט הכנסות",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        SmartTextField(
            label = "מקום העבודה",
            value = formData["workPlace"].orEmpty(),
            onValueChange = { onFieldChange("workPlace", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "השכר שלך נטו",
            value = formData["salaryNet"].orEmpty(),
            onValueChange = { onFieldChange("salaryNet", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מקום העבודה של בן/בת הזוג",
            value = formData["partnerWorkPlace"].orEmpty(),
            onValueChange = { onFieldChange("partnerWorkPlace", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "שכר בן/בת הזוג נטו",
            value = formData["partnerSalaryNet"].orEmpty(),
            onValueChange = { onFieldChange("partnerSalaryNet", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "פירוט הכנסות נוספות",
            value = formData["additionalIncomeDetails"].orEmpty(),
            onValueChange = { onFieldChange("additionalIncomeDetails", it) },
            ttsManager = ttsManager,
            speechManager = speechManager,
            maxLines = 6
        )
    }
}