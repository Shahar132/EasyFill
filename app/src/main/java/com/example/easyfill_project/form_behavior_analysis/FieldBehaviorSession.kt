package com.example.easyfill_project.form_behavior_analysis

data class FieldBehaviorSession(
    val fieldId: String,
    val focusStartTimeMs: Long,
    val initialValue: String,

    var lastValue: String = initialValue,

    var firstEditTimeMs: Long? = null,
    var lastEditTimeMs: Long? = null,
    var lastEditEventTimeMs: Long? = null,

    var insertedChars: Int = 0,
    var deletedChars: Int = 0,
    var editEvents: Int = 0,

    var longPauses: Int = 0,
    var refocusCount: Int = 1,

    // Longest time the user stayed in the field without any text activity.
    var maxIdleTimeMs: Long = 0L,

    // Number of idle threshold events that happened in this field.
    // Example: passed 10 seconds without activity, passed 20 seconds, etc.
    var idleEvents: Int = 0,

    // Prevents reporting the same idle level many times.
    // 0 = no idle reported, 1 = 10s, 2 = 20s, 3 = 30s.
    var lastIdleLevelReported: Int = 0
)