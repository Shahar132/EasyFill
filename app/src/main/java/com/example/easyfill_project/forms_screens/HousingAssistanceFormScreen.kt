package com.example.easyfill_project.forms_screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect

//firestore imports
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

    var personalDetailsMap by remember {
        mutableStateOf<Map<String, String?>>(emptyMap())
    }



    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("upload_prefs", 0)
        val fileId = prefs.getString("latestFileId", null)

        if (uid != null && fileId != null) {
            db.collection("users")
                .document(uid)
                .collection("uploadedFiles")
                .document(fileId)
                .collection("autofillSuggestions")
                .document("latest")
                .get()
                .addOnSuccessListener { doc ->
                    val suggestions = doc.get("suggestions") as? Map<*, *>
                    val personal = suggestions?.get("personalDetails") as? Map<*, *>
                    val address = suggestions?.get("address") as? Map<*, *>
                    val contact = suggestions?.get("contactDetails") as? Map<*, *>

                    personalDetailsMap = mapOf(
                        "firstName" to personal?.get("firstName") as? String,
                        "lastName" to personal?.get("lastName") as? String,
                        "idNumber" to personal?.get("idNumber") as? String,

                        "street" to address?.get("street") as? String,
                        "houseNumber" to address?.get("houseNumber") as? String,
                        "city" to address?.get("city") as? String,
                        "zipCode" to address?.get("zipCode") as? String,

                        "phone" to contact?.get("phone") as? String,
                        "email" to contact?.get("email") as? String
                    )
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
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

            0 -> PersonalDetailsSection(autofill = personalDetailsMap)
            1 -> MailingAddressSection()
            2 -> FamilyStatusSection()
            3 -> IncomeDetailsSection()
            4 -> AssistanceSelectionSection()
            5 -> RentAssistanceSection()
            6 -> SummarySection(navController)

        }


        Spacer(modifier = Modifier.height(24.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("חזור")
                }
            } else {
                Spacer(modifier = Modifier.width(120.dp))
            }

            OutlinedButton(
                onClick = {
                    if (currentStep < sections.size - 1) currentStep++
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("המשך")
            }
        }
    }
}