package com.example.easyfill_project.forms_screens

data class RequiredDocument(
    val documentId: String,
    val title: String,
    val note: String? = null,
    val isRequired: Boolean = true,
    val canMarkNotRelevant: Boolean = false
)

data class DocumentRequirementGroup(

    // The documents are always relevant when this value is null.
    val showWhenSelectedKey: String? = null,

    val documents: List<RequiredDocument>
)

data class FormDefinition(
    val formId: String,
    val title: String,
    val description: String,
    val route: String,
    val sections: List<String>,
    val requiredFields: List<String>,

    // Defines the documents that may need to be attached to this form.
    val documentRequirementGroups:
    List<DocumentRequirementGroup> = emptyList()
)