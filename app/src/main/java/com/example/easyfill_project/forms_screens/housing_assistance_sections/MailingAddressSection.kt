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
fun MailingAddressSection(
    // Stores all current values of the housing form.
    formData: Map<String, String>,

    // Sends an updated field value back to HousingAssistanceFormScreen.
    onFieldChange: (String, String) -> Unit,

    // Sends the ID of the field that the user selected.
    // Example: "mailingStreet" or "mailingCity".
    onFocusedFieldChange: (String) -> Unit
) {
    // Android context is needed for TTS and speech-to-text.
    val context = LocalContext.current

    // Used by the speaker button inside SmartTextField.
    val ttsManager = remember {
        TextToSpeechManager(context)
    }

    // Used by the microphone button inside SmartTextField.
    val speechManager = remember {
        SpeechToTextManager(context)
    }

    // Releases the TTS resources when this section is removed.
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
            text = "כתובת למשלוח דואר",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        SmartTextField(
            fieldId = "mailingStreet",
            label = "רחוב/תא דואר",
            value = formData["mailingStreet"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("mailingStreet", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,

            // When this field receives focus, SmartTextField sends
            // "mailingStreet" through this callback.
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "mailingHouseNumber",
            label = "מספר בית",
            value = formData["mailingHouseNumber"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("mailingHouseNumber", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "mailingEntrance",
            label = "כניסה",
            value = formData["mailingEntrance"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("mailingEntrance", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "mailingApartment",
            label = "דירה",
            value = formData["mailingApartment"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("mailingApartment", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "mailingCity",
            label = "יישוב",
            value = formData["mailingCity"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("mailingCity", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "mailingZipCode",
            label = "מיקוד",
            value = formData["mailingZipCode"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("mailingZipCode", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )
    }
}