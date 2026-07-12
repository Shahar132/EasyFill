package com.example.easyfill_project.pdf_export

enum class PdfFieldDisplayType {
    TEXT,
    SELECTED_OPTION
}

data class PdfFieldDefinition(
    val firebaseKey: String,
    val displayName: String,
    val displayType: PdfFieldDisplayType = PdfFieldDisplayType.TEXT,
    val isRequired: Boolean = false
)

data class PdfSectionDefinition(
    val title: String,
    val fields: List<PdfFieldDefinition>,
    val showWhenSelectedKey: String? = null
)

object HousingAssistancePdfSchema {

    val sections = listOf(

        PdfSectionDefinition(
            title = "פרטים אישיים",
            fields = listOf(
                PdfFieldDefinition(
                    firebaseKey = "lastName",
                    displayName = "שם משפחה",
                    isRequired = true
                ),
                PdfFieldDefinition(
                    firebaseKey = "firstName",
                    displayName = "שם פרטי",
                    isRequired = true
                ),
                PdfFieldDefinition(
                    firebaseKey = "idNumber",
                    displayName = "מספר תעודת זהות",
                    isRequired = true
                ),
                PdfFieldDefinition(
                    firebaseKey = "street",
                    displayName = "רחוב"
                ),
                PdfFieldDefinition(
                    firebaseKey = "houseNumber",
                    displayName = "מספר בית"
                ),
                PdfFieldDefinition(
                    firebaseKey = "city",
                    displayName = "יישוב"
                ),
                PdfFieldDefinition(
                    firebaseKey = "zipCode",
                    displayName = "מיקוד"
                ),
                PdfFieldDefinition(
                    firebaseKey = "phone",
                    displayName = "טלפון נייד"
                ),
                PdfFieldDefinition(
                    firebaseKey = "email",
                    displayName = "דואר אלקטרוני"
                )
            )
        ),

        PdfSectionDefinition(
            title = "כתובת למשלוח דואר",
            fields = listOf(
                PdfFieldDefinition(
                    firebaseKey = "mailingStreet",
                    displayName = "רחוב/תא דואר"
                ),
                PdfFieldDefinition(
                    firebaseKey = "mailingHouseNumber",
                    displayName = "מספר בית"
                ),
                PdfFieldDefinition(
                    firebaseKey = "mailingEntrance",
                    displayName = "כניסה"
                ),
                PdfFieldDefinition(
                    firebaseKey = "mailingApartment",
                    displayName = "דירה"
                ),
                PdfFieldDefinition(
                    firebaseKey = "mailingCity",
                    displayName = "יישוב"
                ),
                PdfFieldDefinition(
                    firebaseKey = "mailingZipCode",
                    displayName = "מיקוד"
                )
            )
        ),
        PdfSectionDefinition(
            title = "מצב משפחתי",
            fields = listOf(
                PdfFieldDefinition(
                    firebaseKey = "maritalStatus",
                    displayName = "מצב משפחתי"
                ),
                PdfFieldDefinition(
                    firebaseKey = "childrenCount",
                    displayName = "מספר הילדים"
                ),
                PdfFieldDefinition(
                    firebaseKey = "childrenAges",
                    displayName = "גיל הילדים"
                )
            )
        ),
        PdfSectionDefinition(
            title = "פירוט הכנסות",
            fields = listOf(
                PdfFieldDefinition(
                    firebaseKey = "workPlace",
                    displayName = "מקום העבודה"
                ),
                PdfFieldDefinition(
                    firebaseKey = "salaryNet",
                    displayName = "השכר שלך נטו"
                ),
                PdfFieldDefinition(
                    firebaseKey = "partnerWorkPlace",
                    displayName = "מקום העבודה של בן/בת הזוג"
                ),
                PdfFieldDefinition(
                    firebaseKey = "partnerSalaryNet",
                    displayName = "שכר בן/בת הזוג נטו"
                ),
                PdfFieldDefinition(
                    firebaseKey = "additionalIncomeDetails",
                    displayName = "פירוט הכנסות נוספות"
                )
            )
        ),
        PdfSectionDefinition(
            title = "בחירת הסיוע בדיור",
            fields = listOf(
                PdfFieldDefinition(
                    firebaseKey = "rentAssistance",
                    displayName = "א׳ - סיוע בשכר דירה",
                    displayType = PdfFieldDisplayType.SELECTED_OPTION
                ),
                PdfFieldDefinition(
                    firebaseKey = "apartmentAdaptation",
                    displayName = "ב׳ - התאמת דירה לנכות",
                    displayType = PdfFieldDisplayType.SELECTED_OPTION
                ),
                PdfFieldDefinition(
                    firebaseKey = "apartmentExchange",
                    displayName = "ג׳ - החלפת דירה",
                    displayType = PdfFieldDisplayType.SELECTED_OPTION
                ),
                PdfFieldDefinition(
                    firebaseKey = "houseBuilding",
                    displayName = "ד׳ - בניית בית",
                    displayType = PdfFieldDisplayType.SELECTED_OPTION
                ),
                PdfFieldDefinition(
                    firebaseKey = "firstApartmentPurchase",
                    displayName = "ה׳ - רכישת דירה ראשונה",
                    displayType = PdfFieldDisplayType.SELECTED_OPTION
                ),
                PdfFieldDefinition(
                    firebaseKey = "apartmentRenovationLoan",
                    displayName = "ו׳ - הלוואה לשיפוץ דירה",
                    displayType = PdfFieldDisplayType.SELECTED_OPTION
                ),
                PdfFieldDefinition(
                    firebaseKey = "firstMortgageAid",
                    displayName = "ז׳ - הלוואה לסידור ראשון",
                    displayType = PdfFieldDisplayType.SELECTED_OPTION
                )
            )
        ),
        PdfSectionDefinition(
            title = "פרטי הדירה בשכירות",
            fields = listOf(
                PdfFieldDefinition(
                    firebaseKey = "rentStreet",
                    displayName = "רחוב"
                ),
                PdfFieldDefinition(
                    firebaseKey = "rentHouseNumber",
                    displayName = "מספר בית"
                ),
                PdfFieldDefinition(
                    firebaseKey = "rentEntrance",
                    displayName = "כניסה"
                ),
                PdfFieldDefinition(
                    firebaseKey = "rentApartment",
                    displayName = "דירה"
                ),
                PdfFieldDefinition(
                    firebaseKey = "rentCity",
                    displayName = "יישוב"
                ),
                PdfFieldDefinition(
                    firebaseKey = "rentZipCode",
                    displayName = "מיקוד"
                ),
                PdfFieldDefinition(
                    firebaseKey = "roomsCount",
                    displayName = "מספר חדרים"
                ),
                PdfFieldDefinition(
                    firebaseKey = "floor",
                    displayName = "קומה"
                ),
                PdfFieldDefinition(
                    firebaseKey = "hasElevator",
                    displayName = "מעלית"
                )
            )
        )
    )
}