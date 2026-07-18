package com.example.easyfill_project.distress_scoring

/**
 * Represents one completed distress-measurement window.
 * For the hand-motion analysis, one window currently lasts 5 seconds.

 * This class is important because StateFlow does not emit the same value twice.
 * For example, if two consecutive windows both produce level 2,
 * totalScore may remain 2 without producing a new StateFlow emission.
 *
 * A DistressWindowResult is therefore emitted once for every completed window,
 * even when the final level is equal to the previous window's level.
 */
data class DistressWindowResult(

    // Final combined distress level calculated for this window.
    val level: Int,

    // Individual scores are included mainly for logs and future analysis.
    val handScore: Int,
    val voiceScore: Int,
    val formBehaviorScore: Int,
    val faceScore: Int,

    // The interaction mode active when this window ended.
    val mode: DistressMode,

    // Time at which the measurement window was completed.
    val timestampMillis: Long = System.currentTimeMillis()
)