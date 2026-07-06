package com.example.easyfill_project.form_behavior_analysis

//final output object of the form behavior analysis. It stores the calculated load score,
// the load level, the individual Z-scores for each behavioral metric,
// the main factor that contributed to the score, and whether the system recommends offering help to the user.

//represents the result of analyzing one field.
enum class FormBehaviorLoadLevel {

    // User behaved normally.
    NORMAL,

    // Some abnormal behavior was detected.
    MODERATE_LOAD,

    // Strong abnormal behavior was detected.
    HIGH_LOAD
}

// Final result returned after comparing one field
// against the user's baseline.
data class FormBehaviorComparisonResult(

    // Which field was analyzed.
    val fieldId: String,

    // Final score (0-100) calculated from all metrics.
    val score: Int,

    // Overall load level based on the score.
    // NORMAL / MODERATE_LOAD / HIGH_LOAD
    val level: FormBehaviorLoadLevel,

    // The metric that contributed the most to the score.
    // Example:
    // "Long thinking time"
    // "Idle time"
    // "Slow typing"
    val topContributor: String,

    // Individual Z-scores for each analyzed metric.
    // These are useful for debugging, visualization,
    // or understanding WHY the score was high.

    // How unusual was the dwell time?
    val dwellTimeZ: Double,

    // How unusual was the thinking time?
    val thinkingTimeZ: Double,

    // How unusual was the typing speed?
    val typingSpeedZ: Double,

    // How unusual was the idle time?
    val idleTimeZ: Double,

    // How unusual was the review time?
    val reviewTimeZ: Double,

    // How unusual was the delete ratio?
    val deleteRatioZ: Double,

    // How unusual were the long pauses?
    val longPausesZ: Double,

    // Indicates whether the system should suggest help
    // to the user based on this result.
    // (Currently true when score >= 30)
    val shouldSuggestHelp: Boolean
)