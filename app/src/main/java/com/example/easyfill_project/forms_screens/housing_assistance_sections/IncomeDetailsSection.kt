package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.components.SmartTextField
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager

@Composable
fun IncomeDetailsSection(
    // Contains all current values saved in the form.
    formData: Map<String, String>,

    // Sends a changed value back to HousingAssistanceFormScreen.
    onFieldChange: (String, String) -> Unit,

    // Reports which SmartTextField the user most recently selected.
    // The field ID is passed upward until AppNavigation stores it.
    onFocusedFieldChange: (String) -> Unit
) {
    // Android context is required for TTS and speech-to-text.
    val context = LocalContext.current

    // Used by the speaker icon inside every SmartTextField.
    val ttsManager = remember {
        TextToSpeechManager(context)
    }

    // Used by the microphone icon inside every SmartTextField.
    val speechManager = remember {
        SpeechToTextManager(context)
    }

    // Releases TTS resources when this section is removed from the screen.
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
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
            // Matches the formData and database key.
            fieldId = "workPlace",
            label = "מקום העבודה",
            value = formData["workPlace"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("workPlace", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,

            // Sends "workPlace" when this field receives focus.
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            // Changed from "incomeSalary" so it matches
            // formData["salaryNet"] and the Azure/database key.
            fieldId = "salaryNet",
            label = "השכר שלך נטו",
            value = formData["salaryNet"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("salaryNet", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            // Changed from "partnerWork" so it matches
            // formData["partnerWorkPlace"].
            fieldId = "partnerWorkPlace",
            label = "מקום העבודה של בן/בת הזוג",
            value = formData["partnerWorkPlace"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("partnerWorkPlace", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            // Changed from "partnerIncomeSalary" so it matches
            // formData["partnerSalaryNet"].
            fieldId = "partnerSalaryNet",
            label = "שכר בן/בת הזוג נטו",
            value = formData["partnerSalaryNet"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("partnerSalaryNet", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            // Changed from "additionalIncome" so it matches
            // formData["additionalIncomeDetails"].
            fieldId = "additionalIncomeDetails",
            label = "פירוט הכנסות נוספות",
            value = formData["additionalIncomeDetails"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("additionalIncomeDetails", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,

            // Allows a larger multi-line answer.
            maxLines = 6,

            onFocusedFieldChange = onFocusedFieldChange
        )
    }
}