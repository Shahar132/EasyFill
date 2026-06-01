package com.example.easyfill_project.forms_screens
//List of forms
//List of sections
//List of required fields
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
            description = "טופס זה מיועד למימוש הזכאות למענקים והלוואות בתחום הדיור בנושאים האלה: סיוע בשכר דירה, התאמת דירה לנכות ועוד.",
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
            )
        )
    )

    fun getFormById(formId: String): FormDefinition {
        return forms.first { it.formId == formId }
    }
}