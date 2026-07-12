package com.example.easyfill_project.forms_screens.housing_assistance_sections

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.pdf_export.FirebaseFormReader
import com.example.easyfill_project.pdf_export.PdfExportManager
import com.example.easyfill_project.pdf_export.PdfShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SummarySection(navController: NavHostController) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCreatingPdf by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(80.dp)
        )

        Text(
            text = "הטופס הושלם בהצלחה",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "ניתן לחזור אחורה כדי לערוך פרטים,\nאו ליצור קובץ PDF מהנתונים השמורים.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedButton(
            enabled = !isCreatingPdf,
            onClick = {
                isCreatingPdf = true

                FirebaseFormReader.loadSavedFormFields(
                    onResult = { result ->

                        result.onSuccess { firebaseFields ->

                            coroutineScope.launch {
                                try {
                                    val pdfFile = withContext(Dispatchers.IO) {
                                        PdfExportManager.createHousingAssistancePdf(
                                            context = context.applicationContext,
                                            firebaseFields = firebaseFields
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
                                            exception.message ?: "שגיאה לא ידועה"
                                        }",
                                        Toast.LENGTH_LONG
                                    ).show()

                                } finally {
                                    isCreatingPdf = false
                                }
                            }
                        }

                        result.onFailure { exception ->

                            isCreatingPdf = false

                            Toast.makeText(
                                context,
                                "קריאת הנתונים נכשלה: ${
                                    exception.message ?: "שגיאה לא ידועה"
                                }",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),

            border = BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.onBackground
            ),

            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {

            if (isCreatingPdf) {

                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "יוצר קובץ...",
                    color = MaterialTheme.colorScheme.onBackground
                )

            } else {

                Text(
                    text = "יצירת קובץ PDF",
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}