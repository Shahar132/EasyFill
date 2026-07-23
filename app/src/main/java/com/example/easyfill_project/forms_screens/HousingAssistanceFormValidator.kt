package com.example.easyfill_project.forms_screens

import com.example.easyfill_project.forms_screens.components.FieldInputRules
import com.example.easyfill_project.forms_screens.components.FieldValidationMessages
import com.example.easyfill_project.pdf_export.HousingAssistancePdfSchema
import com.example.easyfill_project.pdf_export.PdfFieldDefinition
import com.example.easyfill_project.pdf_export.PdfFieldDisplayType
import com.example.easyfill_project.pdf_export.PdfSectionDefinition

enum class FormIssueType {
    MISSING,
    INVALID
}

data class FormIssue(
    val fieldId: String,
    val displayName: String,
    val sectionIndex: Int,
    val issueType: FormIssueType,
    val message: String
)

object HousingAssistanceFormValidator {

    const val ASSISTANCE_SELECTION_FIELD_ID =
        "assistanceSelection"

    private const val CHILDREN_COUNT_FIELD_ID =
        "childrenCount"

    private const val CHILDREN_AGES_FIELD_ID =
        "childrenAges"

    private const val MARITAL_STATUS_FIELD_ID =
        "maritalStatus"

    private val partnerFieldIds = setOf(
        "partnerWorkPlace",
        "partnerSalaryNet"
    )

    private val partnerRelevantStatuses = setOf(
        "נשוי",
        "ידוע בציבור"
    )

    /**
     * Validates all relevant form fields in their original form order.
     */
    fun validateForm(
        formData: Map<String, String>
    ): List<FormIssue> {
        val issues = mutableListOf<FormIssue>()

        HousingAssistancePdfSchema.sections
            .forEachIndexed { sectionIndex, section ->

                if (!isSectionRelevant(section, formData)) {
                    return@forEachIndexed
                }

                validateSection(
                    section = section,
                    sectionIndex = sectionIndex,
                    formData = formData,
                    issues = issues
                )
            }

        return issues
    }


    // Counts all currently relevant logical fields in the form.
    fun countRelevantFields(
        formData: Map<String, String>
    ): Int {
        var relevantFieldsCount = 0

        HousingAssistancePdfSchema.sections.forEach { section ->

            if (!isSectionRelevant(section, formData)) {
                return@forEach
            }

            val containsSelectionOptions =
                section.fields.any { field ->
                    field.displayType ==
                            PdfFieldDisplayType.SELECTED_OPTION
                }

            if (containsSelectionOptions) {
                // All assistance checkboxes are treated as one logical field.
                relevantFieldsCount++
            }

            relevantFieldsCount +=
                section.fields.count { field ->
                    field.displayType ==
                            PdfFieldDisplayType.TEXT &&
                            isFieldRelevant(
                                fieldId = field.firebaseKey,
                                formData = formData
                            )
                }
        }

        return relevantFieldsCount
    }

    /**
     * Validates one form section and appends its issues in field order.
     */
    private fun validateSection(
        section: PdfSectionDefinition,
        sectionIndex: Int,
        formData: Map<String, String>,
        issues: MutableList<FormIssue>
    ) {
        val selectedOptionFields = section.fields.filter { field ->
            field.displayType ==
                    PdfFieldDisplayType.SELECTED_OPTION
        }

        if (selectedOptionFields.isNotEmpty()) {
            validateAssistanceSelection(
                fields = selectedOptionFields,
                sectionIndex = sectionIndex,
                formData = formData,
                issues = issues
            )
        }

        section.fields
            .filter { field ->
                field.displayType == PdfFieldDisplayType.TEXT
            }
            .forEach { field ->

                if (!isFieldRelevant(field.firebaseKey, formData)) {
                    return@forEach
                }

                validateTextField(
                    field = field,
                    sectionIndex = sectionIndex,
                    formData = formData,
                    issues = issues
                )
            }
    }

    /**
     * Validates one text field for missing and invalid values.
     */
    private fun validateTextField(
        field: PdfFieldDefinition,
        sectionIndex: Int,
        formData: Map<String, String>,
        issues: MutableList<FormIssue>
    ) {
        val fieldId = field.firebaseKey
        val value = formData[fieldId].orEmpty()

        if (value.isBlank()) {
            issues.add(
                FormIssue(
                    fieldId = fieldId,
                    displayName = field.displayName,
                    sectionIndex = sectionIndex,
                    issueType = FormIssueType.MISSING,
                    message =
                        FieldValidationMessages
                            .getMissingFieldMessage(
                                field.displayName
                            )
                )
            )

            return
        }

        val validationError =
            FieldInputRules.validate(
                fieldId = fieldId,
                value = value
            ) ?: getCrossFieldValidationError(
                fieldId = fieldId,
                formData = formData
            )

        if (validationError != null) {
            issues.add(
                FormIssue(
                    fieldId = fieldId,
                    displayName = field.displayName,
                    sectionIndex = sectionIndex,
                    issueType = FormIssueType.INVALID,
                    message =
                        FieldValidationMessages
                            .getMessage(validationError)
                )
            )
        }
    }

    /**
     * Validates that at least one housing-assistance option is selected.
     */
    private fun validateAssistanceSelection(
        fields: List<PdfFieldDefinition>,
        sectionIndex: Int,
        formData: Map<String, String>,
        issues: MutableList<FormIssue>
    ) {
        val hasSelectedOption = fields.any { field ->
            isSelectedOption(
                formData[field.firebaseKey].orEmpty()
            )
        }

        if (!hasSelectedOption) {
            issues.add(
                FormIssue(
                    fieldId =
                        ASSISTANCE_SELECTION_FIELD_ID,
                    displayName =
                        "סוג הסיוע המבוקש",
                    sectionIndex = sectionIndex,
                    issueType = FormIssueType.MISSING,
                    message =
                        FieldValidationMessages
                            .getMissingAssistanceSelectionMessage()
                )
            )
        }
    }

    /**
     * Runs validation rules that depend on values from multiple fields.
     */
    private fun getCrossFieldValidationError(
        fieldId: String,
        formData: Map<String, String>
    ): FieldInputRules.ValidationError? {
        if (fieldId != CHILDREN_AGES_FIELD_ID) {
            return null
        }

        return FieldInputRules.validateChildrenAgesCount(
            childrenCountValue =
                formData[CHILDREN_COUNT_FIELD_ID]
                    .orEmpty(),

            childrenAgesValue =
                formData[CHILDREN_AGES_FIELD_ID]
                    .orEmpty()
        )
    }

    /**
     * Checks whether a conditional section is relevant to the user.
     */
    private fun isSectionRelevant(
        section: PdfSectionDefinition,
        formData: Map<String, String>
    ): Boolean {
        val selectedKey =
            section.showWhenSelectedKey
                ?: return true

        return isSelectedOption(
            formData[selectedKey].orEmpty()
        )
    }

    /**
     * Checks whether a field should participate in form validation.
     */
    private fun isFieldRelevant(
        fieldId: String,
        formData: Map<String, String>
    ): Boolean {
        if (fieldId == CHILDREN_AGES_FIELD_ID) {
            val childrenCount =
                formData[CHILDREN_COUNT_FIELD_ID]
                    .orEmpty()
                    .toIntOrNull()

            return childrenCount != null &&
                    childrenCount > 0
        }

        if (fieldId in partnerFieldIds) {
            val maritalStatus =
                formData[MARITAL_STATUS_FIELD_ID]
                    .orEmpty()

            return maritalStatus in partnerRelevantStatuses
        }

        return true
    }

    /**
     * Checks whether a checkbox-style option is currently selected.
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
}