package com.example.easyfill_project.distress_scoring

/**
 * Represents one completed voice-recording distress analysis.
 *
 * A complete result is produced only after both values are available:
 *
 * 1. The final voice score.
 * 2. The average hand score from all motion windows in the recording.
 *
 * The hand average remains a Double so that we do not lose precision
 * before calculating the final weighted distress level.
 */
data class VoiceRecordingDistressResult(

    // Final weighted and rounded distress level: 0–4.
    val level: Int,

    // Final voice-analysis score: 0–4.
    val voiceScore: Int,

    // Average of all hand windows during this recording.
    // It is intentionally not rounded before weighting.
    val handAverage: Double,

    // Weighted score before the final rounding.
    val weightedScore: Double
)