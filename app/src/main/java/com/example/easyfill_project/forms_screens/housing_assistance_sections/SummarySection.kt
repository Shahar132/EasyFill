package com.example.easyfill_project.forms_screens.housing_assistance_sections

import androidx.compose.runtime.Composable
import com.example.easyfill_project.forms_screens.FormDefinition
import com.example.easyfill_project.forms_screens.FormIssue
import com.example.easyfill_project.forms_screens.GeneralFormSummarySection
import com.example.easyfill_project.forms_screens.HousingAssistanceFormValidator
import com.example.easyfill_project.pdf_export.PdfExportManager

@Composable
fun SummarySection(
    formDefinition: FormDefinition,
    formData: Map<String, String>,

    // Sends all missing and invalid fields back to the main form screen.
    onValidationIssuesFound: (List<FormIssue>) -> Unit
) {
    GeneralFormSummarySection(
        formDefinition = formDefinition,
        formData = formData,

        validateForm = { currentFormData ->
            HousingAssistanceFormValidator.validateForm(
                formData = currentFormData
            )
        },

        createFormPdf = { context, currentFormData ->
            PdfExportManager.createHousingAssistancePdf(
                context = context.applicationContext,
                firebaseFields = currentFormData
            )
        },

        onValidationIssuesFound =
            onValidationIssuesFound
    )
}