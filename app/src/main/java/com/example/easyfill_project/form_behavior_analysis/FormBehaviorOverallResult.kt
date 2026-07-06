package com.example.easyfill_project.form_behavior_analysis

// Represents the FINAL overall result of the form behavior analysis.
//
// Unlike FormBehaviorComparisonResult, which describes ONE field,
// this class summarizes the entire form (and can combine multiple
// behavior analysis modules in the future).
data class FormBehaviorOverallResult(

    // ---------------------------------------------------------
    // Overall distress/load score for the entire form.
    // Example: 0-100
    // ---------------------------------------------------------
    val score: Int = 0,

    // Overall load level:
    // NORMAL
    // MODERATE_LOAD
    // HIGH_LOAD
    val level: FormBehaviorLoadLevel = FormBehaviorLoadLevel.NORMAL,

    // The main reason for the overall score.
    // Example:
    // "Long thinking time"
    // "Slow typing"
    val topContributor: String =
        "לא נמצאה חריגה משמעותית בהתנהגות הטופס",

    // Score coming specifically from field behavior analysis.
    // (Typing, thinking time, idle time, etc.)
    val fieldBehaviorScore: Int = 0,

    // Score coming from step navigation behavior.
    // This is intended for another module that would analyze
    // navigation between form pages or sections.
    // (Currently not implemented.)
    val stepNavigationScore: Int = 0,

    // Main contributor inside the field behavior module.
    val fieldTopContributor: String =
        "אין חריגה משמעותית בשדות",

    // Main contributor inside the step navigation module.
    // Reserved for future implementation.
    val stepTopContributor: String =
        "אין חריגה משמעותית בניווט",

    // Final decision:
    // Should the application suggest help to the user?
    val shouldSuggestHelp: Boolean = false
)