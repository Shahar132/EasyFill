package com.example.easyfill_project.face_analysis

import android.util.Log

/**
 * ============================================================
 * FACE RECORDING SCORE AGGREGATOR
 * ============================================================
 *
 * Purpose:
 *
 * Collect every reliable, stabilized face-distress score
 * produced while one voice recording is active.
 *
 * When the recording ends, this class calculates one average
 * face score that can be combined with:
 *
 * - the final voice score;
 * - the final hand average.
 *
 * Important distinction:
 *
 * 0.0:
 * Face analysis was available and reliably detected no distress.
 *
 * null:
 * No reliable face result was available during the recording.
 *
 * This class does not:
 *
 * - open the camera;
 * - process camera frames;
 * - calculate facial features;
 * - combine modalities;
 * - show a chatbot alert.
 *
 * FaceMonitoringSession performs face analysis.
 * DistressScoringManager performs multimodal fusion.
 */
class FaceRecordingScoreAggregator {

    /*
     * Stores every reliable continuous face score received
     * during the current voice recording.
     *
     * Example:
     *
     * [1.2, 1.5, 2.0, 1.8]
     */
    private val recordingScores =
        mutableListOf<Float>()

    /*
     * True only while a voice recording is active.
     *
     * Face results received during ordinary form filling
     * must not be added to the recording average.
     */
    private var isCollecting =
        false

    /**
     * Starts a new face-recording collection session.
     *
     * This must be called once when a new voice recording begins.
     */
    fun startRecording() {

        /*
         * Remove scores belonging to a previous recording.
         */
        recordingScores.clear()

        /*
         * From this point, reliable face results belong
         * to the new recording.
         */
        isCollecting = true

        Log.d(
            TAG,
            "Started collecting face scores for a new recording."
        )
    }

    /**
     * Adds one reliable face score to the current recording.
     *
     * This function receives the stabilized score from
     * FaceDistressResult, not a raw camera-frame score.
     */
    fun addReliableScore(
        score: Float
    ) {

        /*
         * Ignore face results outside an active recording.
         */
        if (!isCollecting) {
            return
        }

        /*
         * Ignore invalid numeric values such as NaN or Infinity.
         */
        if (!score.isFinite()) {
            Log.d(
                TAG,
                "Invalid face score ignored: $score"
            )

            return
        }

        /*
         * Keep every score inside the valid distress range.
         */
        val safeScore =
            score.coerceIn(
                minimumValue = 0f,
                maximumValue = 4f
            )

        recordingScores.add(
            safeScore
        )

        Log.d(
            TAG,
            """
            Added reliable face score.
            score=$safeScore
            collectedCount=${recordingScores.size}
            """.trimIndent()
        )
    }

    /**
     * Ends the current recording and calculates its face average.
     *
     * Returns:
     *
     * Double from 0.0 to 4.0:
     * At least one reliable face score was available.
     *
     * null:
     * No reliable face score was available.
     */
    fun finishRecording():
            Double? {

        /*
         * Stop accepting additional scores for this recording.
         */
        isCollecting = false

        /*
         * Calculate a precise average only when at least one
         * reliable result exists.
         *
         * An empty list becomes null, not 0.0.
         */
        val average =
            recordingScores
                .takeIf { scores ->
                    scores.isNotEmpty()
                }
                ?.average()
                ?.coerceIn(
                    minimumValue = 0.0,
                    maximumValue = 4.0
                )

        Log.d(
            TAG,
            """
            Face recording collection completed.
            collectedScores=$recordingScores
            collectedCount=${recordingScores.size}
            faceAvailable=${average != null}
            faceAverage=$average
            """.trimIndent()
        )

        /*
         * The individual scores are no longer required after
         * their average has been calculated.
         */
        recordingScores.clear()

        return average
    }

    /**
     * Cancels and clears any unfinished collection session.
     */
    fun reset() {

        isCollecting = false
        recordingScores.clear()

        Log.d(
            TAG,
            "Face recording collection reset."
        )
    }

    companion object {

        private const val TAG =
            "VOICE_FACE_SESSION"
    }
}