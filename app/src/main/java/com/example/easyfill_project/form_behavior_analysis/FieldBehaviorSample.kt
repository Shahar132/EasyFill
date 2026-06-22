package com.example.easyfill_project.form_behavior_analysis

data class FieldBehaviorSample(
    val fieldId: String,

    val dwellTimeMs: Long,
    val thinkingTimeMs: Long,
    val typingTimeMs: Long,
    val reviewTimeMs: Long,

    val insertedChars: Int,
    val deletedChars: Int,
    val changedChars: Int,
    val editEvents: Int,
    val longPauses: Int,
    val refocusCount: Int,

    // Longest no-activity time inside this field.
    val maxIdleTimeMs: Long,

    // Number of idle threshold events inside this field.
    val idleEvents: Int,

    // Time in field divided by all changes: inserted + deleted.
    val dwellMsPerChangedChar: Double,

    // Typing time divided by all changes: inserted + deleted.
    val typingMsPerChangedChar: Double,

    // Typing time divided only by inserted characters.
    // Better for measuring actual writing speed.
    val typingMsPerInsertedChar: Double,

    // Deleted characters divided by all changed characters.
    // This should be a supporting metric only, not a trigger by itself.
    val deleteRatio: Double
)