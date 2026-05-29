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
    autofill: Map<String, String?> = emptyMap()
) {
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val speechManager = remember { SpeechToTextManager(context) }

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
            label = "רחוב/תא דואר",
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
            label = "ישוב",
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
    }
}