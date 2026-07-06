package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.components.RadioOption
import com.example.easyfill_project.forms_screens.components.SmartTextField
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyStatusSection(
    formData: Map<String, String>,
    onFieldChange: (String, String) -> Unit
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val speechManager = remember { SpeechToTextManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    val maritalStatus = formData["maritalStatus"].orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "מצב משפחתי",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioOption("רווק/ה", "רווק", maritalStatus) {
                onFieldChange("maritalStatus", it)
            }

            RadioOption("נשוי/אה", "נשוי", maritalStatus) {
                onFieldChange("maritalStatus", it)
            }

            RadioOption("גרוש/ה", "גרוש", maritalStatus) {
                onFieldChange("maritalStatus", it)
            }

            RadioOption("אלמן/ה", "אלמן", maritalStatus) {
                onFieldChange("maritalStatus", it)
            }

            RadioOption("ידוע/ה בציבור", "ידוע בציבור", maritalStatus) {
                onFieldChange("maritalStatus", it)
            }
        }

        SmartTextField(
            fieldId = "numberOfChildren",
            label = "מספר הילדים",
            value = formData["childrenCount"].orEmpty(),
            onValueChange = { onFieldChange("childrenCount", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            fieldId = "childrenAge",
            label = "גיל הילדים",
            value = formData["childrenAges"].orEmpty(),
            onValueChange = { onFieldChange("childrenAges", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )
    }
}