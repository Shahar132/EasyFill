package com.example.easyfill_project.forms_screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.runtime.setValue
import com.example.easyfill_project.forms_screens.housing_assistance_sections.AssistanceSelectionSection
import com.example.easyfill_project.forms_screens.housing_assistance_sections.FamilyStatusSection
import com.example.easyfill_project.forms_screens.housing_assistance_sections.IncomeDetailsSection
import com.example.easyfill_project.forms_screens.housing_assistance_sections.MailingAddressSection
import com.example.easyfill_project.forms_screens.housing_assistance_sections.PersonalDetailsSection
import com.example.easyfill_project.forms_screens.housing_assistance_sections.RentAssistanceSection
import com.example.easyfill_project.forms_screens.housing_assistance_sections.SummarySection

@Composable
fun HousingAssistanceFormScreen(navController: NavHostController) {

    var currentStep by remember { mutableStateOf(0) }

    val sections = listOf(
        "פרטים אישיים",
        "כתובת למשלוח דואר",
        "מצב משפחתי",
        "פירוט הכנסות",
        "בחירת הסיוע בדיור",
        "סיוע בשכר דירה",
        "סיכום"
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        // Progress bar
        FormProgressBar(
            currentStep = currentStep,
            sections = sections
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section content
        when (currentStep) {

            0 -> PersonalDetailsSection()
            1 -> MailingAddressSection()
            2 -> FamilyStatusSection()
            3 -> IncomeDetailsSection()
            4 -> AssistanceSelectionSection()
            5 -> RentAssistanceSection()
            6 -> SummarySection(navController)

        }

        Spacer(modifier = Modifier.weight(1f))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            if (currentStep > 0) {
                Button(onClick = { currentStep-- }) {
                    Text("חזור")
                }
            }

            Button(onClick = {
                if (currentStep < sections.size - 1) {
                    currentStep++
                }
            }) {
                Text("המשך")
            }
        }
    }
}