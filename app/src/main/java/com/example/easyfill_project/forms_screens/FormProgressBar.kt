package com.example.easyfill_project.forms_screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
// Added imports for accessibility semantics:
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

//Shows current section progress
//(step 3 מתוך 7)
@Composable
fun FormProgressBar(
    currentStep: Int,
    sections: List<String>
) {
    val totalSteps = sections.size
    val progress = (currentStep + 1) / totalSteps.toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = "שלב ${currentStep + 1} מתוך $totalSteps",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(6.dp))

        val progressColor = when (currentStep) {
            0 -> Color(0xFFE57373) // red
            1 -> Color(0xFFFFB74D) // orange
            2 -> Color(0xFFFFD54F) // yellow
            3 -> Color(0xFF81C784) // green
            4 -> Color(0xFF4DB6AC) // teal
            5 -> Color(0xFF64B5F6) // blue
            else -> Color(0xFF9575CD) // purple
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp) // <-- Removed the comma here!
                .semantics {
                    contentDescription = "פס התקדמות: שלב ${currentStep + 1} מתוך $totalSteps"
                },
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            //this shows text, Because the user is on step 1, that text outputs "פרטים אישיים".
            text = sections[currentStep],
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            // Add this modifier to give the screen reader a unique, descriptive label!
            modifier = Modifier.semantics {
                contentDescription = "נושא השלב: ${sections[currentStep]}"
            }
        )
    }
}