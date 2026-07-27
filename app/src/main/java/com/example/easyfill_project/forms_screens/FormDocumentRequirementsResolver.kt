package com.example.easyfill_project.forms_screens

object FormDocumentRequirementsResolver {

    /**
     * Returns the documents relevant to the current form selections.
     */
    fun getRelevantDocuments(
        formDefinition: FormDefinition,
        formData: Map<String, String>
    ): List<RequiredDocument> {
        return formDefinition
            .documentRequirementGroups
            .filter { group ->
                isGroupRelevant(
                    group = group,
                    formData = formData
                )
            }
            .flatMap { group ->
                group.documents
            }
            .distinctBy { document ->
                document.documentId
            }
    }

    /**
     * Checks whether the form contains any document requirements.
     */
    fun hasConfiguredDocuments(
        formDefinition: FormDefinition
    ): Boolean {
        return formDefinition
            .documentRequirementGroups
            .any { group ->
                group.documents.isNotEmpty()
            }
    }

    /**
     * Checks whether a document group applies to the current selections.
     */
    private fun isGroupRelevant(
        group: DocumentRequirementGroup,
        formData: Map<String, String>
    ): Boolean {
        val selectedFieldKey =
            group.showWhenSelectedKey
                ?: return true

        return isSelectedValue(
            formData[selectedFieldKey].orEmpty()
        )
    }

    /**
     * Checks whether a checkbox-style value represents a selection.
     */
    private fun isSelectedValue(
        value: String
    ): Boolean {
        return value.isNotBlank() &&
                !value.equals(
                    other = "false",
                    ignoreCase = true
                )
    }
}