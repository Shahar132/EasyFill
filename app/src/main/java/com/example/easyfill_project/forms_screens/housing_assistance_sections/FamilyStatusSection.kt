package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.easyfill_project.forms_screens.components.RadioOption
import com.example.easyfill_project.forms_screens.components.SmartTextField
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyStatusSection(
    // Contains all current values of the form.
    formData: Map<String, String>,

    // Sends changed field values back to HousingAssistanceFormScreen.
    onFieldChange: (String, String) -> Unit,

    // Reports which SmartTextField the user most recently selected.
    // This is forwarded to AppNavigation for field-help reading.
    onFocusedFieldChange: (String) -> Unit,

    // Displays the chatbot beside the section headline.
    chatbotContent: @Composable () -> Unit
) {
    // Android context is required for TTS and speech-to-text.
    val context = LocalContext.current

    // Used by the speaker button inside SmartTextField.
    val ttsManager = remember {
        TextToSpeechManager(context)
    }

    // Used by the microphone button inside SmartTextField.
    val speechManager = remember {
        SpeechToTextManager(context)
    }

    // Releases TTS resources when this section leaves the screen.
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Current marital-status value saved in the form.
    val maritalStatus = formData["maritalStatus"].orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        FormSectionHeader(
            title = "מצב משפחתי",
            chatbotContent = chatbotContent
        )

        // Marital-status options.
        // These are RadioOption components, not SmartTextField,
        // so they do not currently report focused-field changes.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioOption(
                "רווק/ה",
                "רווק",
                maritalStatus
            ) { selectedValue ->
                onFieldChange("maritalStatus", selectedValue)
            }

            RadioOption(
                "נשוי/אה",
                "נשוי",
                maritalStatus
            ) { selectedValue ->
                onFieldChange("maritalStatus", selectedValue)
            }

            RadioOption(
                "גרוש/ה",
                "גרוש",
                maritalStatus
            ) { selectedValue ->
                onFieldChange("maritalStatus", selectedValue)
            }

            RadioOption(
                "אלמן/ה",
                "אלמן",
                maritalStatus
            ) { selectedValue ->
                onFieldChange("maritalStatus", selectedValue)
            }

            RadioOption(
                "ידוע/ה בציבור",
                "ידוע בציבור",
                maritalStatus
            ) { selectedValue ->
                onFieldChange("maritalStatus", selectedValue)
            }
        }

        SmartTextField(
            // Changed from "numberOfChildren" so it matches
            // formData["childrenCount"] and onFieldChange.
            fieldId = "childrenCount",
            label = "מספר הילדים",
            value = formData["childrenCount"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("childrenCount", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,

            // Sends "childrenCount" when this field receives focus.
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            // Changed from "childrenAge" so it matches
            // formData["childrenAges"] and onFieldChange.
            fieldId = "childrenAges",
            label = "גיל הילדים",
            value = formData["childrenAges"].orEmpty(),
            onValueChange = { newValue ->
                onFieldChange("childrenAges", newValue)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,

            // Sends "childrenAges" when this field receives focus.
            onFocusedFieldChange = onFocusedFieldChange
        )
    }
}