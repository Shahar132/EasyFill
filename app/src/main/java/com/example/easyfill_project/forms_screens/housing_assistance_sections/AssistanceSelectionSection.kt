package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.components.CheckBoxOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistanceSelectionSection(
    formData: Map<String, String>,
    onFieldChange: (String, String) -> Unit
) {
    fun isChecked(key: String): Boolean {
        val value = formData[key]
        return !value.isNullOrBlank() && value != "false"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "בחירת הסיוע בדיור",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "איזה סיוע בדיור אתה מבקש? ניתן לסמן יותר מאפשרות אחת",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CheckBoxOption("א׳ - סיוע בשכר דירה", isChecked("rentAssistance")) {
                onFieldChange("rentAssistance", it.toString())
            }

            CheckBoxOption("ב׳ - התאמת דירה לנכות", isChecked("apartmentAdaptation")) {
                onFieldChange("apartmentAdaptation", it.toString())
            }

            CheckBoxOption("ג׳ - החלפת דירה", isChecked("apartmentExchange")) {
                onFieldChange("apartmentExchange", it.toString())
            }

            CheckBoxOption("ד׳ - בניית בית", isChecked("houseBuilding")) {
                onFieldChange("houseBuilding", it.toString())
            }

            CheckBoxOption("ה׳ - רכישת דירה ראשונה", isChecked("firstApartmentPurchase")) {
                onFieldChange("firstApartmentPurchase", it.toString())
            }

            CheckBoxOption("ו׳ - הלוואה לשיפוץ דירה", isChecked("apartmentRenovationLoan")) {
                onFieldChange("apartmentRenovationLoan", it.toString())
            }

            CheckBoxOption("ז׳ - הלוואה לסידור ראשון", isChecked("firstMortgageAid")) {
                onFieldChange("firstMortgageAid", it.toString())
            }
        }
    }
}