package com.example.easyfill_project.forms_screens.components

import android.util.Patterns
import androidx.compose.ui.text.input.KeyboardType

internal object FieldInputRules {

    sealed class ValidationError {

        object NameTooShort : ValidationError()

        object IdLength : ValidationError()
        object InvalidIsraeliId : ValidationError()

        object PhoneLength : ValidationError()
        object InvalidMobilePhonePrefix : ValidationError()

        object ZipCodeLength : ValidationError()

        object InvalidWholeNumber : ValidationError()
        object MustBePositiveNumber : ValidationError()
        object MustBeNonNegativeNumber : ValidationError()

        object InvalidDecimalNumber : ValidationError()

        data class TooManyDecimalPlaces(
            val maximumPlaces: Int
        ) : ValidationError()

        object InvalidChildrenAgesFormat : ValidationError()
        object InvalidChildAge : ValidationError()

        data class ChildrenAgesCountMismatch(
            val expectedCount: Int,
            val actualCount: Int
        ) : ValidationError()

        object AgesEnteredWithoutChildren : ValidationError()

        object InvalidEmail : ValidationError()
    }

    private enum class InputKind {
        FREE_TEXT,
        TEXT_WITHOUT_DIGITS,
        NAME,
        DIGITS,
        DECIMAL,
        PHONE,
        EMAIL,
        CHILDREN_AGES
    }

    private data class FieldRule(
        val inputKind: InputKind,
        val keyboardType: KeyboardType,
        val maxLength: Int? = null,
        val validator: (String) -> ValidationError? = { null }
    )

    private val defaultRule = FieldRule(
        inputKind = InputKind.FREE_TEXT,
        keyboardType = KeyboardType.Text
    )

    private val fieldRules = mapOf(

        // Street names
        "street" to textWithoutDigitsRule(),
        "rentStreet" to textWithoutDigitsRule(),

        // Names
        "firstName" to nameRule(),
        "lastName" to nameRule(),

        // Israeli ID number
        "idNumber" to FieldRule(
            inputKind = InputKind.DIGITS,
            keyboardType = KeyboardType.Number,
            maxLength = 9,
            validator = ::validateIsraeliId
        ),

        // Mobile phone
        "phone" to FieldRule(
            inputKind = InputKind.PHONE,
            keyboardType = KeyboardType.Phone,
            maxLength = 10,
            validator = ::validateMobilePhone
        ),

        // Zip codes
        "zipCode" to zipCodeRule(),
        "mailingZipCode" to zipCodeRule(),
        "rentZipCode" to zipCodeRule(),

        // Positive whole numbers
        "houseNumber" to positiveWholeNumberRule(),
        "mailingHouseNumber" to positiveWholeNumberRule(),
        "rentHouseNumber" to positiveWholeNumberRule(),
        "mailingApartment" to positiveWholeNumberRule(),
        "rentApartment" to positiveWholeNumberRule(),

        // Whole numbers that may contain zero
        "floor" to nonNegativeWholeNumberRule(),
        "childrenCount" to nonNegativeWholeNumberRule(),

        // Money values
        "salaryNet" to moneyRule(),
        "partnerSalaryNet" to moneyRule(),

        // Room count, for example 3 or 3.5
        "roomsCount" to FieldRule(
            inputKind = InputKind.DECIMAL,
            keyboardType = KeyboardType.Decimal,
            validator = { value ->
                validatePositiveDecimal(
                    value = value,
                    maximumDecimalPlaces = 1
                )
            }
        ),

        // Children ages, for example: 3, 7, 12
        "childrenAges" to FieldRule(
            inputKind = InputKind.CHILDREN_AGES,
            keyboardType = KeyboardType.Decimal,
            validator = ::validateChildrenAges
        ),

        // Email
        "email" to FieldRule(
            inputKind = InputKind.EMAIL,
            keyboardType = KeyboardType.Email,
            validator = ::validateEmail
        )
    )

    /**
     * Returns the keyboard type configured for the requested field.
     */
    fun getKeyboardType(fieldId: String): KeyboardType {
        return getRule(fieldId).keyboardType
    }

    /**
     * Sanitizes typed or pasted input according to the field configuration.
     */
    fun sanitizeTypedInput(
        fieldId: String,
        input: String
    ): String {
        val rule = getRule(fieldId)

        val sanitizedInput = when (rule.inputKind) {
            InputKind.NAME -> sanitizeName(input)

            InputKind.TEXT_WITHOUT_DIGITS ->
                input.filterNot { character ->
                    character.isDigit()
                }

            InputKind.DIGITS,
            InputKind.PHONE ->
                input.filter { character ->
                    character.isDigit()
                }

            InputKind.DECIMAL ->
                sanitizeDecimal(input)

            InputKind.CHILDREN_AGES ->
                sanitizeChildrenAges(input)

            InputKind.EMAIL ->
                input.filterNot { character ->
                    character.isWhitespace()
                }

            InputKind.FREE_TEXT ->
                input
        }

        return rule.maxLength?.let { maximumLength ->
            sanitizedInput.take(maximumLength)
        } ?: sanitizedInput
    }

    /**
     * Validates one field independently from the other form fields.
     */
    fun validate(
        fieldId: String,
        value: String
    ): ValidationError? {
        if (value.isBlank()) {
            // Required fields are checked separately at form level.
            return null
        }

        return getRule(fieldId).validator(value.trim())
    }

    /**
     * Checks whether the number of entered ages matches the number of children.
     */
    fun validateChildrenAgesCount(
        childrenCountValue: String,
        childrenAgesValue: String
    ): ValidationError? {
        if (childrenCountValue.isBlank()) {
            return null
        }

        val childrenCount = childrenCountValue.toIntOrNull()
            ?: return null

        if (childrenCount == 0) {
            return if (childrenAgesValue.isBlank()) {
                null
            } else {
                ValidationError.AgesEnteredWithoutChildren
            }
        }

        if (childrenAgesValue.isBlank()) {
            return ValidationError.ChildrenAgesCountMismatch(
                expectedCount = childrenCount,
                actualCount = 0
            )
        }

        val ages = parseChildrenAges(childrenAgesValue)
            ?: return null

        return if (ages.size != childrenCount) {
            ValidationError.ChildrenAgesCountMismatch(
                expectedCount = childrenCount,
                actualCount = ages.size
            )
        } else {
            null
        }
    }

    /**
     * Returns the rule of the requested field or the default free-text rule.
     */
    private fun getRule(fieldId: String): FieldRule {
        return fieldRules[fieldId] ?: defaultRule
    }

    /**
     * Creates the rule used by name fields.
     */
    private fun nameRule(): FieldRule {
        return FieldRule(
            inputKind = InputKind.NAME,
            keyboardType = KeyboardType.Text,
            validator = ::validateName
        )
    }

    /**
     * Creates a text rule that removes digits from the entered value.
     */
    private fun textWithoutDigitsRule(): FieldRule {
        return FieldRule(
            inputKind = InputKind.TEXT_WITHOUT_DIGITS,
            keyboardType = KeyboardType.Text
        )
    }

    /**
     * Creates the rule used by Israeli zip-code fields.
     */
    private fun zipCodeRule(): FieldRule {
        return FieldRule(
            inputKind = InputKind.DIGITS,
            keyboardType = KeyboardType.Number,
            maxLength = 7,
            validator = ::validateZipCode
        )
    }

    /**
     * Creates a rule for whole numbers that must be greater than zero.
     */
    private fun positiveWholeNumberRule(): FieldRule {
        return FieldRule(
            inputKind = InputKind.DIGITS,
            keyboardType = KeyboardType.Number,
            validator = ::validatePositiveWholeNumber
        )
    }

    /**
     * Creates a rule for whole numbers that may also contain zero.
     */
    private fun nonNegativeWholeNumberRule(): FieldRule {
        return FieldRule(
            inputKind = InputKind.DIGITS,
            keyboardType = KeyboardType.Number,
            validator = ::validateNonNegativeWholeNumber
        )
    }

    /**
     * Creates the rule used by monetary fields.
     */
    private fun moneyRule(): FieldRule {
        return FieldRule(
            inputKind = InputKind.DECIMAL,
            keyboardType = KeyboardType.Decimal,
            validator = { value ->
                validateNonNegativeDecimal(
                    value = value,
                    maximumDecimalPlaces = 2
                )
            }
        )
    }

    /**
     * Keeps letters and common characters that may appear in a person's name.
     */
    private fun sanitizeName(input: String): String {
        return input.filter { character ->
            character.isLetter() ||
                    character == ' ' ||
                    character == '-' ||
                    character == '־' ||
                    character == '\'' ||
                    character == '׳'
        }
    }

    /**
     * Keeps digits and allows only one decimal separator.
     */
    private fun sanitizeDecimal(input: String): String {
        var separatorAdded = false

        return buildString {
            input.forEach { character ->
                when {
                    character.isDigit() -> {
                        append(character)
                    }

                    (
                            character == '.' ||
                                    character == ','
                            ) &&
                            !separatorAdded -> {

                        append(character)
                        separatorAdded = true
                    }
                }
            }
        }
    }

    /**
     * Keeps digits, spaces and commas in the children-ages field.
     */
    private fun sanitizeChildrenAges(input: String): String {
        return input
            .replace('.', ',')
            .filter { character ->
                character.isDigit() ||
                        character == ',' ||
                        character == ' '
            }
    }

    /**
     * Validates that a name contains at least two letters.
     */
    private fun validateName(value: String): ValidationError? {
        val lettersCount = value.count { character ->
            character.isLetter()
        }

        return if (lettersCount < 2) {
            ValidationError.NameTooShort
        } else {
            null
        }
    }

    /**
     * Validates the length and checksum of an Israeli ID number.
     */
    private fun validateIsraeliId(value: String): ValidationError? {
        if (
            value.length != 9 ||
            !value.all { character -> character.isDigit() }
        ) {
            return ValidationError.IdLength
        }

        if (value.all { character -> character == '0' }) {
            return ValidationError.InvalidIsraeliId
        }

        return if (isValidIsraeliId(value)) {
            null
        } else {
            ValidationError.InvalidIsraeliId
        }
    }

    /**
     * Calculates the checksum of an Israeli ID number.
     */
    private fun isValidIsraeliId(value: String): Boolean {
        val sum = value.mapIndexed { index, character ->
            var digit = character.digitToInt()

            digit *= if (index % 2 == 0) {
                1
            } else {
                2
            }

            if (digit > 9) {
                digit -= 9
            }

            digit
        }.sum()

        return sum % 10 == 0
    }

    /**
     * Validates the length and prefix of an Israeli mobile phone number.
     */
    private fun validateMobilePhone(value: String): ValidationError? {
        if (
            value.length != 10 ||
            !value.all { character -> character.isDigit() }
        ) {
            return ValidationError.PhoneLength
        }

        return if (!value.startsWith("05")) {
            ValidationError.InvalidMobilePhonePrefix
        } else {
            null
        }
    }

    /**
     * Validates that an Israeli zip code contains exactly seven digits.
     */
    private fun validateZipCode(value: String): ValidationError? {
        return if (
            value.length != 7 ||
            !value.all { character -> character.isDigit() }
        ) {
            ValidationError.ZipCodeLength
        } else {
            null
        }
    }

    /**
     * Validates that a whole number is greater than zero.
     */
    private fun validatePositiveWholeNumber(
        value: String
    ): ValidationError? {
        val number = value.toLongOrNull()
            ?: return ValidationError.InvalidWholeNumber

        return if (number <= 0) {
            ValidationError.MustBePositiveNumber
        } else {
            null
        }
    }

    /**
     * Validates that a whole number is not negative.
     */
    private fun validateNonNegativeWholeNumber(
        value: String
    ): ValidationError? {
        val number = value.toLongOrNull()
            ?: return ValidationError.InvalidWholeNumber

        return if (number < 0) {
            ValidationError.MustBeNonNegativeNumber
        } else {
            null
        }
    }

    /**
     * Validates a decimal number that may contain zero.
     */
    private fun validateNonNegativeDecimal(
        value: String,
        maximumDecimalPlaces: Int
    ): ValidationError? {
        val number = parseDecimal(value)
            ?: return ValidationError.InvalidDecimalNumber

        if (number < 0) {
            return ValidationError.MustBeNonNegativeNumber
        }

        if (countDecimalPlaces(value) > maximumDecimalPlaces) {
            return ValidationError.TooManyDecimalPlaces(
                maximumPlaces = maximumDecimalPlaces
            )
        }

        return null
    }

    /**
     * Validates a decimal number that must be greater than zero.
     */
    private fun validatePositiveDecimal(
        value: String,
        maximumDecimalPlaces: Int
    ): ValidationError? {
        val number = parseDecimal(value)
            ?: return ValidationError.InvalidDecimalNumber

        if (number <= 0) {
            return ValidationError.MustBePositiveNumber
        }

        if (countDecimalPlaces(value) > maximumDecimalPlaces) {
            return ValidationError.TooManyDecimalPlaces(
                maximumPlaces = maximumDecimalPlaces
            )
        }

        return null
    }

    /**
     * Converts a decimal value containing a comma or dot into a number.
     */
    private fun parseDecimal(value: String): Double? {
        return value
            .replace(',', '.')
            .toDoubleOrNull()
    }

    /**
     * Counts the number of digits located after the decimal separator.
     */
    private fun countDecimalPlaces(value: String): Int {
        val separatorIndex = maxOf(
            value.lastIndexOf('.'),
            value.lastIndexOf(',')
        )

        return if (separatorIndex == -1) {
            0
        } else {
            value.length - separatorIndex - 1
        }
    }

    /**
     * Validates the format and range of the entered children ages.
     */
    private fun validateChildrenAges(
        value: String
    ): ValidationError? {
        val parsedAges = parseChildrenAges(value)
            ?: return ValidationError.InvalidChildrenAgesFormat

        return if (parsedAges.any { age -> age !in 0..120 }) {
            ValidationError.InvalidChildAge
        } else {
            null
        }
    }

    /**
     * Parses children ages separated by commas or dots.
     */
    private fun parseChildrenAges(
        value: String
    ): List<Int>? {
        val ageParts = value
            .replace('.', ',')
            .split(',')
            .map { age ->
                age.trim()
            }

        if (
            ageParts.isEmpty() ||
            ageParts.any { age -> age.isEmpty() }
        ) {
            return null
        }

        val parsedAges = mutableListOf<Int>()

        ageParts.forEach { age ->
            val parsedAge = age.toIntOrNull()
                ?: return null

            parsedAges.add(parsedAge)
        }

        return parsedAges
    }

    /**
     * Validates the basic structure of an email address.
     */
    private fun validateEmail(value: String): ValidationError? {
        return if (
            Patterns.EMAIL_ADDRESS
                .matcher(value)
                .matches()
        ) {
            null
        } else {
            ValidationError.InvalidEmail
        }
    }
}