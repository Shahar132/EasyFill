package com.example.easyfill_project.forms_screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.forms_screens.housing_assistance_sections.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay

import com.example.easyfill_project.hand_analysis.MotionTrackingController
import com.example.easyfill_project.form_behavior_analysis.FormBehaviorTrackingController



@Composable
fun HousingAssistanceFormScreen(
    navController: NavHostController,
    startStep: Int = 0,

    // Sends the current step back to AppNavigation.
    onStepChanged: (Int) -> Unit = {},


    // Sends the selected field ID to AppNavigation.
    onFocusedFieldChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val formId = "housing_assistance"

    val motionController = remember {
        MotionTrackingController(context)
    }

    val motionScope = rememberCoroutineScope()

    DisposableEffect(Unit) {//It starts sensors for 30 seconds to create baseline.
        motionController.startTracking(motionScope)

        onDispose {
            motionController.stopTracking()
        }
    }

    val sections = FormsRegistry.getFormById(formId).sections

// Stores the currently displayed form section.
    var currentStep by rememberSaveable(startStep, sections.lastIndex) {
        mutableIntStateOf(
            startStep.coerceIn(0, sections.lastIndex)
        )
    }

// Sends the current step to AppNavigation every time it changes.
    LaunchedEffect(currentStep) {
        onStepChanged(currentStep)
    }

    var formData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dataLoaded by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    fun updateField(key: String, value: String) {
        val oldValue = formData[key].orEmpty()
        val updated = formData.toMutableMap()

        updated[key] = value

        fun syncIfNotChanged(targetKey: String) {
            val currentTargetValue = formData[targetKey].orEmpty()
            if (currentTargetValue.isBlank() || currentTargetValue == oldValue) {
                updated[targetKey] = value
            }
        }

        when (key) {
            "street" -> {
                syncIfNotChanged("mailingStreet")
                syncIfNotChanged("rentStreet")
            }

            "houseNumber" -> {
                syncIfNotChanged("mailingHouseNumber")
                syncIfNotChanged("rentHouseNumber")
            }

            "city" -> {
                syncIfNotChanged("mailingCity")
                syncIfNotChanged("rentCity")
            }

            "zipCode" -> {
                syncIfNotChanged("mailingZipCode")
                syncIfNotChanged("rentZipCode")
            }

            "entrance" -> {
                syncIfNotChanged("mailingEntrance")
                syncIfNotChanged("rentEntrance")
            }

            "apartment" -> {
                syncIfNotChanged("mailingApartment")
                syncIfNotChanged("rentApartment")
            }
        }

        formData = updated
    }

    fun saveFormData() {
        if (uid == null || formData.isEmpty()) return

        db.collection("users")
            .document(uid)
            .collection("savedUpdatedData")
            .document("allFields")
            .set(formData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("SAVE_FORM", "savedUpdatedData saved")
            }
            .addOnFailureListener { e ->
                Log.e("SAVE_FORM", "save error", e)
            }
    }

    fun saveStep(step: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FormProgressStorage.saveCurrentStep(
            context = context,
            uid = uid,
            formId = formId,
            currentStep = step
        )
    }

    LaunchedEffect(formData, dataLoaded) {
        if (dataLoaded && formData.isNotEmpty()) {
            delay(2000)
            saveFormData()
        }
    }

    LaunchedEffect(Unit) {
        if (uid == null) return@LaunchedEffect

        db.collection("users")
            .document(uid)
            .collection("savedUpdatedData")
            .document("allFields")
            .get()
            .addOnSuccessListener { savedDoc ->

                val savedData = savedDoc.data
                    ?.mapValues { it.value?.toString().orEmpty() }
                    ?: emptyMap()

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

                        if (remaining == 0) {
                            formData = savedData
                            dataLoaded = true
                            return@addOnSuccessListener
                        }

                        fun putIfMissing(key: String, value: String?) {
                            if (mergedMap[key].isNullOrBlank() && !value.isNullOrBlank()) {
                                mergedMap[key] = value
                            }
                        }

                        fun finishOneFile() {
                            remaining--

                            if (remaining == 0) {
                                val azureData = mergedMap
                                    .filterValues { !it.isNullOrBlank() }
                                    .mapValues { it.value.orEmpty() }

                                formData = azureData + savedData
                                dataLoaded = true
                            }
                        }

                        files.documents.forEach { fileDoc ->
                            db.collection("users")
                                .document(uid)
                                .collection("uploadedFiles")
                                .document(fileDoc.id)
                                .collection("autofillSuggestions")
                                .document("latest")
                                .get()
                                .addOnSuccessListener { doc ->

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

                                    putIfMissing("mailingStreet", address?.get("street")?.toString())
                                    putIfMissing("mailingHouseNumber", address?.get("houseNumber")?.toString())
                                    putIfMissing("mailingCity", address?.get("city")?.toString())
                                    putIfMissing("mailingZipCode", address?.get("zipCode")?.toString())
                                    putIfMissing("mailingEntrance", address?.get("entrance")?.toString())
                                    putIfMissing("mailingApartment", address?.get("apartment")?.toString())

                                    putIfMissing("rentStreet", address?.get("street")?.toString())
                                    putIfMissing("rentHouseNumber", address?.get("houseNumber")?.toString())
                                    putIfMissing("rentCity", address?.get("city")?.toString())
                                    putIfMissing("rentZipCode", address?.get("zipCode")?.toString())
                                    putIfMissing("rentEntrance", address?.get("entrance")?.toString())
                                    putIfMissing("rentApartment", address?.get("apartment")?.toString())

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

                                    finishOneFile()
                                }
                                .addOnFailureListener {
                                    finishOneFile()
                                }
                        }
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
            0 -> PersonalDetailsSection(
                formData = formData,
                onFieldChange = ::updateField,

                // Forwards the selected field to AppNavigation.
                onFocusedFieldChange = onFocusedFieldChange
            )
            1 -> MailingAddressSection(
                formData = formData,
                onFieldChange = ::updateField,

                // Sends the selected income field toward AppNavigation.
                onFocusedFieldChange = onFocusedFieldChange
            )

            2 -> FamilyStatusSection(
                formData = formData,
                onFieldChange = ::updateField,

                // Sends the selected family-status text field upward.
                onFocusedFieldChange = onFocusedFieldChange
            )

            3 -> IncomeDetailsSection(
                formData = formData,
                onFieldChange = ::updateField,

                // Sends the selected income field toward AppNavigation.
                onFocusedFieldChange = onFocusedFieldChange
            )
            4 -> AssistanceSelectionSection(formData, ::updateField)

            5 -> RentAssistanceSection(
                formData = formData,
                onFieldChange = ::updateField,

                // Sends the selected rent field toward AppNavigation.
                onFocusedFieldChange = onFocusedFieldChange
            )
            6 -> SummarySection(navController)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = {
                        saveFormData()

                        val previousStep = currentStep - 1

                        FormBehaviorTrackingController.onStepChanged(
                            fromStep = currentStep,
                            toStep = previousStep
                        )

                        saveStep(previousStep)
                        currentStep = previousStep
                    },
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
                    saveFormData()

                    if (currentStep < sections.size - 1) {
                        val nextStep = currentStep + 1

                        FormBehaviorTrackingController.onStepChanged(
                            fromStep = currentStep,
                            toStep = nextStep
                        )

                        saveStep(nextStep)
                        currentStep = nextStep

                    } else {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid

                        if (uid != null) {
                            FormProgressStorage.markCompleted(
                                context = context,
                                uid = uid,
                                formId = formId
                            )
                        }

                        navController.navigate("demoFormOptions")
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