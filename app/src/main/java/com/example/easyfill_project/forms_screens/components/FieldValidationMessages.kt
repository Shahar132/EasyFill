package com.example.easyfill_project.forms_screens.components

internal object FieldValidationMessages {

    // Returns the user-facing message for the provided validation error.
    fun getMessage(
        error: FieldInputRules.ValidationError
    ): String {
        return when (error) {
            FieldInputRules.ValidationError.NameTooShort ->
                "יש להזין לפחות שתי אותיות"

            FieldInputRules.ValidationError.IdLength ->
                "מספר תעודת זהות חייב להכיל 9 ספרות"

            FieldInputRules.ValidationError.InvalidIsraeliId ->
                "מספר תעודת הזהות שהוזן אינו תקין"

            FieldInputRules.ValidationError.PhoneLength ->
                "מספר טלפון נייד חייב להכיל 10 ספרות"

            FieldInputRules.ValidationError.InvalidMobilePhonePrefix ->
                "מספר טלפון נייד חייב להתחיל ב־05"

            FieldInputRules.ValidationError.ZipCodeLength ->
                "מיקוד חייב להכיל 7 ספרות"

            FieldInputRules.ValidationError.InvalidWholeNumber ->
                "יש להזין מספר שלם תקין"

            FieldInputRules.ValidationError.MustBePositiveNumber ->
                "יש להזין מספר גדול מ־0"

            FieldInputRules.ValidationError.MustBeNonNegativeNumber ->
                "יש להזין מספר שאינו שלילי"

            FieldInputRules.ValidationError.InvalidDecimalNumber ->
                "יש להזין מספר תקין"

            is FieldInputRules.ValidationError.TooManyDecimalPlaces ->
                "ניתן להזין עד ${error.maximumPlaces} ספרות אחרי הנקודה"

            FieldInputRules.ValidationError.InvalidChildrenAgesFormat ->
                "יש להפריד בין גילי הילדים בפסיקים, לדוגמה: 3, 7, 12"

            FieldInputRules.ValidationError.InvalidChildAge ->
                "כל גיל חייב להיות בין 0 ל־120"

            is FieldInputRules.ValidationError.ChildrenAgesCountMismatch ->
                "הוזנו גילאים עבור ${error.actualCount} ילדים, " +
                        "אך מספר הילדים הוא ${error.expectedCount}"

            FieldInputRules.ValidationError.AgesEnteredWithoutChildren ->
                "אין להזין גילי ילדים כאשר מספר הילדים הוא 0"

            FieldInputRules.ValidationError.InvalidEmail ->
                "כתובת הדואר האלקטרוני אינה תקינה"
        }
    }
}