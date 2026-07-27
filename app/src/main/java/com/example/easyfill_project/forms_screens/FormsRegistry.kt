package com.example.easyfill_project.forms_screens

// List of forms.
// List of sections.
// List of required fields and documents.
object FormsRegistry {

    val housingAssistanceSections = listOf(
        "פרטים אישיים",
        "כתובת למשלוח דואר",
        "מצב משפחתי",
        "פירוט הכנסות",
        "בחירת הסיוע בדיור",
        "סיוע בשכר דירה",
        "סיכום"
    )

    val forms = listOf(
        FormDefinition(
            formId = "housing_assistance",
            title = "טופס בקשה לסיוע בדיור",
            description =
                "טופס זה מיועד למימוש הזכאות למענקים " +
                        "והלוואות בתחום הדיור בנושאים האלה: " +
                        "סיוע בשכר דירה, התאמת דירה לנכות ועוד.",
            route = "housingAssistanceForm",
            sections = housingAssistanceSections,

            requiredFields = listOf(
                "lastName",
                "firstName",
                "idNumber",
                "street",
                "houseNumber",
                "city",
                "zipCode",
                "phone",
                "email",
                "mailingStreet",
                "mailingHouseNumber",
                "mailingCity",
                "mailingZipCode",
                "maritalStatus",
                "childrenCount",
                "workPlace",
                "salaryNet",
                "rentAssistance",
                "rentStreet",
                "rentHouseNumber",
                "rentCity",
                "rentZipCode",
                "roomsCount",
                "floor",
                "hasElevator"
            ),

            documentRequirementGroups = listOf(
                DocumentRequirementGroup(

                    // These documents are relevant only when
                    // rent assistance was selected.
                    showWhenSelectedKey = "rentAssistance",

                    documents = listOf(
                        RequiredDocument(
                            documentId = "signedRentalContract",
                            title = "חוזה שכירות חתום"
                        ),

                        RequiredDocument(
                            documentId = "propertyOwnershipDeclaration",
                            title =
                                "טופס הצהרת בעלות על נכס וקבלת סיוע " +
                                        "ממשרד הבינוי והשיכון"
                        ),

                        RequiredDocument(
                            documentId = "studyConfirmation",
                            title = "אישור לימודים",
                            note = "לסטודנטים בלבד",
                            isRequired = false,
                            canMarkNotRelevant = true
                        )
                    )
                )
            )
        )
    )

    fun getFormById(formId: String): FormDefinition {
        return forms.first { form ->
            form.formId == formId
        }
    }
}