package com.example.easyfill_project.form_behavior_analysis

data class FormBehaviorOverallResult(
    val score: Int = 0,
    val level: FormBehaviorLoadLevel = FormBehaviorLoadLevel.NORMAL,
    val topContributor: String = "לא נמצאה חריגה משמעותית בהתנהגות הטופס",

    val fieldBehaviorScore: Int = 0,
    val stepNavigationScore: Int = 0,

    val fieldTopContributor: String = "אין חריגה משמעותית בשדות",
    val stepTopContributor: String = "אין חריגה משמעותית בניווט",

    val shouldSuggestHelp: Boolean = false
)