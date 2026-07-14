
package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.FormSectionHeader
import com.example.easyfill_project.forms_screens.components.CheckBoxOption
import com.example.easyfill_project.forms_screens.components.SmartTextField
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RentAssistanceSection(
    // Contains the current values of all fields in the form.
    formData: Map<String, String>,

    // Updates a field value in HousingAssistanceFormScreen.
    onFieldChange: (String, String) -> Unit,

    // Reports which SmartTextField the user most recently selected.
    // The field ID is sent upward to HousingAssistanceFormScreen
    // and then to AppNavigation.
    onFocusedFieldChange: (String) -> Unit,
    // Displays the chatbot beside the section headline.
    chatbotContent: @Composable () -> Unit
) {
    // Android context is required for TTS and speech-to-text managers.
    val context = LocalContext.current

    // TTS manager used by the speaker button inside every SmartTextField.
    val ttsManager = remember {
        TextToSpeechManager(context)
    }

    // Speech manager used by the microphone button inside every SmartTextField.
    val speechManager = remember {
        SpeechToTextManager(context)
    }

    // Shut down this section's TTS manager when the section leaves composition.
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Current elevator selection.
    val hasElevator = formData["hasElevator"].orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FormSectionHeader(
            title = "סיוע בשכר דירה",
            chatbotContent = chatbotContent
        )

        Text(
            text = "פרטי הדירה בשכירות",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        SmartTextField(
            fieldId = "rentStreet",
            label = "רחוב",
            value = formData["rentStreet"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("rentStreet", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,

            // Forwards "rentStreet" when this field gets focus.
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "rentHouseNumber",
            label = "מספר בית",
            value = formData["rentHouseNumber"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("rentHouseNumber", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "rentEntrance",
            label = "כניסה",
            value = formData["rentEntrance"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("rentEntrance", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "rentApartment",
            label = "דירה",
            value = formData["rentApartment"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("rentApartment", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "rentCity",
            label = "יישוב",
            value = formData["rentCity"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("rentCity", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "rentZipCode",
            label = "מיקוד",
            value = formData["rentZipCode"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("rentZipCode", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "roomsCount",
            label = "מספר חדרים",
            value = formData["roomsCount"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("roomsCount", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "floor",
            label = "קומה",
            value = formData["floor"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("floor", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        Text(
            text = "מעלית",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )


        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CheckBoxOption("כן", hasElevator == "כן") {
                onFieldChange("hasElevator", if (it) "כן" else "")
            }

            CheckBoxOption("לא", hasElevator == "לא") {
                onFieldChange("hasElevator", if (it) "לא" else "")
            }
        }
    }
}