package com.example.easyfill_project.forms_screens

data class FormDefinition(
    val formId: String,
    val title: String,
    val description: String,
    val route: String,
    val sections: List<String>,
    val requiredFields: List<String>
)