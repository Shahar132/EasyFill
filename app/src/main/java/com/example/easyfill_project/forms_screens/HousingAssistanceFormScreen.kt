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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.chatbot.logic.BotSuggestion
import com.example.easyfill_project.forms_screens.housing_assistance_sections.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.example.easyfill_project.hand_analysis.MotionTrackingController
import com.example.easyfill_project.form_behavior_analysis.FormBehaviorTrackingController

import com.example.easyfill_project.forms_screens.components.FieldInputRules
import com.example.easyfill_project.forms_screens.components.FieldValidationMessages


import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.chatbot.ui.FloatingChatOverlay
import com.example.easyfill_project.distress_scoring.DistressMode
import com.example.easyfill_project.distress_scoring.DistressUiEvent


//imports regarding the face analysis
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.easyfill_project.face_analysis.FaceMonitoringSession
import com.example.easyfill_project.distress_scoring.DistressScoringManager
import com.example.easyfill_project.face_analysis.FaceRecordingScoreAggregator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.easyfill_project.forms_screens.components.LocalFormValidationMessages

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HousingAssistanceFormScreen(
    navController: NavHostController,
    startStep: Int = 0,

    // Sends the current step back to AppNavigation.
    onStepChanged: (Int) -> Unit = {},

    // Sends the selected field ID to AppNavigation.
    onFocusedFieldChange: (String) -> Unit = {},

    // Current distress information used by the chatbot.
    distressSnapshot: DistressSnapshot,

    // Current activity mode, such as form filling or voice recording.
    distressMode: DistressMode,

    // Current application settings used by chatbot suggestions.
    botAppState: BotAppState,

    // Event created by DistressConfirmationManager.
// It tells the chatbot which suggestion or calming message to show.
    distressUiEvent: DistressUiEvent? = null,

// Sends the selected chatbot action back to AppNavigation.
    onBotAction: (BotAction) -> Unit,

// Reports that the user accepted one of the suggested actions.
    onSuggestionAccepted: (BotSuggestion) -> Unit,

    // Reports that an action suggestion was successfully built
// and displayed to the user.
    onSuggestionDisplayed: (BotSuggestion) -> Unit = {},

// Reports that no unused alternative suggestion remains.
    onAlternativeSuggestionUnavailable: () -> Unit = {},

    // Reports that the accepted-action success message has finished.
    onAcceptedActionMessageClosed: () -> Unit = {},

// Reports that the user pressed "לא עכשיו".
//
// We send the exact BotSuggestion so the same suggestion
// can be shown again later.
    onSuggestionDismissed: (BotSuggestion) -> Unit = {},

// Reports that the user closed a calming message.
    onCalmingMessageClosed: () -> Unit = {},

// Sends an undo request back to AppNavigation.
    onUndoBotAction: (
        BotAction,
        BotAppState
    ) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    /*
 * Whether camera permission is currently granted.
 */
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    /*
     * Requests camera permission for continuous face analysis.
     */
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCameraPermission = granted

            Log.d(
                "FACE_PERMISSION",
                "Camera permission granted=$granted"
            )
        }

    /*
 * Request permission once when the housing form opens.
 */
    LaunchedEffect(Unit) {

        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    /*
     * CameraX needs the lifecycle of the current screen.
     *
     * The face camera will be connected to this lifecycle so CameraX
     * knows when the screen is active, stopped or destroyed.
     */
    val lifecycleOwner = LocalLifecycleOwner.current

    val formId = "housing_assistance"

    /*
 * ============================================================
 * HAND AND FACE MONITORING
 * ============================================================
 */

    /*
     * Create one hand-motion controller for this form-screen instance.
     *
     * remember prevents a new controller from being created after
     * every Compose recomposition.
     */
    val motionController = remember(context) {
        MotionTrackingController(context)
    }

    /*
     * Coroutine scope used by MotionTrackingController.
     */
    val motionScope = rememberCoroutineScope()

    /*
 * Collects reliable face scores only while a voice
 * recording is active.
 *
 * remember ensures that recomposition does not create a new
 * list and lose scores from the current recording.
 */
    val faceRecordingAggregator =
        remember {
            FaceRecordingScoreAggregator()
        }

    /*
 * ============================================================
 * FACE RECORDING LIFECYCLE
 * ============================================================
 *
 * Listen to the same recording lifecycle events already used
 * by MotionTrackingController.
 *
 * beginVoiceRecordingSession()
 *      ↓
 * voiceRecordingStarted event
 *      ↓
 * start collecting face scores
 *
 * requestVoiceRecordingStop()
 *      ↓
 * voiceRecordingStopped event
 *      ↓
 * calculate the face average
 *      ↓
 * submit it to DistressScoringManager
 */
    /*
 * Listen to recording lifecycle events for face aggregation.
 *
 * LaunchedEffect provides a CoroutineScope tied to this
 * composable. Both child collectors are automatically cancelled
 * when the screen leaves the composition.
 */
    LaunchedEffect(
        faceRecordingAggregator
    ) {

        /*
         * Recording start collector.
         */
        launch {
            DistressScoringManager
                .voiceRecordingStarted
                .collect {

                    /*
                     * Clear scores from a previous recording and
                     * start collecting face scores for the new one.
                     */
                    faceRecordingAggregator
                        .startRecording()

                    Log.d(
                        "VOICE_FACE_SESSION",
                        "Received recording-start event."
                    )
                }
        }

        /*
         * Recording stop collector.
         */
        launch {
            DistressScoringManager
                .voiceRecordingStopped
                .collect {

                    /*
                     * Stop collecting and calculate one recording
                     * face average.
                     *
                     * null means that no reliable face result
                     * existed during the recording.
                     */
                    val faceAverage =
                        faceRecordingAggregator
                            .finishRecording()

                    /*
                     * Submit the value even when it is null.
                     *
                     * The manager uses a separate completion flag,
                     * so null means completed but unavailable.
                     */
                    DistressScoringManager
                        .submitVoiceRecordingFaceAverage(
                            average = faceAverage
                        )

                    Log.d(
                        "VOICE_FACE_SESSION",
                        """
                    Submitted completed face result.
                    faceAvailable=${faceAverage != null}
                    faceAverage=$faceAverage
                    """.trimIndent()
                    )
                }
        }
    }

    /*
     * Create one face-monitoring session for this form screen.
     *
     * FaceMonitoringSession owns:
     *
     * - FaceCameraManager
     * - FaceLandmarkerHelper
     * - FaceAnalysisController
     * - FaceDistressAnalyzer
     *
     * It will remain active while the user is inside this form.
     */
    val faceMonitoringSession = remember(
        context,
        lifecycleOwner,
        faceRecordingAggregator
    ) {
        FaceMonitoringSession(
            context = context,
            lifecycleOwner = lifecycleOwner,

            /*
             * Receives the current state of the face-analysis pipeline.
             *
             * This is useful for checking whether the system is:
             *
             * - loading an existing baseline
             * - calibrating
             * - analyzing
             * - reporting an error
             */
            onAnalysisStateChanged = { state ->
                Log.d(
                    "FACE_FORM_SESSION",
                    "phase=${state.phase}, " +
                            "message=${state.message}, " +
                            "baselineReady=${state.baselineReady}"
                )
            },

            /*
             * Receives the full stabilized face result.
             *
             * This is not one raw camera-frame result.
             *
             * FaceDistressAnalyzer has already:
             *
             * - grouped frames into 500 ms windows
             * - checked that enough valid frames exist
             * - compared features to the personal baseline
             * - checked persistence across recent windows
             * - smoothed the face score
             */
            onDistressResult = { result ->

                /*
                 * Only reliable and stabilized face results participate
                 * in either form or recording distress scoring.
                 */
                if (result.isReliable) {

                    /*
                     * Route the reliable face score according to the
                     * application's current analysis mode.
                     */
                    when (
                        DistressScoringManager
                            .mode
                            .value
                    ) {

                        /*
                         * During ordinary form filling:
                         *
                         * Save the latest reliable face score.
                         *
                         * It will be combined with the next completed
                         * five-second hand window using:
                         *
                         * field = 30%
                         * face  = 35%
                         * hand  = 35%
                         */
                        DistressMode.FORM_FILLING -> {

                            DistressScoringManager
                                .updateFormFaceScore(
                                    score = result.score,
                                    timestampMs =
                                        result.windowEndTimestampMs
                                )
                        }

                        /*
                         * During voice recording:
                         *
                         * Do not update the live form score.
                         *
                         * Add every reliable continuous face score to
                         * the recording-level aggregator.
                         */
                        DistressMode.VOICE_RECORDING -> {

                            faceRecordingAggregator
                                .addReliableScore(
                                    score = result.score
                                )
                        }
                    }

                    Log.d(
                        "FACE_DISTRESS_RESULT",
                        """
            mode=${DistressScoringManager.mode.value}
            score=${result.score}
            level=${result.level}
            reliable=${result.isReliable}
            topContributor=${result.topContributor}
            """.trimIndent()
                    )
                }
            },

            /*
             * onScoreReady returns only the rounded level 0–4.
             *
             * We do not use it for weighted fusion because
             * onDistressResult gives us the more precise Float score.
             */
            onScoreReady = { level ->
                Log.d(
                    "FACE_FORM_LEVEL",
                    "Face level=$level"
                )
            },

            /*
             * Reports whether MediaPipe currently detects a face.
             *
             * We log it for testing.
             *
             * The score manager will use timestamp freshness instead
             * of immediately clearing the score after one missed frame.
             */
            onDetectionStatusChanged = { status ->
                Log.d(
                    "FACE_DETECTION_STATUS",
                    "detected=${status.faceDetected}, " +
                            "landmarks=${status.landmarkCount}, " +
                            "message=${status.message}"
                )
            }
        )
    }

    /*
     * Start hand and face monitoring when this screen enters
     * the Compose composition.
     *
     * Stop and release both components when the user leaves
     * the form screen.
     */
    DisposableEffect(
        motionController,
        faceMonitoringSession,
        faceRecordingAggregator
    ) {

        /*
         * Start the existing hand-motion pipeline.
         */
        motionController.startTracking(
            scope = motionScope
        )


        onDispose {

            /*
             * If the user leaves the form while a voice-recording
             * session is still active, cancel the incomplete session
             * before closing the hand and face components.
             */
            if (
                DistressScoringManager.mode.value ==
                DistressMode.VOICE_RECORDING
            ) {
                DistressScoringManager
                    .cancelVoiceRecordingSession()
            }

            /*
             * Stop hand tracking.
             *
             * MotionTrackingController also calls
             * clearFormHandScore().
             */
            motionController.stopTracking()

            /*
             * Stop CameraX, MediaPipe and the face analyzer.
             */
            faceMonitoringSession.close()

            /*
             * Remove any unfinished recording-level face scores.
             */
            faceRecordingAggregator.reset()

            /*
             * Clear all field and step-navigation state belonging
             * to this form session.
             *
             * clear() now marks form behavior as unavailable.
             */
            FormBehaviorTrackingController.clear()

            /*
             * Mark live face information as unavailable.
             */
            DistressScoringManager
                .clearFormFaceScore()

            Log.d(
                "FACE_FORM_SESSION",
                "Hand, face and form-behavior monitoring stopped."
            )
        }
    }

    DisposableEffect(
        faceMonitoringSession,
        hasCameraPermission
    ) {

        if (hasCameraPermission) {

            val faceStarted =
                faceMonitoringSession.start()

            Log.d(
                "FACE_FORM_SESSION",
                "Face monitoring started=$faceStarted"
            )

        } else {

            /*
             * Camera permission was denied or has not yet been granted.
             * Face is unavailable, but hand and form analysis continue.
             */
            DistressScoringManager
                .clearFormFaceScore()

            Log.d(
                "FACE_FORM_SESSION",
                "Face monitoring not started because camera permission is unavailable."
            )
        }

        onDispose {
            /*
             * Permission-state changes should stop only the camera,
             * not the complete hand/form tracking session.
             */
            faceMonitoringSession.close()

            DistressScoringManager
                .clearFormFaceScore()
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

    var validationIssues by remember {
        mutableStateOf<List<FormIssue>>(emptyList())
    }

    val validationMessagesByField = remember(validationIssues) {
        validationIssues.associate { issue ->
            issue.fieldId to issue.message
        }
    }

    LaunchedEffect(
        formData,
        validationIssues.isNotEmpty()
    ) {
        if (validationIssues.isNotEmpty()) {
            validationIssues =
                HousingAssistanceFormValidator.validateForm(
                    formData = formData
                )
        }
    }

    // Compares the declared number of children with the entered ages.
    val childrenAgesCountError =
        FieldInputRules.validateChildrenAgesCount(
            childrenCountValue =
                formData["childrenCount"].orEmpty(),

            childrenAgesValue =
                formData["childrenAges"].orEmpty()
        )

// Converts the comparison error into a user-facing message.
    val childrenAgesCountMessage =
        childrenAgesCountError?.let { error ->
            FieldValidationMessages.getMessage(error)
        }

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

    // The outer Box keeps the chatbot fixed above the scrolling form.
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {

            FormProgressBar(
                currentStep = currentStep,
                sections = sections
            )

            Spacer(modifier = Modifier.height(10.dp))

            CompositionLocalProvider(
                LocalFormValidationMessages provides
                        validationMessagesByField
            ) {
                when (currentStep) {
                    0 -> PersonalDetailsSection(
                        formData = formData,
                        onFieldChange = ::updateField,

                        // Forwards the selected field to AppNavigation.
                        onFocusedFieldChange = onFocusedFieldChange,
                        chatbotContent = {}
                    )

                    1 -> MailingAddressSection(
                        formData = formData,
                        onFieldChange = ::updateField,

                        // Sends the selected income field toward AppNavigation.
                        onFocusedFieldChange = onFocusedFieldChange,
                        chatbotContent = {}
                    )

                    2 -> FamilyStatusSection(
                        formData = formData,
                        onFieldChange = ::updateField,

                        // Sends the children-count comparison error to the ages field.
                        childrenAgesValidationMessage =
                            childrenAgesCountMessage,

                        // Sends the selected family-status text field upward.
                        onFocusedFieldChange = onFocusedFieldChange,
                        chatbotContent = {}
                    )

                    3 -> IncomeDetailsSection(
                        formData = formData,
                        onFieldChange = ::updateField,

                        // Sends the selected income field toward AppNavigation.
                        onFocusedFieldChange = onFocusedFieldChange,
                        chatbotContent = {}
                    )

                    4 -> AssistanceSelectionSection(formData, ::updateField, chatbotContent = {})

                    5 -> RentAssistanceSection(
                        formData = formData,
                        onFieldChange = ::updateField,

                        // Sends the selected rent field toward AppNavigation.
                        onFocusedFieldChange = onFocusedFieldChange,
                        chatbotContent = {}
                    )

                    6 -> SummarySection(
                        formData = formData,

                        onValidationIssuesFound = { issues ->
                            validationIssues = issues

                            val firstIssue =
                                issues.firstOrNull()

                            if (firstIssue != null) {
                                val targetStep =
                                    firstIssue.sectionIndex

                                FormBehaviorTrackingController
                                    .onStepChanged(
                                        fromStep = currentStep,
                                        toStep = targetStep
                                    )

                                saveStep(targetStep)
                                currentStep = targetStep
                            }
                        }
                    )
                }
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

        // The chatbot is outside the scrolling Column, so it stays in the
        // same visible screen position while the user scrolls the form.
        if (currentStep != 6) {
            FloatingChatOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 76.dp,
                        end = 70.dp
                    )
                    .zIndex(100f),

                distressSnapshot = distressSnapshot,
                distressMode = distressMode,

                // The parameter of this screen is named botAppState.
                appState = botAppState,

                distressUiEvent = distressUiEvent,

                // Forward the action to the parent.
                onBotAction = { action ->
                    onBotAction(action)
                },

                /**
                 * FloatingChatOverlay sends the exact accepted suggestion.
                 * Forward it to AppNavigation, where the manager exists.
                 */
                onSuggestionAccepted = { suggestion ->
                    onSuggestionAccepted(suggestion)
                },

                onSuggestionDisplayed = { suggestion ->
                    onSuggestionDisplayed(suggestion)
                },

                onAlternativeSuggestionUnavailable = {
                    onAlternativeSuggestionUnavailable()
                },

                onAcceptedActionMessageClosed = {
                    onAcceptedActionMessageClosed()
                },

                onSuggestionDismissed = { suggestion ->
                    onSuggestionDismissed(suggestion)
                },

                onCalmingMessageClosed = {
                    onCalmingMessageClosed()
                },

                onNavigateToColorSettings = {
                    // Add your navigation route here later.
                },

                onNavigateToFontSettings = {
                    // Add your navigation route here later.
                },

                onNavigateToMusicSettings = {
                    // Add your navigation route here later.
                },

                onUndoAction = { action, previousState ->
                    onUndoBotAction(action, previousState)
                }
            )
        }
    }
}