package com.example.easyfill_project.form_behavior_analysis

enum class FormBehaviorLoadLevel {
    NORMAL,
    MODERATE_LOAD,
    HIGH_LOAD
}

data class FormBehaviorComparisonResult(
    val fieldId: String,

    val score: Int,
    val level: FormBehaviorLoadLevel,

    val topContributor: String,

    val dwellTimeZ: Double,
    val thinkingTimeZ: Double,
    val typingSpeedZ: Double,
    val idleTimeZ: Double,
    val reviewTimeZ: Double,
    val deleteRatioZ: Double,
    val longPausesZ: Double,

    val shouldSuggestHelp: Boolean
)