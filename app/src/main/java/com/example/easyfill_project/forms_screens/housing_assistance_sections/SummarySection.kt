package com.example.easyfill_project.forms_screens.housing_assistance_sections

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.FormIssue
import com.example.easyfill_project.forms_screens.FormIssueType
import com.example.easyfill_project.forms_screens.HousingAssistanceFormValidator
import com.example.easyfill_project.pdf_export.PdfExportManager
import com.example.easyfill_project.pdf_export.PdfShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SummarySection(
    formData: Map<String, String>,

    // Sends all missing and invalid fields back to the main form screen.
    onValidationIssuesFound: (List<FormIssue>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCreatingPdf by remember {
        mutableStateOf(false)
    }

    // Stores the issues until the user chooses to return to the form.
    var pendingValidationIssues by remember {
        mutableStateOf<List<FormIssue>>(emptyList())
    }

    var showValidationDialog by remember {
        mutableStateOf(false)
    }

    val missingFieldsCount =
        pendingValidationIssues.count { issue ->
            issue.issueType == FormIssueType.MISSING
        }

    val invalidFieldsCount =
        pendingValidationIssues.count { issue ->
            issue.issueType == FormIssueType.INVALID
        }

    // Creates a calm summary of the detected form issues.
    val validationSummary = when {
        missingFieldsCount > 0 &&
                invalidFieldsCount > 0 -> {

            "נמצאו $missingFieldsCount שדות חסרים " +
                    "ו־$invalidFieldsCount שדות שאינם תקינים."
        }

        missingFieldsCount > 0 -> {
            "נמצאו $missingFieldsCount שדות חסרים."
        }

        invalidFieldsCount > 0 -> {
            "נמצאו $invalidFieldsCount שדות שאינם תקינים."
        }

        else -> {
            "נמצאו שדות שדורשים בדיקה."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 28.dp,
                bottom = 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 22.dp,
                        vertical = 28.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "יצירת מסמך PDF",
                        modifier = Modifier.size(46.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }

                Text(
                    text = "בדיקת הטופס ויצירת קובץ",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text =
                        "לפני יצירת הקובץ המערכת תבדוק שכל השדות " +
                                "הנדרשים מולאו ושהפרטים שהוזנו תקינים.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Button(
                    enabled = !isCreatingPdf,

                    onClick = {
                        val formIssues =
                            HousingAssistanceFormValidator.validateForm(
                                formData = formData
                            )

                        if (formIssues.isNotEmpty()) {
                            pendingValidationIssues = formIssues
                            showValidationDialog = true
                            return@Button
                        }

                        isCreatingPdf = true

                        coroutineScope.launch {
                            try {
                                val pdfFile =
                                    withContext(Dispatchers.IO) {
                                        PdfExportManager
                                            .createHousingAssistancePdf(
                                                context =
                                                    context.applicationContext,
                                                firebaseFields = formData
                                            )
                                    }

                                PdfShareManager.openPdf(
                                    context = context,
                                    pdfFile = pdfFile
                                )

                            } catch (exception: Exception) {
                                Toast.makeText(
                                    context,
                                    "יצירת הקובץ נכשלה: ${
                                        exception.message
                                            ?: "שגיאה לא ידועה"
                                    }",
                                    Toast.LENGTH_LONG
                                ).show()

                            } finally {
                                isCreatingPdf = false
                            }
                        }
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),

                    shape = RoundedCornerShape(18.dp),

                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            MaterialTheme.colorScheme.secondary,

                        contentColor =
                            MaterialTheme.colorScheme.onSecondary,

                        disabledContainerColor =
                            MaterialTheme.colorScheme.secondary
                                .copy(alpha = 0.55f),

                        disabledContentColor =
                            MaterialTheme.colorScheme.onSecondary
                                .copy(alpha = 0.7f)
                    )
                ) {
                    if (isCreatingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color =
                                MaterialTheme.colorScheme.onSecondary
                        )

                        Spacer(
                            modifier = Modifier.width(10.dp)
                        )

                        Text(
                            text = "יוצר קובץ...",
                            style =
                                MaterialTheme.typography.labelLarge
                        )

                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(10.dp)
                        )

                        Text(
                            text = "יצירת קובץ PDF",
                            style =
                                MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    if (showValidationDialog) {
        AlertDialog(
            onDismissRequest = {
                showValidationDialog = false
            },

            // Uses the colors selected by the user.
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor =
                MaterialTheme.colorScheme.onBackground,
            textContentColor =
                MaterialTheme.colorScheme.onBackground,
            iconContentColor =
                MaterialTheme.colorScheme.onSurface,

            title = {
                Text(
                    text = "נדרשת השלמת שדות",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },

            text = {
                Text(
                    text =
                        "$validationSummary\n\n" +
                                "אפשר לעבור לטופס ולתקן " +
                                "את השדות המסומנים.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },

            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    // Keeps the buttons on opposite physical sides.
                    horizontalArrangement =
                        Arrangement.Absolute.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            showValidationDialog = false

                            // Marks the issues and navigates to
                            // the section containing the first one.
                            onValidationIssuesFound(
                                pendingValidationIssues
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color =
                                MaterialTheme.colorScheme.onSecondary
                        ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.secondary,

                                contentColor =
                                    MaterialTheme.colorScheme.onSecondary
                            )
                    ) {
                        Text(
                            text = "מעבר לשדות",
                            style =
                                MaterialTheme.typography.labelLarge,
                            color =
                                MaterialTheme.colorScheme.onSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showValidationDialog = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color =
                                MaterialTheme.colorScheme.onSecondary
                        ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.secondary,

                                contentColor =
                                    MaterialTheme.colorScheme.onSecondary
                            )
                    ) {
                        Text(
                            text = "סגור",
                            style =
                                MaterialTheme.typography.labelLarge,
                            color =
                                MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }
        )
    }
}