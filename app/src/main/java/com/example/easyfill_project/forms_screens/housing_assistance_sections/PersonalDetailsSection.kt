package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.FormSectionHeader
import com.example.easyfill_project.forms_screens.components.SmartTextField
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
@Composable
fun PersonalDetailsSection(
    formData: Map<String, String>,
    onFieldChange: (String, String) -> Unit,

    // Sends the ID of the field currently selected by the user
    // back to HousingAssistanceFormScreen.
    onFocusedFieldChange: (String) -> Unit,
    // Displays the chatbot beside the section headline.
    chatbotContent: @Composable () -> Unit
) {
    val context = LocalContext.current

    val ttsManager = remember {
        TextToSpeechManager(context)
    }

    val speechManager = remember {
        SpeechToTextManager(context)
    }

    // Shuts down this section's TTS manager when the section leaves the screen.
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        FormSectionHeader(
            title = "פרטים אישיים",
            chatbotContent = chatbotContent
        )

        Spacer(modifier = Modifier.height(12.dp))

        SmartTextField(
            fieldId = "lastName",
            label = "שם משפחה",
            value = formData["lastName"].orEmpty(),
            onValueChange = {
                onFieldChange("lastName", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,

            // Passes "lastName" when this field receives focus.
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "firstName",
            label = "שם פרטי",
            value = formData["firstName"].orEmpty(),
            onValueChange = {
                onFieldChange("firstName", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "idNumber",
            label = "מספר תעודת זהות",
            value = formData["idNumber"].orEmpty(),
            onValueChange = {
                onFieldChange("idNumber", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "street",
            label = "רחוב",
            value = formData["street"].orEmpty(),
            onValueChange = {
                onFieldChange("street", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "houseNumber",
            label = "מספר בית",
            value = formData["houseNumber"].orEmpty(),
            onValueChange = {
                onFieldChange("houseNumber", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "city",
            label = "יישוב",
            value = formData["city"].orEmpty(),
            onValueChange = {
                onFieldChange("city", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            // Must match the formData key.
            fieldId = "zipCode",
            label = "מיקוד",
            value = formData["zipCode"].orEmpty(),
            onValueChange = {
                onFieldChange("zipCode", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            // Must match the formData key.
            fieldId = "phone",
            label = "טלפון נייד",
            value = formData["phone"].orEmpty(),
            onValueChange = {
                onFieldChange("phone", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )

        SmartTextField(
            fieldId = "email",
            label = "דואר אלקטרוני",
            value = formData["email"].orEmpty(),
            onValueChange = {
                onFieldChange("email", it)
            },
            ttsManager = ttsManager,
            speechManager = speechManager,
            onFocusedFieldChange = onFocusedFieldChange
        )
    }
}