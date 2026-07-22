package com.example.easyfill_project.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.forms_screens.FormDefinition
import com.example.easyfill_project.forms_screens.FormProgressStorage
import com.example.easyfill_project.forms_screens.FormStatus
import com.example.easyfill_project.forms_screens.FormsRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class FormProgressItem(
    val form: FormDefinition,
    val progress: Float,
    val percentText: Int,
    val status: FormStatus,
    val currentStep: Int,
    val lastUpdated: String
)

@Composable
fun MyFormsProgressScreen(navController: NavHostController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var savedFields by remember {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect

        db.collection("users")
            .document(uid)
            .collection("savedUpdatedData")
            .document("allFields")
            .get()
            .addOnSuccessListener { doc ->
                savedFields = doc.data
                    ?.mapValues { it.value?.toString().orEmpty() }
                    ?: emptyMap()
            }
    }

    val forms = FormsRegistry.forms.map { form ->

        val currentStep = if (uid != null) {
            FormProgressStorage
                .getCurrentStep(context, uid, form.formId)
                .coerceIn(0, form.sections.lastIndex)
        } else {
            0
        }

        val progress = calculateFieldsProgress(
            savedFields = savedFields,
            requiredFields = form.requiredFields
        )

        FormProgressItem(
            form = form,
            progress = progress,
            percentText = roundedPercent(progress),
            status = if (uid != null) {
                getStatusByProgress(
                    context,
                    uid,
                    form.formId,
                    progress
                )
            } else {
                FormStatus.NOT_STARTED
            },
            currentStep = currentStep,
            lastUpdated = if (uid != null) {
                FormProgressStorage.getLastUpdatedText(
                    context,
                    uid,
                    form.formId
                )
            } else {
                "עדיין לא התחלת למלא את הטופס"
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "הטפסים שלי",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "אפשר להמשיך מאיפה שעצרת פעם קודמת.\nההתקדמות שלך נשמרת באופן אוטומטי.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        items(forms) { formProgress ->
            FormProgressCard(
                formProgress = formProgress,
                onContinueClick = {
                    navController.navigate(
                        "${formProgress.form.route}/${formProgress.currentStep}"
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
    if (requiredFields.isEmpty()) return 0f

    val filledCount = requiredFields.count { key ->
        !savedFields[key].isNullOrBlank() &&
                savedFields[key] != "false"
    }

    return filledCount / requiredFields.size.toFloat()
}

fun roundedPercent(progress: Float): Int {
    val rawPercent = (progress * 100).toInt()
    return ((rawPercent + 2) / 5) * 5
}

fun getStatusByProgress(
    context: android.content.Context,
    uid: String,
    formId: String,
    progress: Float
): FormStatus {
    if (FormProgressStorage.isCompleted(context, uid, formId)) {
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
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = form.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = form.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FormStatusChip(
                    status = formProgress.status
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val totalSteps = form.sections.size
            val currentStepForDisplay = formProgress.currentStep + 1
            val currentSectionName =
                form.sections[formProgress.currentStep]

            Text(
                text = "שלב $currentStepForDisplay מתוך $totalSteps - $currentSectionName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "כמות שדות שכבר מולאו: ${formProgress.percentText}%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "שים/י לב, חלק מהשדות מולאו אוטומטית, מומלץ לעבור עליהם בהמשך",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = {
                    formProgress.progress
                },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = formProgress.lastUpdated,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (formProgress.status) {
                FormStatus.NOT_STARTED -> {
                    Button(
                        onClick = onContinueClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("התחל מילוי")
                    }
                }

                FormStatus.IN_PROGRESS -> {
                    Button(
                        onClick = onContinueClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("המשך מילוי")
                    }
                }

                FormStatus.COMPLETED -> {
                    OutlinedButton(
                        onClick = onContinueClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
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
fun FormStatusChip(status: FormStatus) {
    val statusText = when (status) {
        FormStatus.NOT_STARTED -> "לא התחיל"
        FormStatus.IN_PROGRESS -> "בתהליך"
        FormStatus.COMPLETED -> "הושלם"
    }

    val chipColor = when (status) {
        FormStatus.NOT_STARTED -> Color(0xFFE53935)
        FormStatus.IN_PROGRESS -> Color(0xFFFFC107)
        FormStatus.COMPLETED -> Color(0xFF4CAF50)
    }

    AssistChip(
        onClick = {},
        label = {
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onPrimary
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = chipColor
        )
    )
}