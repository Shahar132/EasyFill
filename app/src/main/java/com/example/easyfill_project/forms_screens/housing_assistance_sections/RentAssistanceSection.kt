package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.components.CheckBoxOption
import com.example.easyfill_project.forms_screens.components.SmartTextField
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RentAssistanceSection(
    formData: Map<String, String>,
    onFieldChange: (String, String) -> Unit
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val speechManager = remember { SpeechToTextManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    val hasElevator = formData["hasElevator"].orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "סיוע בשכר דירה",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "פרטי הדירה בשכירות",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        SmartTextField(
            label = "רחוב",
            value = formData["rentStreet"].orEmpty(),
            onValueChange = { onFieldChange("rentStreet", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר בית",
            value = formData["rentHouseNumber"].orEmpty(),
            onValueChange = { onFieldChange("rentHouseNumber", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "כניסה",
            value = formData["rentEntrance"].orEmpty(),
            onValueChange = { onFieldChange("rentEntrance", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "דירה",
            value = formData["rentApartment"].orEmpty(),
            onValueChange = { onFieldChange("rentApartment", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "יישוב",
            value = formData["rentCity"].orEmpty(),
            onValueChange = { onFieldChange("rentCity", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מיקוד",
            value = formData["rentZipCode"].orEmpty(),
            onValueChange = { onFieldChange("rentZipCode", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר חדרים",
            value = formData["roomsCount"].orEmpty(),
            onValueChange = { onFieldChange("roomsCount", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "קומה",
            value = formData["floor"].orEmpty(),
            onValueChange = { onFieldChange("floor", it) },
            ttsManager = ttsManager,
            speechManager = speechManager
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