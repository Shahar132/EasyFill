package com.example.easyfill_project.distress_scoring

/**
 * Represents one completed multimodal voice-recording
 * distress analysis.
 *
 * A result is produced only after all three analysis
 * components have completed:
 *
 * 1. Voice analysis.
 * 2. Face analysis.
 * 3. Hand-motion analysis.
 *
 * A modality score may still be null after completion.
 *
 * null means:
 * the component completed, but no reliable information
 * was available.
 *
 * A value of 0 means:
 * the component was available and reliably detected
 * no distress.
 */
data class VoiceRecordingDistressResult(

    /*
     * Final weighted and rounded multimodal distress
     * level from 0 to 4.
     */
    val level: Int,

    /*
     * Final voice score from 0 to 4.
     *
     * null means voice analysis was unavailable
     * or unreliable.
     */
    val voiceScore: Int?,

    /*
     * Average of all reliable face results produced
     * during the recording.
     *
     * The value remains a Double so precision is kept
     * until after multimodal weighting.
     *
     * null means no reliable face result was available.
     */
    val faceAverage: Double?,

    /*
     * Average of all complete hand-motion windows produced
     * during the recording.
     *
     * The value remains a Double so precision is kept
     * until after multimodal weighting.
     *
     * null means no complete hand window was available.
     */
    val handAverage: Double?,

    /*
     * True when a reliable voice score participated
     * in the final weighted calculation.
     */
    val voiceAvailable: Boolean,

    /*
     * True when at least one reliable face score
     * participated in the final weighted calculation.
     */
    val faceAvailable: Boolean,

    /*
     * True when at least one complete hand-motion window
     * participated in the final weighted calculation.
     */
    val handAvailable: Boolean,

    /*
     * Normalized weighted score before final rounding.
     *
     * Recording weights:
     *
     * Voice = 35%
     * Face  = 35%
     * Hand  = 30%
     *
     * Unavailable modalities are excluded and the remaining
     * weights are normalized.
     */
    val weightedScore: Double
)