package com.example.easyfill_project.form_behavior_analysis

data class FormStepNavigationResult(
    val score: Int = 0,
    val level: FormBehaviorLoadLevel = FormBehaviorLoadLevel.NORMAL,
    val topContributor: String = "לא נמצאה חריגה משמעותית בניווט",

    val backStepCount: Int = 0,
    val directionChangeCount: Int = 0,
    val noProgressStepSwitches: Int = 0,
    val shortNoProgressStepVisits: Int = 0,
    val changesInLastWindow: Int = 0,

    val shouldSuggestHelp: Boolean = false
)