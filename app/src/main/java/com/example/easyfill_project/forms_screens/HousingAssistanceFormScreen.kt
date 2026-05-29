package com.example.easyfill_project.forms_screens

import android.util.Log // ✅ ADDED
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

    var personalDetailsMap by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(Unit) {
        Log.d("AUTOFILL", "uid = $uid")

        if (uid != null) {
            db.collection("users")
                .document(uid)
                .collection("uploadedFiles")
                .orderBy(
                    "uploadedAt",
                    com.google.firebase.firestore.Query.Direction.DESCENDING
                )
                .get()
                .addOnSuccessListener { files ->

                    val mergedMap = mutableMapOf<String, String?>()
                    var remaining = files.documents.size

                    Log.d("AUTOFILL", "files count = $remaining")

                    if (remaining == 0) {
                        personalDetailsMap = emptyMap()
                        return@addOnSuccessListener
                    }

                    fun putIfMissing(key: String, value: String?) {
                        if (mergedMap[key].isNullOrBlank() && !value.isNullOrBlank()) {
                            mergedMap[key] = value
                        }
                    }

                    files.documents.forEach { fileDoc ->
                        val fileId = fileDoc.id
                        Log.d("AUTOFILL", "checking fileId = $fileId")

                        db.collection("users")
                            .document(uid)
                            .collection("uploadedFiles")
                            .document(fileId)
                            .collection("autofillSuggestions")
                            .document("latest")
                            .get()
                            .addOnSuccessListener { doc ->

                                //what needs to pull for this housing assistance form
                                val suggestions = doc.get("suggestions") as? Map<*, *>
                                val personal = suggestions?.get("personalDetails") as? Map<*, *>
                                val address = suggestions?.get("address") as? Map<*, *>
                                val contact = suggestions?.get("contactDetails") as? Map<*, *>
                                val income = suggestions?.get("incomeDetails") as? Map<*, *>
                                val assistance = suggestions?.get("assistanceSelection") as? Map<*, *>



                                putIfMissing("firstName", personal?.get("firstName")?.toString())
                                putIfMissing("lastName", personal?.get("lastName")?.toString())
                                putIfMissing("idNumber", personal?.get("idNumber")?.toString())
                                putIfMissing("maritalStatus", personal?.get("maritalStatus")?.toString())
                                putIfMissing("birthDate", personal?.get("birthDate")?.toString())
                                putIfMissing("birthCountry", personal?.get("birthCountry")?.toString())
                                putIfMissing("fatherName", personal?.get("fatherName")?.toString())

                                putIfMissing("street", address?.get("street")?.toString())
                                putIfMissing("houseNumber", address?.get("houseNumber")?.toString())
                                putIfMissing("city", address?.get("city")?.toString())
                                putIfMissing("zipCode", address?.get("zipCode")?.toString())
                                putIfMissing("entrance", address?.get("entrance")?.toString())
                                putIfMissing("apartment", address?.get("apartment")?.toString())
                                putIfMissing("roomsCount", address?.get("roomsCount")?.toString())
                                putIfMissing("floor", address?.get("floor")?.toString())
                                putIfMissing("hasElevator", address?.get("hasElevator")?.toString())

                                putIfMissing("phone", contact?.get("phone")?.toString())
                                putIfMissing("email", contact?.get("email")?.toString())

                                putIfMissing("workPlace", income?.get("workPlace")?.toString())
                                putIfMissing("salaryNet", income?.get("salaryNet")?.toString())
                                putIfMissing("partnerWorkPlace", income?.get("partnerWorkPlace")?.toString())
                                putIfMissing("partnerSalaryNet", income?.get("partnerSalaryNet")?.toString())
                                putIfMissing("additionalIncomeDetails", income?.get("additionalIncomeDetails")?.toString())

                                putIfMissing("rentAssistance", assistance?.get("סיוע בשכר דירה")?.toString())
                                putIfMissing("apartmentAdaptation", assistance?.get("התאמת דירה לנכות")?.toString())
                                putIfMissing("apartmentExchange", assistance?.get("החלפת דירה")?.toString())
                                putIfMissing("houseBuilding", assistance?.get("בניית בית")?.toString())
                                putIfMissing("firstApartmentPurchase", assistance?.get("רכישת דירה ראשונה")?.toString())
                                putIfMissing("apartmentRenovationLoan", assistance?.get("הלוואה לשיפוץ דירה")?.toString())
                                putIfMissing("firstMortgageAid", assistance?.get("הלוואה לסידור ראשון")?.toString())

                                remaining--

                                if (remaining == 0) {
                                    personalDetailsMap = mergedMap
                                    Log.d("AUTOFILL", "FINAL mergedMap = $mergedMap")
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("AUTOFILL", "suggestions error for fileId=$fileId", e)

                                remaining--

                                if (remaining == 0) {
                                    personalDetailsMap = mergedMap
                                    Log.d("AUTOFILL", "FINAL mergedMap = $mergedMap")
                                }
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("AUTOFILL", "uploadedFiles query error", e)
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

        FormProgressBar(currentStep = currentStep, sections = sections)

        Spacer(modifier = Modifier.height(20.dp))

        when (currentStep) {
            0 -> PersonalDetailsSection(autofill = personalDetailsMap)
            1 -> MailingAddressSection(autofill = personalDetailsMap)
            2 -> FamilyStatusSection(autofill = personalDetailsMap)
            3 -> IncomeDetailsSection(autofill = personalDetailsMap)
            4 -> AssistanceSelectionSection(autofill = personalDetailsMap)
            5 -> RentAssistanceSection(autofill = personalDetailsMap)
            6 -> SummarySection(navController)
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                    if (currentStep < sections.size - 1) {
                        currentStep++
                    } else {
                        navController.navigate("demoFormOptions")//last section navigate to demo oprions
                    }
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    if (currentStep == sections.size - 1) {
                        "בחירת טופס נוסף"
                    } else {
                        "המשך"
                    }
                )
            }
        }
    }
}