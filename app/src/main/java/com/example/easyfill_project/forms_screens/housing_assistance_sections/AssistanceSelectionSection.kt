package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.components.CheckBoxOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistanceSelectionSection(
    autofill: Map<String, String?> = emptyMap()
) {
    var rentAssistance by remember { mutableStateOf(false) }
    var apartmentAdaptation by remember { mutableStateOf(false) }
    var apartmentExchange by remember { mutableStateOf(false) }
    var houseBuilding by remember { mutableStateOf(false) }
    var firstApartmentPurchase by remember { mutableStateOf(false) }
    var apartmentRenovationLoan by remember { mutableStateOf(false) }
    var firstMortgageAid by remember { mutableStateOf(false) }

    LaunchedEffect(autofill["rentAssistance"]) {
        rentAssistance = !autofill["rentAssistance"].isNullOrBlank()
    }

    LaunchedEffect(autofill["apartmentAdaptation"]) {
        apartmentAdaptation = !autofill["apartmentAdaptation"].isNullOrBlank()
    }

    LaunchedEffect(autofill["apartmentExchange"]) {
        apartmentExchange = !autofill["apartmentExchange"].isNullOrBlank()
    }

    LaunchedEffect(autofill["houseBuilding"]) {
        houseBuilding = !autofill["houseBuilding"].isNullOrBlank()
    }

    LaunchedEffect(autofill["firstApartmentPurchase"]) {
        firstApartmentPurchase = !autofill["firstApartmentPurchase"].isNullOrBlank()
    }

    LaunchedEffect(autofill["apartmentRenovationLoan"]) {
        apartmentRenovationLoan = !autofill["apartmentRenovationLoan"].isNullOrBlank()
    }

    LaunchedEffect(autofill["firstMortgageAid"]) {
        firstMortgageAid = !autofill["firstMortgageAid"].isNullOrBlank()
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
            CheckBoxOption("א׳ - סיוע בשכר דירה", rentAssistance) {
                rentAssistance = it
            }

            CheckBoxOption("ב׳ - התאמת דירה לנכות", apartmentAdaptation) {
                apartmentAdaptation = it
            }

            CheckBoxOption("ג׳ - החלפת דירה", apartmentExchange) {
                apartmentExchange = it
            }

            CheckBoxOption("ד׳ - בניית בית", houseBuilding) {
                houseBuilding = it
            }

            CheckBoxOption("ה׳ - רכישת דירה ראשונה", firstApartmentPurchase) {
                firstApartmentPurchase = it
            }

            CheckBoxOption("ו׳ - הלוואה לשיפוץ דירה", apartmentRenovationLoan) {
                apartmentRenovationLoan = it
            }

            CheckBoxOption("ז׳ - הלוואה לסידור ראשון", firstMortgageAid) {
                firstMortgageAid = it
            }
        }
    }
}