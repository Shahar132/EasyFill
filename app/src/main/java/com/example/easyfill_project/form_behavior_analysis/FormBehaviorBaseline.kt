package com.example.easyfill_project.form_behavior_analysis

//Represents the user's baseline.
 //It is calculated from the first few fields the user fills
 // (currently the first 3-4 samples).
 //Later, every new FieldBehaviorSample is compared against these values to determine whether the user's behavior is normal or unusual.

data class FormBehaviorBaseline(

    // Number of samples (fields) used to build this baseline.
    // Example: 3 or 4 fields.
    val sampleCount: Int,

    // Time when this baseline was created.
    val calculatedAtMs: Long,


    // Average time the user normally stays inside a field.
    val avgDwellTimeMs: Double,

    // Standard deviation of dwell time.
    // Shows how much the dwell time usually varies.
    val stdDwellTimeMs: Double,


    // Average time before the user starts typing.
    val avgThinkingTimeMs: Double,

    // Standard deviation of thinking time.
    val stdThinkingTimeMs: Double,



    // Average typing time per inserted character.
    // Represents the user's normal typing speed.
    val avgTypingMsPerInsertedChar: Double,

    // Standard deviation of typing speed.
    val stdTypingMsPerInsertedChar: Double,



    // Average time the user stays after finishing typing.
    val avgReviewTimeMs: Double,

    // Standard deviation of review time.
    val stdReviewTimeMs: Double,



    // Average longest idle period inside a field.
    val avgMaxIdleTimeMs: Double,

    // Standard deviation of longest idle time.
    val stdMaxIdleTimeMs: Double,


    // Average number of idle events.
    // (How many times the user exceeded the idle threshold.)
    val avgIdleEvents: Double,

    // Standard deviation of idle events.
    val stdIdleEvents: Double,



    // Average delete ratio.
    // Represents how often the user normally deletes text.
    val avgDeleteRatio: Double,

    // Standard deviation of delete ratio.
    val stdDeleteRatio: Double,


    // Average number of long pauses while typing.
    val avgLongPauses: Double,

    // Standard deviation of long pauses.
    val stdLongPauses: Double
)