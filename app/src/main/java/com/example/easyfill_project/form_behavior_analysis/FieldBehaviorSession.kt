package com.example.easyfill_project.form_behavior_analysis
//Stores live tracking data for one field while the user is focused on it.
 //This is temporary session data, not the final calculated sample.

//User types...
//User deletes...
//User pauses...
//User types again...
//(All values inside FieldBehaviorSession keep updating)

//FieldBehaviorSession → stores live data while the user is inside one field.

data class FieldBehaviorSession(

    // The unique id/name of the field currently being tracked.
    val fieldId: String,

    // The time when the user entered/focused this field.
    val focusStartTimeMs: Long,

    // The text value that was inside the field when focus started.
    val initialValue: String,

    // The latest text value in the field.
    // Starts as initialValue and updates whenever the user types/deletes.
    var lastValue: String = initialValue,

    // The first time the user edited the text.
    // Null means the user has not typed yet.
    var firstEditTimeMs: Long? = null,

    // The most recent time the user edited the text.
    var lastEditTimeMs: Long? = null,

    // The time of the previous edit event.
    // Used to detect long pauses between edits.
    var lastEditEventTimeMs: Long? = null,

    // Total number of characters added.
    var insertedChars: Int = 0,

    // Total number of characters deleted.
    var deletedChars: Int = 0,

    // Total number of edit events.
    var editEvents: Int = 0,

    // Number of long pauses detected while editing.
    var longPauses: Int = 0,

    // Number of times the user focused this field.
    // Starts at 1 because entering the field counts as first focus.
    var refocusCount: Int = 1,

    // Longest time the user stayed in the field without typing/editing.
    var maxIdleTimeMs: Long = 0L,

    // Number of idle events detected.
    // Example: user was inactive for 10s, 20s, 30s.
    var idleEvents: Int = 0,

    // Prevents counting the same idle level more than once.
    // 0 = none, 1 = 10s, 2 = 20s, 3 = 30s.
    var lastIdleLevelReported: Int = 0
)