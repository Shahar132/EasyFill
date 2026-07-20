package com.example.easyfill_project.hand_analysis

import android.content.Context
import android.util.Log
import com.example.easyfill_project.distress_scoring.DistressScoringManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


private const val EXCEED_RATIO_THRESHOLD = 0.10f // 10%
private const val ACC_VARIATION_FACTOR = 1.5f
private const val GYRO_VARIATION_FACTOR = 1.5f

private const val GYRO_EXCEED_RATIO_THRESHOLD = 0.10f


class MotionTrackingController(
    context: Context
) {

    private val distressManager =
        DistressScoringManager

    private val motionManager =
        MotionSensorManager(context)

    private val baselineRepository =
        MotionBaselineRepository()

    /*
     * Main motion-tracking job.
     *
     * This job:
     *
     * 1. Creates the user's motion baseline.
     * 2. Repeatedly creates five-second motion windows.
     * 3. Calculates one hand score for every window.
     */
    private var job: Job? = null

    /*
     * Separate job used to listen for voice-recording
     * start and stop events.
     *
     * The main job continues collecting sensor windows,
     * while this job controls which hand scores belong
     * to a voice-recording session.
     */
    private var recordingLifecycleJob: Job? = null

    /*
     * Stores one hand score for every completed
     * five-second motion window during the current
     * voice recording.
     *
     * Example:
     *
     * First recording window  -> 2
     * Second recording window -> 1
     * Third recording window  -> 3
     *
     * The list becomes:
     *
     * [2, 1, 3]
     *
     * MotionTrackingController owns this list because
     * this class is responsible for hand-motion analysis.
     */
    private val voiceRecordingHandScores =
        mutableListOf<Int>()

    /*
     * True only while motion windows should be stored
     * as part of the current voice recording.
     *
     * false:
     * Normal form-filling hand windows are published.
     *
     * true:
     * Hand scores are stored locally and later averaged.
     */
    private var isCollectingVoiceRecordingHandScores =
        false


    fun startTracking(
        scope: CoroutineScope
    ) {

        /*
         * Cancel an existing tracking job before starting
         * a new one.
         */
        job?.cancel()

        /*
         * Cancel an existing recording lifecycle listener
         * before creating a new listener.
         */
        recordingLifecycleJob?.cancel()

        /*
         * Listen for voice-recording start and stop events.
         */
        recordingLifecycleJob =
            scope.launch {

                /*
                 * Listen for the beginning of a new recording.
                 */
                launch {

                    distressManager
                        .voiceRecordingStarted
                        .collect {

                            /*
                             * Remove hand scores left from
                             * the previous recording.
                             */
                            voiceRecordingHandScores.clear()

                            /*
                             * From now on, every completed
                             * five-second hand score belongs
                             * to this recording.
                             */
                            isCollectingVoiceRecordingHandScores =
                                true

                            Log.d(
                                "VOICE_HAND_SESSION",
                                """
                                Started collecting hand windows
                                for a new voice recording.
                                """.trimIndent()
                            )
                        }
                }

                /*
                 * Listen for the end of the current recording.
                 */
                launch {

                    distressManager
                        .voiceRecordingStopped
                        .collect {

                            /*
                             * Stop adding new motion-window
                             * scores to this recording.
                             */
                            isCollectingVoiceRecordingHandScores =
                                false

                            /*
                             * If the recording ended before one
                             * complete five-second motion window,
                             * the list may be empty.
                             *
                             * In that case, use 0.0.
                             *
                             * Otherwise, calculate the precise
                             * Double average.
                             *
                             * No rounding happens here.
                             */
                            val handAverage =
                                if (
                                    voiceRecordingHandScores
                                        .isEmpty()
                                ) {
                                    0.0
                                } else {
                                    voiceRecordingHandScores
                                        .average()
                                }

                            Log.d(
                                "VOICE_HAND_SESSION",
                                """
                                Recording hand session completed.
                                windowScores=$voiceRecordingHandScores
                                handAverage=$handAverage
                                """.trimIndent()
                            )

                            /*
                             * Send only one final hand average
                             * to DistressScoringManager.
                             *
                             * The manager may already have the
                             * voice score, or it may still be
                             * waiting for it.
                             */
                            distressManager
                                .submitVoiceRecordingHandAverage(
                                    average = handAverage
                                )

                            /*
                             * The completed recording scores are
                             * no longer needed.
                             */
                            voiceRecordingHandScores.clear()
                        }
                }
            }

        /*
         * Start normal motion tracking.
         */
        job =
            scope.launch {

                Log.d(
                    "MOTION_FLOW",
                    "Starting 10 sec baseline"
                )

                motionManager.start()

                delay(10_000)

                val baselineResult =
                    motionManager.stopAndAnalyze()

                baselineRepository.saveBaseline(
                    baselineResult
                )

                Log.d(
                    "MOTION_FLOW",
                    "Baseline result: $baselineResult"
                )

                Log.d(
                    "MOTION_FLOW",
                    "Baseline acceleration P95: " +
                            "${baselineResult.accelerationP95}"
                )

                /*
                 * Continuously create five-second motion windows.
                 */
                while (isActive) {

                    motionManager.start()

                    delay(5_000)

                    val currentResult =
                        motionManager.stopAndAnalyze()

                    Log.d(
                        "MOTION_CURRENT",
                        currentResult.toString()
                    )

                    analyzeCurrentAgainstBaseline(
                        baseline = baselineResult,
                        current = currentResult
                    )
                }
            }
    }


    private fun analyzeCurrentAgainstBaseline(
        baseline: MotionAnalysisResult,
        current: MotionAnalysisResult
    ) {

        val exceedCount =
            current.accelerationValues.count {
                it > baseline.accelerationP95
            }

        val exceedRatio =
            if (
                current.accelerationValues
                    .isNotEmpty()
            ) {
                exceedCount.toFloat() /
                        current.accelerationValues.size
            } else {
                0f
            }

        val gyroExceedCount =
            current.gyroscopeValues.count {
                it > baseline.gyroscopeP95
            }

        val gyroExceedRatio =
            if (
                current.gyroscopeValues
                    .isNotEmpty()
            ) {
                gyroExceedCount.toFloat() /
                        current.gyroscopeValues.size
            } else {
                0f
            }

        /*
         * Check whether the gyroscope detected
         * meaningful movement.
         *
         * This helps distinguish actual hand movement
         * from simply holding the phone at a different
         * stable angle.
         */
        val gyroVariationHigh =
            current.gyroscopeVariation >
                    baseline.gyroscopeVariation *
                    GYRO_VARIATION_FACTOR

        val gyroP95High =
            gyroExceedRatio >
                    GYRO_EXCEED_RATIO_THRESHOLD

        val hasGyroscopeMovement =
            gyroVariationHigh || gyroP95High

        var score = 0

        /*
         * Rule 1:
         *
         * More than 10% of current acceleration values
         * passed the user's baseline acceleration P95.
         *
         * Count this rule only if the gyroscope also
         * detected movement.
         */
        if (
            exceedRatio >
            EXCEED_RATIO_THRESHOLD &&
            hasGyroscopeMovement
        ) {
            score += 1
        }

        /*
         * Rule 2:
         *
         * Acceleration variation is much higher than
         * the user's baseline.
         *
         * Count this rule only if the gyroscope also
         * detected movement.
         */
        if (
            current.accelerationVariation >
            baseline.accelerationVariation *
            ACC_VARIATION_FACTOR &&
            hasGyroscopeMovement
        ) {
            score += 1
        }

        /*
         * Rule 3:
         *
         * Gyroscope variation is much higher than
         * the user's baseline.
         */
        if (gyroVariationHigh) {
            score += 1
        }

        /*
         * Rule 4:
         *
         * More than 10% of current gyroscope values
         * passed the user's baseline gyroscope P95.
         */
        if (gyroP95High) {
            score += 1
        }

        Log.d(
            "MOTION_SCORE",
            "score=$score, " +
                    "accExceedCount=$exceedCount, " +
                    "accExceedRatio=$exceedRatio, " +
                    "gyroExceedCount=$gyroExceedCount, " +
                    "gyroExceedRatio=$gyroExceedRatio, " +
                    "baselineAccP95=${baseline.accelerationP95}, " +
                    "baselineGyroP95=${baseline.gyroscopeP95}, " +
                    "currentAccVar=${current.accelerationVariation}, " +
                    "accVarThreshold=" +
                    "${baseline.accelerationVariation * ACC_VARIATION_FACTOR}, " +
                    "currentGyroVar=${current.gyroscopeVariation}, " +
                    "gyroVarThreshold=" +
                    "${baseline.gyroscopeVariation * GYRO_VARIATION_FACTOR}"
        )

        /*
         * During voice recording:
         *
         * Store this five-second hand score locally.
         *
         * Do not:
         *
         * - update the manager's normal hand score
         * - calculate a normal form-filling total
         * - publish a completed form-filling window
         */
        if (isCollectingVoiceRecordingHandScores) {

            val safeScore =
                score.coerceIn(0, 4)

            voiceRecordingHandScores.add(
                safeScore
            )

            Log.d(
                "VOICE_HAND_SESSION",
                """
                Added recording hand window.
                score=$safeScore
                collectedScores=$voiceRecordingHandScores
                """.trimIndent()
            )

        } else {

            /*
             * During normal form filling:
             *
             * Update the current hand score and publish
             * one completed five-second measurement window.
             *
             * These completed windows are later used by
             * the consecutive-window confirmation logic.
             */
            distressManager.updateHandScore(
                score
            )

            distressManager
                .completeMeasurementWindow()

            distressManager.printStatus()

            if (
                distressManager
                    .isDistressDetected()
            ) {
                Log.d(
                    "DISTRESS",
                    "Distress detected"
                )
            }
        }
    }


    fun stopTracking() {

        /*
         * Stop the main sensor-analysis coroutine.
         */
        job?.cancel()
        job = null

        /*
         * Stop listening for recording lifecycle events.
         */
        recordingLifecycleJob?.cancel()
        recordingLifecycleJob = null

        /*
         * Remove any unfinished recording hand scores.
         */
        voiceRecordingHandScores.clear()

        isCollectingVoiceRecordingHandScores =
            false

        /*
         * Keep your existing sensor cleanup.
         */
        motionManager.stopAndAnalyze()

        Log.d(
            "MOTION_FLOW",
            "Motion tracking stopped"
        )
    }
}