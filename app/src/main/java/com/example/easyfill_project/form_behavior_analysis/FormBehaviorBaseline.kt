package com.example.easyfill_project.form_behavior_analysis

data class FormBehaviorBaseline(
    val sampleCount: Int,
    val calculatedAtMs: Long,

    val avgDwellTimeMs: Double,
    val stdDwellTimeMs: Double,

    val avgThinkingTimeMs: Double,
    val stdThinkingTimeMs: Double,

    val avgTypingMsPerInsertedChar: Double,
    val stdTypingMsPerInsertedChar: Double,

    val avgReviewTimeMs: Double,
    val stdReviewTimeMs: Double,

    val avgMaxIdleTimeMs: Double,
    val stdMaxIdleTimeMs: Double,

    val avgIdleEvents: Double,
    val stdIdleEvents: Double,

    val avgDeleteRatio: Double,
    val stdDeleteRatio: Double,

    val avgLongPauses: Double,
    val stdLongPauses: Double
)