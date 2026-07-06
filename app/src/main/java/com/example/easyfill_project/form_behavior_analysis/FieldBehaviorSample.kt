package com.example.easyfill_project.form_behavior_analysis
// this file represents one behavior sample collected from one text field in the form
//Each object stores how the user behaved while filling a specific one field.
//FieldBehaviorSample → stores the final results for one field after the user leaves it.


data class FieldBehaviorSample(

    // Unique id/name of the field being measured.
    val fieldId: String,

    // Total time the user stayed inside the field.
    val dwellTimeMs: Long,

    // Time from entering the field until the user started typing.
    val thinkingTimeMs: Long,

    // Time spent actively typing/editing.
    val typingTimeMs: Long,

    // Time after typing ended until the user left the field.
    val reviewTimeMs: Long,

    // Number of characters the user added.
    val insertedChars: Int,

    // Number of characters the user deleted.
    val deletedChars: Int,

    // Total changed characters: inserted + deleted.
    val changedChars: Int,

    // Number of edit actions in the field.
    val editEvents: Int,

    // Number of long pauses while typing.
    val longPauses: Int,

    // Number of times the user returned/focused this field again.
    val refocusCount: Int,

    // Longest time with no activity inside this field.
    val maxIdleTimeMs: Long,

    // Number of times the user was idle for longer than the threshold.
    val idleEvents: Int,

    // Total field time divided by changed characters.
    // Helps detect slow or difficult field completion.
    val dwellMsPerChangedChar: Double,

    // Typing time divided by inserted + deleted characters.
    // Measures typing/editing speed.
    val typingMsPerChangedChar: Double,

    // Typing time divided only by inserted characters.
    // Better for measuring actual writing speed.
    val typingMsPerInsertedChar: Double,

    // Deleted characters divided by total changed characters.
    // High value may indicate many corrections.
    val deleteRatio: Double
)