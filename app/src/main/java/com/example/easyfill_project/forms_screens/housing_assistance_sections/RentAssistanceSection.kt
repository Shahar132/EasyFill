package com.example.easyfill_project.forms_screens.housing_assistance_sections


import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    autofill: Map<String, String?> = emptyMap()
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val speechManager = remember { SpeechToTextManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    var hasElevatorYes by remember { mutableStateOf(false) }
    var hasElevatorNo by remember { mutableStateOf(false) }


    LaunchedEffect(autofill["hasElevator"]) {
        val value = autofill["hasElevator"]?.trim()

        hasElevatorYes = value == "כן"
        hasElevatorNo = value == "לא"
    }


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
            valueFromAzure = autofill["street"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר בית",
            valueFromAzure = autofill["houseNumber"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "כניסה",
            valueFromAzure = autofill["entrance"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "דירה",
            valueFromAzure = autofill["apartment"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "יישוב",
            valueFromAzure = autofill["city"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מיקוד",
            valueFromAzure = autofill["zipCode"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "מספר חדרים",
            valueFromAzure = autofill["roomsCount"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "קומה",
            valueFromAzure = autofill["floor"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        Text(
            text = "מעלית",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CheckBoxOption("כן", hasElevatorYes) {
                hasElevatorYes = it
                if (it) hasElevatorNo = false
            }

            CheckBoxOption("לא", hasElevatorNo) {
                hasElevatorNo = it
                if (it) hasElevatorYes = false
            }
        }

    }
}