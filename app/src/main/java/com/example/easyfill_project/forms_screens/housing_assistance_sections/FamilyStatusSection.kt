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
@Composable
fun FamilyStatusSection(
    autofill: Map<String, String?> = emptyMap()
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val speechManager = remember { SpeechToTextManager(context) }

    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    var maritalStatus by remember { mutableStateOf("") }

    // Autofill from Firestore
    LaunchedEffect(autofill["maritalStatus"]) {
        autofill["maritalStatus"]?.let { maritalStatus = it.trim() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 🔹 Section title
        Text(
            text = "מצב משפחתי",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Radio buttons (horizontal like the form)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioOption("רווק/ה", "רווק", maritalStatus) { maritalStatus = it }
            RadioOption("נשוי/אה", "נשוי", maritalStatus) { maritalStatus = it }
            RadioOption("גרוש/ה", "גרוש", maritalStatus) { maritalStatus = it }
            RadioOption("אלמן/ה", "אלמן", maritalStatus) { maritalStatus = it }
            RadioOption("ידוע/ה בציבור", "ידוע בציבור", maritalStatus) { maritalStatus = it }
        }

        // Children fields
        SmartTextField(
            label = "מספר הילדים",
            valueFromAzure = autofill["childrenCount"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )

        SmartTextField(
            label = "גיל הילדים",
            valueFromAzure = autofill["childrenAges"],
            ttsManager = ttsManager,
            speechManager = speechManager
        )
    }
}