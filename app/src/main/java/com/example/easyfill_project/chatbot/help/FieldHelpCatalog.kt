package com.example.easyfill_project.chatbot.help

/**
 * Stores the spoken explanation for one form field.
 */
data class FieldHelp(
    // Must match the fieldId used inside SmartTextField.
    val fieldId: String,

    // Text that will be read aloud for this field.
    val explanation: String
)

/**
 * Contains field explanations for every housing-form step.
 *
 * The first field in each list is used when the user
 * has not focused a specific field yet.
 */
object FieldHelpCatalog {

    private val housingFieldsByStep: Map<Int, List<FieldHelp>> = mapOf(

        // Step 0: PersonalDetailsSection
        0 to listOf(
            FieldHelp(
                fieldId = "lastName",
                explanation = "שדה שם משפחה. יש להזין את שם המשפחה כפי שהוא מופיע בתעודת הזהות."
            ),
            FieldHelp(
                fieldId = "firstName",
                explanation = "שדה שם פרטי. יש להזין את השם הפרטי כפי שהוא מופיע בתעודת הזהות."
            ),
            FieldHelp(
                fieldId = "idNumber",
                explanation = "שדה מספר תעודת זהות. יש להזין מספר תעודת זהות תקין."
            ),
            FieldHelp(
                fieldId = "street",
                explanation = "שדה רחוב. יש להזין את שם הרחוב בכתובת המגורים."
            ),
            FieldHelp(
                fieldId = "houseNumber",
                explanation = "שדה מספר בית. יש להזין את מספר הבית בכתובת המגורים."
            ),
            FieldHelp(
                fieldId = "city",
                explanation = "שדה יישוב. יש להזין את שם העיר או היישוב."
            ),
            FieldHelp(
                fieldId = "zipCode",
                explanation = "שדה מיקוד. יש להזין את המיקוד של כתובת המגורים."
            ),
            FieldHelp(
                fieldId = "phone",
                explanation = "שדה טלפון נייד. יש להזין מספר טלפון זמין ליצירת קשר."
            ),
            FieldHelp(
                fieldId = "email",
                explanation = "שדה דואר אלקטרוני. יש להזין כתובת דואר אלקטרוני תקינה."
            )
        ),

        // Step 1: MailingAddressSection
        1 to listOf(
            FieldHelp(
                fieldId = "mailingStreet",
                explanation = "שדה רחוב או תא דואר. יש להזין את הכתובת שאליה תרצו לקבל דואר."
            ),
            FieldHelp(
                fieldId = "mailingHouseNumber",
                explanation = "שדה מספר בית בכתובת למשלוח דואר."
            ),
            FieldHelp(
                fieldId = "mailingEntrance",
                explanation = "שדה כניסה. יש להזין את אות או מספר הכניסה, אם קיימים."
            ),
            FieldHelp(
                fieldId = "mailingApartment",
                explanation = "שדה דירה. יש להזין את מספר הדירה, אם קיים."
            ),
            FieldHelp(
                fieldId = "mailingCity",
                explanation = "שדה יישוב למשלוח דואר. יש להזין את שם העיר או היישוב."
            ),
            FieldHelp(
                fieldId = "mailingZipCode",
                explanation = "שדה מיקוד למשלוח דואר. יש להזין את המיקוד המתאים לכתובת."
            )
        ),

        // Step 2: FamilyStatusSection
        2 to listOf(
            FieldHelp(
                fieldId = "childrenCount",
                explanation = "שדה מספר הילדים. יש להזין את מספר הילדים במשפחה."
            ),
            FieldHelp(
                fieldId = "childrenAges",
                explanation = "שדה גיל הילדים. יש להזין את גילי הילדים, מופרדים בפסיקים."
            )
        ),

        // Step 3: IncomeDetailsSection
        3 to listOf(
            FieldHelp(
                fieldId = "workPlace",
                explanation = "שדה מקום העבודה. יש להזין את שם מקום העבודה הנוכחי."
            ),
            FieldHelp(
                fieldId = "salaryNet",
                explanation = "שדה השכר שלך נטו. יש להזין את ההכנסה החודשית לאחר ניכויים."
            ),
            FieldHelp(
                fieldId = "partnerWorkPlace",
                explanation = "שדה מקום העבודה של בן או בת הזוג."
            ),
            FieldHelp(
                fieldId = "partnerSalaryNet",
                explanation = "שדה שכר בן או בת הזוג נטו. יש להזין את ההכנסה החודשית לאחר ניכויים."
            ),
            FieldHelp(
                fieldId = "additionalIncomeDetails",
                explanation = "שדה הכנסות נוספות. יש לפרט הכנסות נוספות, אם קיימות."
            )
        ),

        // Step 4: AssistanceSelectionSection
        // This section contains checkboxes and no SmartTextField.
        // Therefore the first explanation describes the whole section.
        4 to listOf(
            FieldHelp(
                fieldId = "assistanceSelection",
                explanation = """
                    בחלק זה יש לבחור את סוגי הסיוע בדיור המבוקשים.
                    ניתן לבחור יותר מאפשרות אחת.
                    לדוגמה: סיוע בשכר דירה, התאמת דירה,
                    החלפת דירה, בניית בית או הלוואה.
                """.trimIndent()
            )
        ),

        // Step 5: RentAssistanceSection
        5 to listOf(
            FieldHelp(
                fieldId = "rentStreet",
                explanation = "שדה רחוב של הדירה השכורה. יש להזין את שם הרחוב."
            ),
            FieldHelp(
                fieldId = "rentHouseNumber",
                explanation = "שדה מספר הבית של הדירה השכורה."
            ),
            FieldHelp(
                fieldId = "rentEntrance",
                explanation = "שדה כניסה של הדירה השכורה, אם קיימת."
            ),
            FieldHelp(
                fieldId = "rentApartment",
                explanation = "שדה מספר הדירה השכורה."
            ),
            FieldHelp(
                fieldId = "rentCity",
                explanation = "שדה יישוב של הדירה השכורה."
            ),
            FieldHelp(
                fieldId = "rentZipCode",
                explanation = "שדה מיקוד של הדירה השכורה."
            ),
            FieldHelp(
                fieldId = "roomsCount",
                explanation = "שדה מספר חדרים. יש להזין את מספר החדרים בדירה."
            ),
            FieldHelp(
                fieldId = "floor",
                explanation = "שדה קומה. יש להזין את הקומה שבה נמצאת הדירה."
            )
        ),

        // Step 6: SummarySection
        6 to listOf(
            FieldHelp(
                fieldId = "summary",
                explanation = """
                    זהו שלב הסיכום.
                    יש לעבור על הפרטים שמילאתם ולבדוק שהם נכונים.
                    ניתן לחזור לשלבים הקודמים כדי לתקן מידע.
                """.trimIndent()
            )
        )
    )

    /**
     * Returns the explanation for the focused field.
     *
     * Returns null when the field is not found in the current step.
     */
    fun getFieldExplanation(
        step: Int,
        fieldId: String
    ): String? {
        return housingFieldsByStep[step]
            ?.firstOrNull { fieldHelp ->
                fieldHelp.fieldId == fieldId
            }
            ?.explanation
    }

    /**
     * Returns the first explanation in the current step.
     *
     * This is used when the user has not selected a field yet.
     */
    fun getFirstFieldExplanation(
        step: Int
    ): String {
        return housingFieldsByStep[step]
            ?.firstOrNull()
            ?.explanation
            ?: "לא נמצא שדה שניתן להקריא בשלב הנוכחי."
    }
}