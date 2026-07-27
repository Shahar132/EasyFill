package com.example.easyfill_project.screen

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.easyfill_project.forms_screens.FormDefinition
import com.example.easyfill_project.forms_screens.FormProgressStorage
import com.example.easyfill_project.forms_screens.FormStatus
import com.example.easyfill_project.forms_screens.FormsRegistry
import com.example.easyfill_project.forms_screens.HousingAssistanceFormValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private const val HOUSING_ASSISTANCE_FORM_ID =
    "housing_assistance"

private const val RENT_ASSISTANCE_STEP_INDEX = 5
private const val SUMMARY_STEP_INDEX = 6

data class FormProgressItem(
    val form: FormDefinition,
    val progress: Float,
    val percentText: Int,
    val status: FormStatus,

    // Actual section index used for navigation.
    val currentStep: Int,

    // Values adjusted for conditional sections.
    val currentStepForDisplay: Int,
    val totalStepsForDisplay: Int,
    val currentSectionName: String,

    val lastUpdated: String
)

@Composable
fun MyFormsProgressScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val db = remember {
        FirebaseFirestore.getInstance()
    }

    val uid =
        FirebaseAuth.getInstance()
            .currentUser
            ?.uid

    /*
     * Changes whenever the progress screen returns to the foreground.
     *
     * Firestore updates saved field values, while this key causes
     * local FormProgressStorage values such as the current step and
     * last update time to be read again.
     */
    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshKey++
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var savedFields by remember(uid) {
        mutableStateOf<Map<String, String>>(
            emptyMap()
        )
    }

    /*
     * Listens continuously for saved form-field changes.
     *
     * This prevents the progress screen from displaying old data
     * when the Firestore save finishes after navigation.
     */
    DisposableEffect(uid) {
        if (uid == null) {
            savedFields = emptyMap()

            onDispose { }
        } else {
            val listenerRegistration =
                db.collection("users")
                    .document(uid)
                    .collection("savedUpdatedData")
                    .document("allFields")
                    .addSnapshotListener { document, error ->

                        if (error != null) {
                            Log.e(
                                "FORM_PROGRESS",
                                "Failed to load saved form fields",
                                error
                            )

                            return@addSnapshotListener
                        }

                        savedFields =
                            document
                                ?.data
                                ?.mapValues { entry ->
                                    entry.value
                                        ?.toString()
                                        .orEmpty()
                                }
                                ?: emptyMap()
                    }

            onDispose {
                listenerRegistration.remove()
            }
        }
    }

    /*
     * refreshKey is intentionally included because the current step
     * and last-update time are stored locally rather than in Firestore.
     */
    val forms = remember(
        savedFields,
        uid,
        refreshKey
    ) {
        FormsRegistry.forms.map { form ->

            val savedCurrentStep =
                if (
                    uid != null &&
                    form.sections.isNotEmpty()
                ) {
                    FormProgressStorage
                        .getCurrentStep(
                            context = context,
                            uid = uid,
                            formId = form.formId
                        )
                        .coerceIn(
                            minimumValue = 0,
                            maximumValue =
                                form.sections.lastIndex
                        )
                } else {
                    0
                }

            val isHousingAssistanceForm =
                form.formId ==
                        HOUSING_ASSISTANCE_FORM_ID

            val isRentAssistanceSelected =
                isSelectedOption(
                    savedFields[
                        "rentAssistance"
                    ].orEmpty()
                )

            /*
             * A previously saved step may point to the rent section.
             *
             * When option A is no longer selected, continuing the form
             * should open the summary instead of the hidden rent step.
             */
            val currentStep =
                if (
                    isHousingAssistanceForm &&
                    savedCurrentStep ==
                    RENT_ASSISTANCE_STEP_INDEX &&
                    !isRentAssistanceSelected &&
                    form.sections.lastIndex >=
                    SUMMARY_STEP_INDEX
                ) {
                    SUMMARY_STEP_INDEX
                } else {
                    savedCurrentStep
                }

            /*
             * Removes the rent-assistance step from the displayed
             * progress only when option A is not selected.
             *
             * The original indexes remain unchanged for navigation.
             */
            val visibleStepIndexes =
                form.sections.indices.filter { stepIndex ->
                    !isHousingAssistanceForm ||
                            stepIndex !=
                            RENT_ASSISTANCE_STEP_INDEX ||
                            isRentAssistanceSelected
                }

            val currentStepForDisplay =
                visibleStepIndexes
                    .indexOf(currentStep)
                    .takeIf { index ->
                        index >= 0
                    }
                    ?.plus(1)
                    ?: 1

            val totalStepsForDisplay =
                visibleStepIndexes.size

            val currentSectionName =
                form.sections.getOrElse(
                    currentStep
                ) {
                    form.sections
                        .firstOrNull()
                        .orEmpty()
                }

            /*
             * The housing form uses conditional field counting.
             *
             * Rent fields participate in the percentage only when
             * rent assistance was selected.
             */
            val progress =
                if (isHousingAssistanceForm) {
                    val relevantFieldsCount =
                        HousingAssistanceFormValidator
                            .countRelevantFields(
                                formData = savedFields
                            )

                    val filledRelevantFieldsCount =
                        HousingAssistanceFormValidator
                            .countFilledRelevantFields(
                                formData = savedFields
                            )

                    if (relevantFieldsCount == 0) {
                        0f
                    } else {
                        (
                                filledRelevantFieldsCount.toFloat() /
                                        relevantFieldsCount.toFloat()
                                ).coerceIn(
                                minimumValue = 0f,
                                maximumValue = 1f
                            )
                    }
                } else {
                    calculateFieldsProgress(
                        savedFields = savedFields,
                        requiredFields =
                            form.requiredFields
                    )
                }

            FormProgressItem(
                form = form,
                progress = progress,
                percentText =
                    roundedPercent(progress),

                status =
                    if (uid != null) {
                        getStatusByProgress(
                            context = context,
                            uid = uid,
                            formId = form.formId,
                            progress = progress
                        )
                    } else {
                        FormStatus.NOT_STARTED
                    },

                currentStep = currentStep,
                currentStepForDisplay =
                    currentStepForDisplay,
                totalStepsForDisplay =
                    totalStepsForDisplay,
                currentSectionName =
                    currentSectionName,

                lastUpdated =
                    if (uid != null) {
                        FormProgressStorage
                            .getLastUpdatedText(
                                context = context,
                                uid = uid,
                                formId = form.formId
                            )
                    } else {
                        "עדיין לא התחלת למלא את הטופס"
                    }
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "הטפסים שלי",
                style =
                    MaterialTheme.typography
                        .headlineLarge,
                color =
                    MaterialTheme.colorScheme
                        .onBackground
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "אפשר להמשיך מאיפה שעצרת פעם קודמת.\n" +
                            "ההתקדמות שלך נשמרת באופן אוטומטי.",

                style =
                    MaterialTheme.typography
                        .bodyLarge,

                color =
                    MaterialTheme.colorScheme
                        .onBackground
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }

        items(
            items = forms,
            key = { formProgress ->
                formProgress.form.formId
            }
        ) { formProgress ->

            FormProgressCard(
                formProgress = formProgress,

                onContinueClick = {
                    navController.navigate(
                        "${formProgress.form.route}/" +
                                formProgress.currentStep
                    )
                }
            )
        }
    }
}

fun calculateFieldsProgress(
    savedFields: Map<String, String>,
    requiredFields: List<String>
): Float {
    if (requiredFields.isEmpty()) {
        return 0f
    }

    val filledCount =
        requiredFields.count { key ->
            isSelectedOption(
                savedFields[key].orEmpty()
            )
        }

    return (
            filledCount.toFloat() /
                    requiredFields.size.toFloat()
            ).coerceIn(
            minimumValue = 0f,
            maximumValue = 1f
        )
}

fun roundedPercent(
    progress: Float
): Int {
    val exactPercent =
        progress.coerceIn(
            minimumValue = 0f,
            maximumValue = 1f
        ) * 100f

    return kotlin.math.floor(
        exactPercent.toDouble()
    ).toInt()
}

fun getStatusByProgress(
    context: Context,
    uid: String,
    formId: String,
    progress: Float
): FormStatus {
    if (
        FormProgressStorage.isCompleted(
            context = context,
            uid = uid,
            formId = formId
        )
    ) {
        return FormStatus.COMPLETED
    }

    if (progress <= 0f) {
        return FormStatus.NOT_STARTED
    }

    return FormStatus.IN_PROGRESS
}

@Composable
fun FormProgressCard(
    formProgress: FormProgressItem,
    onContinueClick: () -> Unit
) {
    val form = formProgress.form

    Card(
        modifier = Modifier.fillMaxWidth(),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 6.dp
            ),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface
            ),

        border = BorderStroke(
            width = 1.dp,
            color =
                MaterialTheme.colorScheme.secondary
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = form.title,

                        style =
                            MaterialTheme.typography
                                .titleLarge,

                        color =
                            MaterialTheme.colorScheme
                                .onSurface
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text = form.description,

                        style =
                            MaterialTheme.typography
                                .bodyMedium,

                        color =
                            MaterialTheme.colorScheme
                                .onSurface
                    )
                }

                FormStatusChip(
                    status = formProgress.status
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            /*
             * Uses the adjusted display values rather than the original
             * section index and original section count.
             */
            Text(
                text =
                    "שלב ${formProgress.currentStepForDisplay} " +
                            "מתוך ${formProgress.totalStepsForDisplay} - " +
                            formProgress.currentSectionName,

                style =
                    MaterialTheme.typography.bodyLarge,

                color =
                    MaterialTheme.colorScheme.onSurface
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "כמות שדות שכבר מולאו: " +
                            "${formProgress.percentText}%",

                style =
                    MaterialTheme.typography.bodyLarge,

                color =
                    MaterialTheme.colorScheme.onSurface
            )

            Text(
                text =
                    "שים/י לב, חלק מהשדות מולאו אוטומטית, " +
                            "מומלץ לעבור עליהם בהמשך",

                style =
                    MaterialTheme.typography.bodyLarge,

                color =
                    MaterialTheme.colorScheme.onSurface
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            LinearProgressIndicator(
                progress = {
                    formProgress.progress
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "התקדמות מילוי הטופס: " +
                                    "${formProgress.percentText}%"
                    },

                color =
                    MaterialTheme.colorScheme.secondary,

                trackColor =
                    MaterialTheme.colorScheme
                        .surfaceVariant
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = formProgress.lastUpdated,

                style =
                    MaterialTheme.typography
                        .bodySmall,

                color =
                    MaterialTheme.colorScheme
                        .onSurface
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            when (formProgress.status) {
                FormStatus.NOT_STARTED -> {
                    Button(
                        onClick = onContinueClick,
                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,

                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimary
                            )
                    ) {
                        Text("התחל מילוי")
                    }
                }

                FormStatus.IN_PROGRESS -> {
                    Button(
                        onClick = onContinueClick,
                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,

                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimary
                            )
                    ) {
                        Text("המשך מילוי")
                    }
                }

                FormStatus.COMPLETED -> {
                    OutlinedButton(
                        onClick = onContinueClick,
                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            ButtonDefaults
                                .outlinedButtonColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,

                                    contentColor =
                                        MaterialTheme
                                            .colorScheme
                                            .onPrimary
                                )
                    ) {
                        Text("צפייה בטופס")
                    }
                }
            }
        }
    }
}

@Composable
fun FormStatusChip(
    status: FormStatus
) {
    val statusText =
        when (status) {
            FormStatus.NOT_STARTED ->
                "לא התחיל"

            FormStatus.IN_PROGRESS ->
                "בתהליך"

            FormStatus.COMPLETED ->
                "הושלם"
        }

    val chipColor =
        when (status) {
            FormStatus.NOT_STARTED ->
                Color(0xFFE53935)

            FormStatus.IN_PROGRESS ->
                Color(0xFFFFC107)

            FormStatus.COMPLETED ->
                Color(0xFF4CAF50)
        }

    AssistChip(
        onClick = {},

        label = {
            Text(
                text = statusText,
                color =
                    MaterialTheme.colorScheme
                        .onPrimary
            )
        },

        colors =
            AssistChipDefaults
                .assistChipColors(
                    containerColor = chipColor
                )
    )
}

/**
 * Treats a saved checkbox value as selected unless it is empty or false.
 */
private fun isSelectedOption(
    value: String
): Boolean {
    return value.isNotBlank() &&
            !value.equals(
                other = "false",
                ignoreCase = true
            )
}