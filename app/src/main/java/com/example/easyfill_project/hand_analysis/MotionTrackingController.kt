//package com.example.easyfill_project.hand_analysis
//
//import android.content.Context
//import android.util.Log
//import com.example.easyfill_project.distress_scoring.DistressScoringManager
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//
//
//private const val EXCEED_RATIO_THRESHOLD = 0.10f // 10%
//private const val ACC_VARIATION_FACTOR = 1.5f
//private const val GYRO_VARIATION_FACTOR = 1.5f
//
//private const val GYRO_EXCEED_RATIO_THRESHOLD = 0.10f // 10%
//
//
//class MotionTrackingController(
//    context: Context
//) {
//
//    private val distressManager =
//        DistressScoringManager
//
//    private val motionManager =
//        MotionSensorManager(context)
//
//    private val baselineRepository =
//        MotionBaselineRepository()
//
//    /*
//     * Main motion-tracking job.
//     *
//     * This job:
//     *
//     * 1. Creates the user's motion baseline.
//     * 2. Repeatedly creates five-second motion windows.
//     * 3. Calculates one hand score for every window.
//     */
//    private var job: Job? = null
//
//    /*
//     * Separate job used to listen for voice-recording
//     * start and stop events.
//     *
//     * The main job continues collecting sensor windows,
//     * while this job controls which hand scores belong
//     * to a voice-recording session.
//     */
//    private var recordingLifecycleJob: Job? = null
//
//    /*
//     * Stores one hand score for every completed
//     * five-second motion window during the current
//     * voice recording.
//     *
//     * Example:
//     *
//     * First recording window  -> 2
//     * Second recording window -> 1
//     * Third recording window  -> 3
//     *
//     * The list becomes:
//     *
//     * [2, 1, 3]
//     *
//     * MotionTrackingController owns this list because
//     * this class is responsible for hand-motion analysis.
//     */
//    private val voiceRecordingHandScores =
//        mutableListOf<Int>()
//
//    /*
//     * True only while motion windows should be stored
//     * as part of the current voice recording.
//     *
//     * false:
//     * Normal form-filling hand windows are published.
//     *
//     * true:
//     * Hand scores are stored locally and later averaged.
//     */
//    private var isCollectingVoiceRecordingHandScores =
//        false
//
//
//    fun startTracking(
//        scope: CoroutineScope
//    ) {
//
//        /*
//         * Cancel an existing tracking job before starting
//         * a new one.
//         */
//        job?.cancel()
//
//        /*
//         * Cancel an existing recording lifecycle listener
//         * before creating a new listener.
//         */
//        recordingLifecycleJob?.cancel()
//
//        /*
//         * Listen for voice-recording start and stop events.
//         */
//        recordingLifecycleJob =
//            scope.launch {
//
//                /*
//                 * Listen for the beginning of a new recording.
//                 */
//                launch {
//
//                    distressManager
//                        .voiceRecordingStarted
//                        .collect {
//
//                            /*
//                             * Remove hand scores left from
//                             * the previous recording.
//                             */
//                            voiceRecordingHandScores.clear()
//
//                            /*
//                             * From now on, every completed
//                             * five-second hand score belongs
//                             * to this recording.
//                             */
//                            isCollectingVoiceRecordingHandScores =
//                                true
//
//                            Log.d(
//                                "VOICE_HAND_SESSION",
//                                """
//                                Started collecting hand windows
//                                for a new voice recording.
//                                """.trimIndent()
//                            )
//                        }
//                }
//
//                /*
//                 * Listen for the end of the current recording.
//                 */
//                launch {
//
//                    distressManager
//                        .voiceRecordingStopped
//                        .collect {
//
//                            /*
//                             * Stop adding new motion-window
//                             * scores to this recording.
//                             */
//                            isCollectingVoiceRecordingHandScores =
//                                false
//
//                            /*
//                          * Calculate one final hand result for the recording.
//                          *
//                          * When at least one complete five-second hand window exists:
//                          * calculate the precise Double average.
//                          *
//                          * When no complete window exists:
//                          * return null.
//                          *
//                          * null means that hand information was unavailable.
//                          *
//                          * This is different from 0.0:
//                          *
//                          * 0.0 means that hand analysis was available and reliably
//                          * detected no unusual hand movement.
//                          */
//                            val handAverage: Double? =
//                                voiceRecordingHandScores
//                                    .takeIf { scores ->
//                                        scores.isNotEmpty()
//                                    }
//                                    ?.average()
//
//                            Log.d(
//                                "VOICE_HAND_SESSION",
//                                """
//                                Recording hand session completed.
//                                windowScores=$voiceRecordingHandScores
//                                completedWindowCount=${voiceRecordingHandScores.size}
//                                handAvailable=${handAverage != null}
//                                handAverage=$handAverage
//                                """.trimIndent()
//                                                        )
//
//                            /*
//                             * Send only one final hand average
//                             * to DistressScoringManager.
//                             *
//                             * The manager may already have the
//                             * voice score, or it may still be
//                             * waiting for it.
//                             */
//                            distressManager
//                                .submitVoiceRecordingHandAverage(
//                                    average = handAverage
//                                )
//
//                            /*
//                             * The completed recording scores are
//                             * no longer needed.
//                             */
//                            voiceRecordingHandScores.clear()
//                        }
//                }
//            }
//
//        /*
//         * Start normal motion tracking.
//         */
//        job =
//            scope.launch {
//
//                Log.d(
//                    "MOTION_FLOW",
//                    "Starting 10 sec baseline"
//                )
//
//                motionManager.start()
//
//                delay(10_000)
//
//                val baselineResult =
//                    motionManager.stopAndAnalyze()
//
//                baselineRepository.saveBaseline(
//                    baselineResult
//                )
//
//                Log.d(
//                    "MOTION_FLOW",
//                    "Baseline result: $baselineResult"
//                )
//
//                Log.d(
//                    "MOTION_FLOW",
//                    "Baseline acceleration P95: " +
//                            "${baselineResult.accelerationP95}"
//                )
//
//                /*
//                 * Continuously create five-second motion windows.
//                 */
//                while (isActive) {
//
//                    motionManager.start()
//
//                    delay(5_000)
//
//                    val currentResult =
//                        motionManager.stopAndAnalyze()
//
//                    Log.d(
//                        "MOTION_CURRENT",
//                        currentResult.toString()
//                    )
//
//                    analyzeCurrentAgainstBaseline(
//                        baseline = baselineResult,
//                        current = currentResult
//                    )
//                }
//            }
//    }
//
//
//
//
//    private fun analyzeCurrentAgainstBaseline(
//        baseline: MotionAnalysisResult,
//        current: MotionAnalysisResult
//    ) {
//
//        /*
//         * Count how many acceleration samples exceeded
//         * the personal baseline P95 value.
//         */
//        val exceedCount =
//            current.accelerationValues.count { value ->
//                value > baseline.accelerationP95
//            }
//
//        /*
//         * Calculate the percentage of acceleration samples
//         * that exceeded the personal baseline P95.
//         */
//        val exceedRatio =
//            if (current.accelerationValues.isNotEmpty()) {
//                exceedCount.toFloat() /
//                        current.accelerationValues.size
//            } else {
//                0f
//            }
//
//        /*
//         * Count how many gyroscope samples exceeded
//         * the personal baseline P95 value.
//         */
//        val gyroExceedCount =
//            current.gyroscopeValues.count { value ->
//                value > baseline.gyroscopeP95
//            }
//
//        /*
//         * Calculate the percentage of gyroscope samples
//         * that exceeded the personal baseline P95.
//         */
//        val gyroExceedRatio =
//            if (current.gyroscopeValues.isNotEmpty()) {
//                gyroExceedCount.toFloat() /
//                        current.gyroscopeValues.size
//            } else {
//                0f
//            }
//
//        /*
//         * Check whether the gyroscope variation exceeded
//         * the personal baseline threshold.
//         */
//        val gyroVariationHigh =
//            current.gyroscopeVariation >
//                    baseline.gyroscopeVariation *
//                    GYRO_VARIATION_FACTOR
//
//        /*
//         * Check whether enough gyroscope samples exceeded
//         * the personal baseline P95 value.
//         */
//        val gyroP95High =
//            gyroExceedRatio >
//                    GYRO_EXCEED_RATIO_THRESHOLD
//
//        /*
//         * At least one gyroscope rule must indicate movement
//         * before acceleration rules may contribute points.
//         *
//         * This helps distinguish real movement from holding
//         * the phone at a different stable angle.
//         */
//        val hasGyroscopeMovement =
//            gyroVariationHigh || gyroP95High
//
//        /*
//         * Store the result of each scoring rule separately.
//         *
//         * These variables are used for diagnostic logging.
//         * They preserve the existing score behavior.
//         */
//        val accelerationExceedRule =
//            exceedRatio >
//                    EXCEED_RATIO_THRESHOLD &&
//                    hasGyroscopeMovement
//
//        val accelerationVariationRule =
//            current.accelerationVariation >
//                    baseline.accelerationVariation *
//                    ACC_VARIATION_FACTOR &&
//                    hasGyroscopeMovement
//
//        val gyroscopeVariationRule =
//            gyroVariationHigh
//
//        val gyroscopeExceedRule =
//            gyroP95High
//
//        var score = 0
//
//        /*
//         * Rule 1:
//         *
//         * Enough acceleration samples exceeded the baseline
//         * P95 while gyroscope movement was also detected.
//         */
//        if (accelerationExceedRule) {
//            score += 1
//        }
//
//        /*
//         * Rule 2:
//         *
//         * Acceleration variation exceeded the personal
//         * threshold while gyroscope movement was detected.
//         */
//        if (accelerationVariationRule) {
//            score += 1
//        }
//
//        /*
//         * Rule 3:
//         *
//         * Gyroscope variation exceeded the personal threshold.
//         */
//        if (gyroscopeVariationRule) {
//            score += 1
//        }
//
//        /*
//         * Rule 4:
//         *
//         * Enough gyroscope samples exceeded the baseline P95.
//         */
//        if (gyroscopeExceedRule) {
//            score += 1
//        }
//
//        /*
//         * Diagnostic log that shows exactly which rules
//         * contributed to the final hand score.
//         */
//        Log.d(
//            "MOTION_SCORE",
//            "score=$score, " +
//                    "accelerationExceedRule=$accelerationExceedRule, " +
//                    "accelerationVariationRule=$accelerationVariationRule, " +
//                    "gyroscopeVariationRule=$gyroscopeVariationRule, " +
//                    "gyroscopeExceedRule=$gyroscopeExceedRule, " +
//                    "hasGyroscopeMovement=$hasGyroscopeMovement, " +
//                    "accExceedCount=$exceedCount, " +
//                    "accExceedRatio=$exceedRatio, " +
//                    "accExceedThreshold=$EXCEED_RATIO_THRESHOLD, " +
//                    "gyroExceedCount=$gyroExceedCount, " +
//                    "gyroExceedRatio=$gyroExceedRatio, " +
//                    "gyroExceedThreshold=$GYRO_EXCEED_RATIO_THRESHOLD, " +
//                    "baselineAccP95=${baseline.accelerationP95}, " +
//                    "baselineGyroP95=${baseline.gyroscopeP95}, " +
//                    "currentAccVar=${current.accelerationVariation}, " +
//                    "accVarThreshold=" +
//                    "${baseline.accelerationVariation * ACC_VARIATION_FACTOR}, " +
//                    "currentGyroVar=${current.gyroscopeVariation}, " +
//                    "gyroVarThreshold=" +
//                    "${baseline.gyroscopeVariation * GYRO_VARIATION_FACTOR}"
//        )
//
//        /*
//         * During voice recording:
//         *
//         * Store this five-second hand score locally.
//         *
//         * Do not:
//         *
//         * - update the manager's normal hand score
//         * - calculate a normal form-filling total
//         * - publish a completed form-filling window
//         */
//        if (isCollectingVoiceRecordingHandScores) {
//
//            val safeScore =
//                score.coerceIn(0, 4)
//
//            voiceRecordingHandScores.add(
//                safeScore
//            )
//
//            Log.d(
//                "VOICE_HAND_SESSION",
//                """
//            Added recording hand window.
//            score=$safeScore
//            collectedScores=$voiceRecordingHandScores
//            """.trimIndent()
//            )
//
//        } else {
//
//            /*
//             * During normal form filling:
//             *
//             * Update the current hand score and publish
//             * one completed five-second measurement window.
//             *
//             * These completed windows are later used by
//             * the consecutive-window confirmation logic.
//             */
//            distressManager.updateHandScore(
//                score
//            )
//
//            distressManager
//                .completeMeasurementWindow()
//
//            distressManager.printStatus()
//
//            if (
//                distressManager
//                    .isDistressDetected()
//            ) {
//                Log.d(
//                    "DISTRESS",
//                    "Distress detected"
//                )
//            }
//        }
//    }
//
//
//    fun stopTracking() {
//
//        /*
//         * Stop the main sensor-analysis coroutine.
//         */
//        job?.cancel()
//        job = null
//
//        /*
//         * Stop listening for recording lifecycle events.
//         */
//        recordingLifecycleJob?.cancel()
//        recordingLifecycleJob = null
//
//        /*
//         * Remove any unfinished recording hand scores.
//         */
//        voiceRecordingHandScores.clear()
//
//        isCollectingVoiceRecordingHandScores =
//            false
//
//        /*
//   * Stop the motion sensors and return the last unfinished
//   * sensor analysis, which is intentionally ignored here.
//   */
//        motionManager.stopAndAnalyze()
//
//        /*
//         * Hand tracking is no longer active.
//         *
//         * Mark hand information as unavailable so the most recent
//         * form hand score is not reused after this screen closes.
//         */
//        distressManager.clearFormHandScore()
//
//        Log.d(
//            "MOTION_FLOW",
//            "Motion tracking stopped"
//        )
//    }
//}



//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.example.easyfill_project.hand_analysis

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.easyfill_project.distress_scoring.DistressScoringManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt


/*
 * Live hand analysis.
 */
private const val LIVE_WINDOW_SECONDS = 5.0
private const val LIVE_ANALYSIS_INTERVAL_MILLIS = 5_000L


/*
 * Baseline collection.
 *
 * Each valid ten-second session is divided into five
 * non-overlapping two-second summaries.
 */
private const val BASELINE_SESSION_SECONDS = 10.0
private const val BASELINE_SESSION_MILLIS = 10_000L

private const val BASELINE_WINDOW_SECONDS = 2.0
private const val BASELINE_WINDOW_COUNT = 5

private const val MIN_BASELINE_SESSION_DURATION_SECONDS = 9.5
private const val MIN_TWO_SECOND_WINDOW_DURATION_SECONDS = 1.70

private const val MAX_INITIAL_BASELINE_ATTEMPTS = 3


/*
 * Firebase loading must not block tracking forever.
 */
private const val BASELINE_LOAD_TIMEOUT_MILLIS = 8_000L


/*
 * Tremor frequency band used by the uploaded studies.
 */
private const val TREMOR_MIN_HZ = 6.0
private const val TREMOR_MAX_HZ = 13.0


/*
 * Whole five-second periodicity requirements.
 *
 * These values determine whether movement resembles a
 * sustained rhythmic tremor. They do not determine severity.
 */
private const val MIN_WHOLE_CONCENTRATION = 4.0
private const val MIN_WHOLE_NARROWBAND_RATIO = 3.0
private const val MIN_WHOLE_RHYTHMIC_SHARE = 0.35


/*
 * Four overlapping two-second windows are analyzed inside
 * every five-second live window:
 *
 * 0-2 seconds
 * 1-3 seconds
 * 2-4 seconds
 * 3-5 seconds
 */
private const val TEMPORAL_WINDOW_SECONDS = 2.0
private const val TEMPORAL_WINDOW_STEP_SECONDS = 1.0
private const val TEMPORAL_WINDOW_COUNT = 4

private const val MIN_TEMPORAL_CANDIDATE_WINDOWS = 3

private const val MIN_TEMPORAL_CONCENTRATION = 3.0
private const val MIN_TEMPORAL_NARROWBAND_RATIO = 2.0
private const val MIN_TEMPORAL_RHYTHMIC_SHARE = 0.30

private const val TEMPORAL_PEAK_TOLERANCE_HZ = 1.0
private const val MAX_TEMPORAL_FREQUENCY_SPREAD_HZ = 1.0

private const val MAX_TEMPORAL_POWER_RATIO = 6.0
private const val MAX_TEMPORAL_POWER_CV = 0.70


/*
 * A personal rhythmic-share threshold is allowed to rise
 * above the generic floor, but it is capped so weak real
 * tremor is not made impossible to detect.
 */
private const val MAX_PERSONAL_RHYTHMIC_THRESHOLD = 0.75


/*
 * Detects whether most movement occurred during one short
 * part of the five-second window.
 */
private const val BURST_SEGMENT_COUNT = 5
private const val BURST_DOMINANCE_FACTOR = 4.0
private const val BURST_DOMINANCE_ABSOLUTE_MARGIN = 0.03


/*
 * Initial baseline protection.
 *
 * During the first baseline there is no previous personal
 * profile. Therefore, only especially clear and sustained
 * periodic motion causes rejection.
 *
 * Natural small hand movement should still be accepted.
 */
private const val INITIAL_REJECTION_CONCENTRATION = 12.0
private const val INITIAL_REJECTION_NARROWBAND_RATIO = 6.0
private const val INITIAL_REJECTION_RHYTHMIC_SHARE = 0.65

private const val INITIAL_WINDOW_REJECTION_CONCENTRATION = 6.0
private const val INITIAL_WINDOW_REJECTION_NARROWBAND_RATIO = 3.5
private const val INITIAL_WINDOW_REJECTION_RHYTHMIC_SHARE = 0.50

private const val MIN_BASELINE_TREMOR_WINDOWS = 3
private const val MAX_BASELINE_TREMOR_FREQUENCY_SPREAD_HZ = 1.0


/*
 * Prevents an accumulated baseline from learning a session
 * containing strong movement during almost the entire
 * ten-second period.
 *
 * This comparison is relative to the existing personal
 * baseline rather than an absolute sensor value.
 */
private const val SUSTAINED_MOVEMENT_NORMAL_MULTIPLIER = 3.0
private const val MIN_SUSTAINED_MOVEMENT_WINDOWS = 4


/*
 * Personal normal-range calculation.
 *
 * The accumulated baseline stores a mean and M2 for every
 * learned feature. M2 and totalWindowCount are used to derive
 * the sample standard deviation without storing old windows.
 */
private const val PERSONAL_NORMAL_STANDARD_DEVIATION_MULTIPLIER = 2.5

/*
 * When very few windows exist, standard deviation may be zero
 * or extremely small. These floors prevent unstable
 * comparisons and division.
 */
private const val MIN_RELATIVE_NORMAL_SCALE = 0.15
private const val MIN_ABSOLUTE_NORMAL_SCALE = 1e-9


/*
 * Severity weights.
 *
 * Spectral power confirms rhythmic motion, but physical
 * acceleration and rotation contribute more to severity.
 * This prevents a very clean weak rhythm from receiving
 * score 4 only because its spectrum is highly concentrated.
 */
private const val ACCELERATION_SEVERITY_WEIGHT = 0.55
private const val GYROSCOPE_SEVERITY_WEIGHT = 0.35
private const val SPECTRAL_SEVERITY_WEIGHT = 0.10

private const val MAX_SPECTRAL_SEVERITY_LEVEL = 2.5


/*
 * Personal severity levels.
 *
 * These operate on relative logarithmic deviation from the
 * user's personal upper-normal range, not on raw sensor
 * values.
 */
private const val SCORE_TWO_THRESHOLD = 0.90
private const val SCORE_THREE_THRESHOLD = 1.70
private const val SCORE_FOUR_THRESHOLD = 2.70


class MotionTrackingController(
    context: Context
) {

    private val distressManager =
        DistressScoringManager

    private val motionManager =
        MotionSensorManager(context)

    private val baselineRepository =
        MotionBaselineRepository()


    private var trackingJob: Job? = null
    private var recordingLifecycleJob: Job? = null


    private val voiceRecordingHandScores =
        mutableListOf<Int>()


    /*
     * The hand-analysis algorithm remains identical during
     * form filling and voice recording.
     *
     * Only the destination of the completed score changes.
     */
    private var isCollectingVoiceRecordingHandScores =
        false


    fun startTracking(
        scope: CoroutineScope
    ) {
        trackingJob?.cancel()
        recordingLifecycleJob?.cancel()

        voiceRecordingHandScores.clear()

        isCollectingVoiceRecordingHandScores =
            false

        startVoiceRecordingLifecycleListener(
            scope = scope
        )

        trackingJob =
            scope.launch {

                val loadResult =
                    loadBaselineWithTimeout()

                val loadedBaseline =
                    if (loadResult.isSuccess) {
                        loadResult.getOrNull()
                    } else {
                        null
                    }

                if (loadResult.isFailure) {
                    Log.e(
                        "MOTION_FLOW",
                        "Failed to load the saved motion baseline",
                        loadResult.exceptionOrNull()
                    )
                }

                /*
                 * When a saved baseline exists, it becomes the
                 * active profile immediately.
                 *
                 * When no baseline exists, create the initial
                 * baseline before live hand scoring starts.
                 */
                val activeBaseline =
                    loadedBaseline
                        ?: createInitialBaseline(
                            canPersist =
                                loadResult.isSuccess
                        )

                if (activeBaseline == null) {
                    Log.e(
                        "MOTION_FLOW",
                        """
                        No valid motion baseline is available.
                        Hand scoring is disabled for this entry.
                        """.trimIndent()
                    )

                    distressManager
                        .clearFormHandScore()

                    return@launch
                }

                logActiveBaseline(
                    baseline = activeBaseline
                )

                /*
                 * The profile used for the current form entry
                 * remains fixed.
                 *
                 * A successful accumulated update is saved for
                 * the next entry and does not replace this
                 * active profile mid-session.
                 */
                val activeProfile =
                    activeBaseline.profile

                val shouldCollectAccumulatedUpdate =
                    loadedBaseline != null

                motionManager.startContinuous()

                val continuousStartTimeMillis =
                    SystemClock.elapsedRealtime()

                var baselineUpdateHandled =
                    !shouldCollectAccumulatedUpdate

                try {
                    delay(
                        LIVE_ANALYSIS_INTERVAL_MILLIS
                    )

                    while (isActive) {

                        /*
                         * The five-second live window is always
                         * analyzed first.
                         *
                         * Therefore, tremor occurring during the
                         * first ten seconds is still detected for
                         * the current form.
                         */
                        val current =
                            motionManager.snapshot(
                                windowSeconds =
                                    LIVE_WINDOW_SECONDS
                            )

                        analyzeCurrentWindow(
                            profile =
                                activeProfile,

                            current =
                                current
                        )

                        val continuousElapsedMillis =
                            SystemClock.elapsedRealtime() -
                                    continuousStartTimeMillis

                        /*
                         * A saved baseline already existed when
                         * this entry began.
                         *
                         * After ten seconds, inspect the same
                         * continuously collected sensor data as
                         * a candidate baseline update.
                         */
                        if (
                            !baselineUpdateHandled &&
                            continuousElapsedMillis >=
                            BASELINE_SESSION_MILLIS
                        ) {
                            baselineUpdateHandled =
                                true

                            val updateCandidate =
                                motionManager.snapshot(
                                    windowSeconds =
                                        BASELINE_SESSION_SECONDS
                                )

                            processAccumulatedBaselineCandidate(
                                existingBaseline =
                                    activeBaseline,

                                candidate =
                                    updateCandidate
                            )
                        }

                        delay(
                            LIVE_ANALYSIS_INTERVAL_MILLIS
                        )
                    }

                } finally {
                    motionManager.stopContinuous()
                }
            }
    }


    /*
     * Voice recording changes only where completed hand scores
     * are stored. The analysis itself remains identical.
     */
    private fun startVoiceRecordingLifecycleListener(
        scope: CoroutineScope
    ) {
        recordingLifecycleJob =
            scope.launch {

                launch {
                    distressManager
                        .voiceRecordingStarted
                        .collect {

                            voiceRecordingHandScores.clear()

                            isCollectingVoiceRecordingHandScores =
                                true

                            Log.d(
                                "VOICE_HAND_SESSION",
                                "Started collecting completed hand scores"
                            )
                        }
                }

                launch {
                    distressManager
                        .voiceRecordingStopped
                        .collect {

                            isCollectingVoiceRecordingHandScores =
                                false

                            val handAverage =
                                voiceRecordingHandScores
                                    .takeIf { scores ->
                                        scores.isNotEmpty()
                                    }
                                    ?.average()

                            Log.d(
                                "VOICE_HAND_SESSION",
                                """
                                Hand recording session completed.
                                scores=$voiceRecordingHandScores
                                handAvailable=${handAverage != null}
                                average=$handAverage
                                """.trimIndent()
                            )

                            distressManager
                                .submitVoiceRecordingHandAverage(
                                    average =
                                        handAverage
                                )

                            voiceRecordingHandScores.clear()
                        }
                }
            }
    }


    /*
     * Loads the single fixed-size accumulated profile.
     */
    private suspend fun loadBaselineWithTimeout():
            Result<MotionBaselineData?> {

        return withTimeoutOrNull(
            timeMillis =
                BASELINE_LOAD_TIMEOUT_MILLIS
        ) {
            suspendCancellableCoroutine { continuation ->

                baselineRepository.getBaseline(
                    onSuccess = { baseline ->

                        if (continuation.isActive) {
                            continuation.resume(
                                Result.success(
                                    baseline
                                )
                            )
                        }
                    },

                    onFailure = { error ->

                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(
                                    error
                                )
                            )
                        }
                    }
                )
            }
        } ?: Result.failure(
            IllegalStateException(
                "Motion baseline loading timed out"
            )
        )
    }


    /*
     * Creates the first personal baseline.
     *
     * The user may hold the phone naturally. Small movement or
     * one grip adjustment does not automatically invalidate
     * the session.
     *
     * Only unreliable data or especially clear sustained
     * rhythmic motion causes another attempt.
     */
    private suspend fun createInitialBaseline(
        canPersist: Boolean
    ): MotionBaselineData? {

        for (
        attempt in
        1..MAX_INITIAL_BASELINE_ATTEMPTS
        ) {
            Log.d(
                "MOTION_FLOW",
                "Starting initial baseline attempt $attempt"
            )

            motionManager.start()

            delay(
                BASELINE_SESSION_MILLIS
            )

            val candidate =
                motionManager.stopAndAnalyze()

            val windows =
                createBaselineWindowSummaries(
                    candidate =
                        candidate
                )

            Log.d(
                "MOTION_BASELINE_CANDIDATE",
                """
                type=initial
                attempt=$attempt
                reliable=${candidate.isReliable}
                duration=${candidate.durationSeconds}
                createdWindows=${windows.size}
                accelerationP95=${candidate.accelerationP95}
                gyroscopeP95=${candidate.gyroscopeP95}
                """.trimIndent()
            )

            if (
                !candidate.isReliable ||
                candidate.durationSeconds <
                MIN_BASELINE_SESSION_DURATION_SECONDS ||
                windows.size !=
                BASELINE_WINDOW_COUNT
            ) {
                Log.d(
                    "MOTION_FLOW",
                    "Initial baseline attempt $attempt was unreliable"
                )

                continue
            }

            if (
                containsClearInitialTremor(
                    candidate =
                        candidate,

                    windows =
                        windows
                )
            ) {
                Log.d(
                    "MOTION_FLOW",
                    """
                    Initial baseline attempt $attempt contained
                    clear sustained periodic motion and was rejected.
                    """.trimIndent()
                )

                continue
            }

            val profile =
                calculateBaselineProfile(
                    windows =
                        windows,

                    validSessionCount =
                        1
                )

            val baseline =
                MotionBaselineData(
                    profile =
                        profile
                )

            if (canPersist) {
                baselineRepository
                    .saveInitialBaseline(
                        baselineData =
                            baseline,

                        onSuccess = {
                            Log.d(
                                "MOTION_FLOW",
                                "Initial accumulated baseline saved"
                            )
                        },

                        onFailure = { error ->
                            Log.e(
                                "MOTION_FLOW",
                                "Failed to save initial baseline",
                                error
                            )
                        }
                    )
            } else {
                Log.d(
                    "MOTION_FLOW",
                    """
                    Initial baseline is being used locally.
                    It was not saved because loading Firebase failed.
                    """.trimIndent()
                )
            }

            return baseline
        }

        return null
    }


    /*
     * Evaluates the additional ten seconds collected when a
     * saved baseline already existed.
     *
     * Rejection does not affect the current form and does not
     * remove or modify the saved baseline.
     */
    private fun processAccumulatedBaselineCandidate(
        existingBaseline: MotionBaselineData,
        candidate: MotionAnalysisResult
    ) {
        val newWindows =
            createBaselineWindowSummaries(
                candidate =
                    candidate
            )

        Log.d(
            "MOTION_BASELINE_CANDIDATE",
            """
            type=accumulated-update
            reliable=${candidate.isReliable}
            duration=${candidate.durationSeconds}
            createdWindows=${newWindows.size}
            existingSessions=${existingBaseline.profile.validSessionCount}
            existingWindows=${existingBaseline.profile.totalWindowCount}
            """.trimIndent()
        )

        if (
            !candidate.isReliable ||
            candidate.durationSeconds <
            MIN_BASELINE_SESSION_DURATION_SECONDS ||
            newWindows.size !=
            BASELINE_WINDOW_COUNT
        ) {
            Log.d(
                "MOTION_BASELINE_UPDATE",
                """
                Candidate update was not reliable.
                Existing Firebase baseline remains unchanged.
                """.trimIndent()
            )

            return
        }

        /*
         * A candidate containing current tremor may still have
         * produced live scores during the first ten seconds.
         *
         * It is rejected only as learning data.
         */
        if (
            containsTremorComparedWithExistingProfile(
                profile =
                    existingBaseline.profile,

                candidate =
                    candidate,

                windows =
                    newWindows
            )
        ) {
            Log.d(
                "MOTION_BASELINE_UPDATE",
                """
                Candidate contained sustained rhythmic tremor.
                Live detection remains valid, but Firebase was
                not updated.
                """.trimIndent()
            )

            return
        }

        if (
            containsSustainedExtremeMovement(
                profile =
                    existingBaseline.profile,

                windows =
                    newWindows
            )
        ) {
            Log.d(
                "MOTION_BASELINE_UPDATE",
                """
                Candidate contained strong movement throughout
                most of the session. Firebase was not updated.
                """.trimIndent()
            )

            return
        }

        val updatedProfile =
            mergeBaselineProfile(
                existingProfile =
                    existingBaseline.profile,

                newWindows =
                    newWindows
            )

        baselineRepository
            .updateBaseline(
                updatedProfile =
                    updatedProfile,

                onSuccess = {
                    Log.d(
                        "MOTION_BASELINE_UPDATE",
                        """
                        Fixed-size baseline updated successfully.
                        totalSessions=${updatedProfile.validSessionCount}
                        totalWindows=${updatedProfile.totalWindowCount}
                        totalSeconds=${updatedProfile.totalBaselineSeconds}
                        """.trimIndent()
                    )
                },

                onFailure = { error ->
                    Log.e(
                        "MOTION_BASELINE_UPDATE",
                        """
                        Failed to update the fixed-size baseline.
                        Existing baseline remains available.
                        """.trimIndent(),
                        error
                    )
                }
            )
    }


    /*
     * Divides one ten-second candidate into five
     * non-overlapping two-second windows.
     */
    private fun createBaselineWindowSummaries(
        candidate: MotionAnalysisResult
    ): List<MotionBaselineWindowSummary> {

        if (
            !candidate.isReliable ||
            candidate.durationSeconds <
            MIN_BASELINE_SESSION_DURATION_SECONDS
        ) {
            return emptyList()
        }

        val summaries =
            mutableListOf<MotionBaselineWindowSummary>()

        for (
        windowIndex in
        0 until BASELINE_WINDOW_COUNT
        ) {
            val startSeconds =
                windowIndex *
                        BASELINE_WINDOW_SECONDS

            val window =
                sliceMotionWindow(
                    source =
                        candidate,

                    startSeconds =
                        startSeconds,

                    requestedDurationSeconds =
                        BASELINE_WINDOW_SECONDS
                ) ?: return emptyList()

            val summary =
                createBaselineWindowSummary(
                    window =
                        window
                ) ?: return emptyList()

            summaries.add(
                summary
            )
        }

        return summaries
    }


    private fun createBaselineWindowSummary(
        window: MotionAnalysisResult
    ): MotionBaselineWindowSummary? {

        if (
            !window.isReliable ||
            window.durationSeconds <
            MIN_TWO_SECOND_WINDOW_DURATION_SECONDS
        ) {
            return null
        }

        val spectrum =
            analyzeSpectrum(
                result =
                    window
            )

        if (
            spectrum.sampleCount <= 0 ||
            !spectrum.bandAveragePower.isFinite() ||
            !spectrum.peakNeighborhoodPower.isFinite()
        ) {
            return null
        }

        return MotionBaselineWindowSummary(
            durationSeconds =
                window.durationSeconds,

            accelerationP95 =
                window.accelerationP95.toDouble(),

            gyroscopeP95 =
                window.gyroscopeP95.toDouble(),

            accelerationVariation =
                window.accelerationVariation.toDouble(),

            gyroscopeVariation =
                window.gyroscopeVariation.toDouble(),

            peakFrequencyHz =
                spectrum.peakFrequencyHz,

            bandAveragePower =
                spectrum.bandAveragePower,

            peakNeighborhoodPower =
                spectrum.peakNeighborhoodPower,

            concentrationRatio =
                spectrum.concentrationRatio,

            narrowbandRatio =
                spectrum.narrowbandRatio,

            rhythmicEnergyShare =
                spectrum.rhythmicEnergyShare
        )
    }


    /*
     * Rejects only especially clear periodic movement during
     * the first baseline, when no previous profile exists.
     */
    private fun containsClearInitialTremor(
        candidate: MotionAnalysisResult,
        windows: List<MotionBaselineWindowSummary>
    ): Boolean {

        val wholeSpectrum =
            analyzeSpectrum(
                result =
                    candidate
            )

        val wholePeriodic =
            wholeSpectrum.peakFrequencyHz in
                    TREMOR_MIN_HZ..
                    TREMOR_MAX_HZ &&
                    wholeSpectrum.concentrationRatio >=
                    INITIAL_REJECTION_CONCENTRATION &&
                    wholeSpectrum.narrowbandRatio >=
                    INITIAL_REJECTION_NARROWBAND_RATIO &&
                    wholeSpectrum.rhythmicEnergyShare >=
                    INITIAL_REJECTION_RHYTHMIC_SHARE

        val rhythmicWindows =
            windows.filter { window ->

                window.peakFrequencyHz in
                        TREMOR_MIN_HZ..
                        TREMOR_MAX_HZ &&
                        window.concentrationRatio >=
                        INITIAL_WINDOW_REJECTION_CONCENTRATION &&
                        window.narrowbandRatio >=
                        INITIAL_WINDOW_REJECTION_NARROWBAND_RATIO &&
                        window.rhythmicEnergyShare >=
                        INITIAL_WINDOW_REJECTION_RHYTHMIC_SHARE
            }

        val frequencySpread =
            calculateFrequencySpread(
                frequencies =
                    rhythmicWindows.map { window ->
                        window.peakFrequencyHz
                    }
            )

        val windowsPeriodic =
            rhythmicWindows.size >=
                    MIN_BASELINE_TREMOR_WINDOWS &&
                    frequencySpread <=
                    MAX_BASELINE_TREMOR_FREQUENCY_SPREAD_HZ

        Log.d(
            "MOTION_INITIAL_BASELINE_CHECK",
            """
            wholePeriodic=$wholePeriodic
            rhythmicWindows=${rhythmicWindows.size}
            frequencySpreadHz=$frequencySpread
            wholeConcentration=${wholeSpectrum.concentrationRatio}
            wholeNarrowband=${wholeSpectrum.narrowbandRatio}
            wholeRhythmicShare=${wholeSpectrum.rhythmicEnergyShare}
            """.trimIndent()
        )

        return wholePeriodic &&
                windowsPeriodic
    }


    /*
     * Checks whether an accumulated candidate contains tremor
     * relative to the already learned personal baseline.
     */
    private fun containsTremorComparedWithExistingProfile(
        profile: MotionBaselineProfile,
        candidate: MotionAnalysisResult,
        windows: List<MotionBaselineWindowSummary>
    ): Boolean {

        val wholeSpectrum =
            analyzeSpectrum(
                result =
                    candidate
            )

        val upperBandPower =
            personalUpperNormal(
                mean =
                    profile.bandAveragePowerMean,

                m2 =
                    profile.bandAveragePowerM2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperPeakPower =
            personalUpperNormal(
                mean =
                    profile.peakNeighborhoodPowerMean,

                m2 =
                    profile.peakNeighborhoodPowerM2,

                sampleCount =
                    profile.totalWindowCount
            )

        val personalRhythmicThreshold =
            personalRhythmicThreshold(
                profile =
                    profile,

                genericMinimum =
                    MIN_WHOLE_RHYTHMIC_SHARE
            )

        val wholePowerHigh =
            wholeSpectrum.bandAveragePower >
                    upperBandPower ||
                    wholeSpectrum.peakNeighborhoodPower >
                    upperPeakPower

        val wholePeriodic =
            wholeSpectrum.peakFrequencyHz in
                    TREMOR_MIN_HZ..
                    TREMOR_MAX_HZ &&
                    wholeSpectrum.concentrationRatio >=
                    MIN_WHOLE_CONCENTRATION &&
                    wholeSpectrum.narrowbandRatio >=
                    MIN_WHOLE_NARROWBAND_RATIO &&
                    wholeSpectrum.rhythmicEnergyShare >=
                    personalRhythmicThreshold &&
                    wholePowerHigh

        val temporalRhythmicThreshold =
            personalRhythmicThreshold(
                profile =
                    profile,

                genericMinimum =
                    MIN_TEMPORAL_RHYTHMIC_SHARE
            )

        val rhythmicWindows =
            windows.filter { window ->

                val powerHigh =
                    window.bandAveragePower >
                            upperBandPower ||
                            window.peakNeighborhoodPower >
                            upperPeakPower

                window.peakFrequencyHz in
                        TREMOR_MIN_HZ..
                        TREMOR_MAX_HZ &&
                        window.concentrationRatio >=
                        MIN_TEMPORAL_CONCENTRATION &&
                        window.narrowbandRatio >=
                        MIN_TEMPORAL_NARROWBAND_RATIO &&
                        window.rhythmicEnergyShare >=
                        temporalRhythmicThreshold &&
                        powerHigh
            }

        val frequencySpread =
            calculateFrequencySpread(
                frequencies =
                    rhythmicWindows.map { window ->
                        window.peakFrequencyHz
                    }
            )

        val windowsPeriodic =
            rhythmicWindows.size >=
                    MIN_BASELINE_TREMOR_WINDOWS &&
                    frequencySpread <=
                    MAX_BASELINE_TREMOR_FREQUENCY_SPREAD_HZ

        Log.d(
            "MOTION_BASELINE_TREMOR_CHECK",
            """
            wholePeriodic=$wholePeriodic
            rhythmicWindows=${rhythmicWindows.size}
            frequencySpreadHz=$frequencySpread
            upperBandPower=$upperBandPower
            upperPeakPower=$upperPeakPower
            """.trimIndent()
        )

        return wholePeriodic &&
                windowsPeriodic
    }


    /*
     * Rejects only when strong non-baseline movement appears
     * in most of the ten-second session.
     *
     * One grip change or a few ordinary movements do not
     * trigger this rejection.
     */
    private fun containsSustainedExtremeMovement(
        profile: MotionBaselineProfile,
        windows: List<MotionBaselineWindowSummary>
    ): Boolean {

        val upperAcceleration =
            personalUpperNormal(
                mean =
                    profile.accelerationP95Mean,

                m2 =
                    profile.accelerationP95M2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperGyroscope =
            personalUpperNormal(
                mean =
                    profile.gyroscopeP95Mean,

                m2 =
                    profile.gyroscopeP95M2,

                sampleCount =
                    profile.totalWindowCount
            )

        val extremeWindowCount =
            windows.count { window ->

                window.accelerationP95 >
                        upperAcceleration *
                        SUSTAINED_MOVEMENT_NORMAL_MULTIPLIER &&
                        window.gyroscopeP95 >
                        upperGyroscope *
                        SUSTAINED_MOVEMENT_NORMAL_MULTIPLIER
            }

        return extremeWindowCount >=
                MIN_SUSTAINED_MOVEMENT_WINDOWS
    }


    /*
     * Creates the first fixed-size accumulated profile from
     * the five temporary two-second windows.
     */
    private fun calculateBaselineProfile(
        windows: List<MotionBaselineWindowSummary>,
        validSessionCount: Int
    ): MotionBaselineProfile {

        require(windows.isNotEmpty()) {
            "Cannot calculate a baseline profile without windows"
        }

        val accelerationP95 =
            calculateFeatureStatistics(
                values =
                    windows.map { window ->
                        window.accelerationP95
                    }
            )

        val gyroscopeP95 =
            calculateFeatureStatistics(
                values =
                    windows.map { window ->
                        window.gyroscopeP95
                    }
            )

        val accelerationVariation =
            calculateFeatureStatistics(
                values =
                    windows.map { window ->
                        window.accelerationVariation
                    }
            )

        val gyroscopeVariation =
            calculateFeatureStatistics(
                values =
                    windows.map { window ->
                        window.gyroscopeVariation
                    }
            )

        val bandAveragePower =
            calculateFeatureStatistics(
                values =
                    windows.map { window ->
                        window.bandAveragePower
                    }
            )

        val peakNeighborhoodPower =
            calculateFeatureStatistics(
                values =
                    windows.map { window ->
                        window.peakNeighborhoodPower
                    }
            )

        val rhythmicEnergyShare =
            calculateFeatureStatistics(
                values =
                    windows.map { window ->
                        window.rhythmicEnergyShare
                    }
            )

        return MotionBaselineProfile(
            accelerationP95Mean =
                accelerationP95.mean,

            accelerationP95M2 =
                accelerationP95.m2,

            gyroscopeP95Mean =
                gyroscopeP95.mean,

            gyroscopeP95M2 =
                gyroscopeP95.m2,

            accelerationVariationMean =
                accelerationVariation.mean,

            accelerationVariationM2 =
                accelerationVariation.m2,

            gyroscopeVariationMean =
                gyroscopeVariation.mean,

            gyroscopeVariationM2 =
                gyroscopeVariation.m2,

            bandAveragePowerMean =
                bandAveragePower.mean,

            bandAveragePowerM2 =
                bandAveragePower.m2,

            peakNeighborhoodPowerMean =
                peakNeighborhoodPower.mean,

            peakNeighborhoodPowerM2 =
                peakNeighborhoodPower.m2,

            rhythmicEnergyShareMean =
                rhythmicEnergyShare.mean,

            rhythmicEnergyShareM2 =
                rhythmicEnergyShare.m2,

            totalBaselineSeconds =
                windows.sumOf { window ->
                    window.durationSeconds
                },

            validSessionCount =
                validSessionCount,

            totalWindowCount =
                windows.size
        )
    }


    /*
     * Merges one accepted ten-second session into the
     * fixed-size accumulated profile.
     *
     * Historical windows are not required because the mean,
     * M2 and sample count contain the information needed for
     * an exact aggregate merge.
     */
    private fun mergeBaselineProfile(
        existingProfile: MotionBaselineProfile,
        newWindows: List<MotionBaselineWindowSummary>
    ): MotionBaselineProfile {

        require(
            existingProfile.totalWindowCount > 0
        ) {
            "Existing baseline must contain at least one window"
        }

        require(newWindows.isNotEmpty()) {
            "Cannot merge an empty baseline session"
        }

        val existingCount =
            existingProfile.totalWindowCount

        val accelerationP95 =
            mergeFeatureStatistics(
                existingMean =
                    existingProfile.accelerationP95Mean,

                existingM2 =
                    existingProfile.accelerationP95M2,

                existingCount =
                    existingCount,

                newValues =
                    newWindows.map { window ->
                        window.accelerationP95
                    }
            )

        val gyroscopeP95 =
            mergeFeatureStatistics(
                existingMean =
                    existingProfile.gyroscopeP95Mean,

                existingM2 =
                    existingProfile.gyroscopeP95M2,

                existingCount =
                    existingCount,

                newValues =
                    newWindows.map { window ->
                        window.gyroscopeP95
                    }
            )

        val accelerationVariation =
            mergeFeatureStatistics(
                existingMean =
                    existingProfile.accelerationVariationMean,

                existingM2 =
                    existingProfile.accelerationVariationM2,

                existingCount =
                    existingCount,

                newValues =
                    newWindows.map { window ->
                        window.accelerationVariation
                    }
            )

        val gyroscopeVariation =
            mergeFeatureStatistics(
                existingMean =
                    existingProfile.gyroscopeVariationMean,

                existingM2 =
                    existingProfile.gyroscopeVariationM2,

                existingCount =
                    existingCount,

                newValues =
                    newWindows.map { window ->
                        window.gyroscopeVariation
                    }
            )

        val bandAveragePower =
            mergeFeatureStatistics(
                existingMean =
                    existingProfile.bandAveragePowerMean,

                existingM2 =
                    existingProfile.bandAveragePowerM2,

                existingCount =
                    existingCount,

                newValues =
                    newWindows.map { window ->
                        window.bandAveragePower
                    }
            )

        val peakNeighborhoodPower =
            mergeFeatureStatistics(
                existingMean =
                    existingProfile.peakNeighborhoodPowerMean,

                existingM2 =
                    existingProfile.peakNeighborhoodPowerM2,

                existingCount =
                    existingCount,

                newValues =
                    newWindows.map { window ->
                        window.peakNeighborhoodPower
                    }
            )

        val rhythmicEnergyShare =
            mergeFeatureStatistics(
                existingMean =
                    existingProfile.rhythmicEnergyShareMean,

                existingM2 =
                    existingProfile.rhythmicEnergyShareM2,

                existingCount =
                    existingCount,

                newValues =
                    newWindows.map { window ->
                        window.rhythmicEnergyShare
                    }
            )

        return MotionBaselineProfile(
            accelerationP95Mean =
                accelerationP95.mean,

            accelerationP95M2 =
                accelerationP95.m2,

            gyroscopeP95Mean =
                gyroscopeP95.mean,

            gyroscopeP95M2 =
                gyroscopeP95.m2,

            accelerationVariationMean =
                accelerationVariation.mean,

            accelerationVariationM2 =
                accelerationVariation.m2,

            gyroscopeVariationMean =
                gyroscopeVariation.mean,

            gyroscopeVariationM2 =
                gyroscopeVariation.m2,

            bandAveragePowerMean =
                bandAveragePower.mean,

            bandAveragePowerM2 =
                bandAveragePower.m2,

            peakNeighborhoodPowerMean =
                peakNeighborhoodPower.mean,

            peakNeighborhoodPowerM2 =
                peakNeighborhoodPower.m2,

            rhythmicEnergyShareMean =
                rhythmicEnergyShare.mean,

            rhythmicEnergyShareM2 =
                rhythmicEnergyShare.m2,

            totalBaselineSeconds =
                existingProfile.totalBaselineSeconds +
                        newWindows.sumOf { window ->
                            window.durationSeconds
                        },

            validSessionCount =
                existingProfile.validSessionCount +
                        1,

            totalWindowCount =
                existingCount +
                        newWindows.size
        )
    }


    /*
     * Analyzes one completed five-second live window.
     */
    private fun analyzeCurrentWindow(
        profile: MotionBaselineProfile,
        current: MotionAnalysisResult
    ) {

        if (!current.isReliable) {
            Log.d(
                "MOTION_SCORE",
                """
                Live hand window was unreliable.
                duration=${current.durationSeconds}
                """.trimIndent()
            )

            /*
             * Unavailable data is not considered calm.
             */
            if (
                !isCollectingVoiceRecordingHandScores
            ) {
                distressManager
                    .clearFormHandScore()
            }

            return
        }

        val evaluation =
            evaluateTremor(
                profile =
                    profile,

                current =
                    current
            )

        logTremorEvaluation(
            current =
                current,

            evaluation =
                evaluation
        )

        publishCompletedHandWindow(
            score =
                evaluation.severity.score
        )
    }


    /*
     * Stage 1:
     * Determine whether movement is periodic tremor.
     *
     * Stage 2:
     * Only after confirmation, calculate personal severity.
     */
    private fun evaluateTremor(
        profile: MotionBaselineProfile,
        current: MotionAnalysisResult
    ): TremorEvaluation {

        val spectrum =
            analyzeSpectrum(
                result =
                    current
            )

        val upperBandPower =
            personalUpperNormal(
                mean =
                    profile.bandAveragePowerMean,

                m2 =
                    profile.bandAveragePowerM2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperPeakPower =
            personalUpperNormal(
                mean =
                    profile.peakNeighborhoodPowerMean,

                m2 =
                    profile.peakNeighborhoodPowerM2,

                sampleCount =
                    profile.totalWindowCount
            )

        val wholeRhythmicThreshold =
            personalRhythmicThreshold(
                profile =
                    profile,

                genericMinimum =
                    MIN_WHOLE_RHYTHMIC_SHARE
            )

        val wholePeakInBand =
            spectrum.peakFrequencyHz in
                    TREMOR_MIN_HZ..
                    TREMOR_MAX_HZ

        val wholeConcentrated =
            spectrum.concentrationRatio >=
                    MIN_WHOLE_CONCENTRATION

        val wholeNarrowband =
            spectrum.narrowbandRatio >=
                    MIN_WHOLE_NARROWBAND_RATIO

        val wholeRhythmic =
            spectrum.rhythmicEnergyShare >=
                    wholeRhythmicThreshold

        val wholePowerHigh =
            spectrum.bandAveragePower >
                    upperBandPower ||
                    spectrum.peakNeighborhoodPower >
                    upperPeakPower

        val temporal =
            analyzeTemporalConsistency(
                profile =
                    profile,

                current =
                    current,

                wholeWindowPeakHz =
                    spectrum.peakFrequencyHz,

                upperBandPower =
                    upperBandPower,

                upperPeakPower =
                    upperPeakPower
            )

        val tremorConfirmed =
            wholePeakInBand &&
                    wholeConcentrated &&
                    wholeNarrowband &&
                    wholeRhythmic &&
                    wholePowerHigh &&
                    temporal.hasTemporalCoverage &&
                    temporal.frequencyStable &&
                    temporal.powerStable &&
                    !temporal.isBurstDominated

        val severity =
            if (tremorConfirmed) {
                calculatePersonalSeverity(
                    profile =
                        profile,

                    current =
                        current,

                    spectrum =
                        spectrum
                )
            } else {
                PersonalSeverityResult()
            }

        return TremorEvaluation(
            tremorConfirmed =
                tremorConfirmed,

            spectrum =
                spectrum,

            temporal =
                temporal,

            severity =
                severity,

            wholePeakInBand =
                wholePeakInBand,

            wholeConcentrated =
                wholeConcentrated,

            wholeNarrowband =
                wholeNarrowband,

            wholeRhythmic =
                wholeRhythmic,

            wholePowerHigh =
                wholePowerHigh,

            wholeRhythmicThreshold =
                wholeRhythmicThreshold,

            upperBandPower =
                upperBandPower,

            upperPeakPower =
                upperPeakPower
        )
    }


    /*
     * Checks four overlapping two-second windows using real
     * sensor timestamps.
     */
    private fun analyzeTemporalConsistency(
        profile: MotionBaselineProfile,
        current: MotionAnalysisResult,
        wholeWindowPeakHz: Double,
        upperBandPower: Double,
        upperPeakPower: Double
    ): TemporalTremorResult {

        val windowPeakFrequencies =
            mutableListOf<Double>()

        val windowConcentrations =
            mutableListOf<Double>()

        val windowNarrowbandRatios =
            mutableListOf<Double>()

        val windowRhythmicShares =
            mutableListOf<Double>()

        val windowBandPowers =
            mutableListOf<Double>()

        val windowPeakPowers =
            mutableListOf<Double>()

        val candidateFrequencies =
            mutableListOf<Double>()

        val candidatePowers =
            mutableListOf<Double>()

        val temporalRhythmicThreshold =
            personalRhythmicThreshold(
                profile =
                    profile,

                genericMinimum =
                    MIN_TEMPORAL_RHYTHMIC_SHARE
            )

        var candidateWindowCount = 0

        for (
        windowIndex in
        0 until TEMPORAL_WINDOW_COUNT
        ) {
            val startSeconds =
                windowIndex *
                        TEMPORAL_WINDOW_STEP_SECONDS

            val window =
                sliceMotionWindow(
                    source =
                        current,

                    startSeconds =
                        startSeconds,

                    requestedDurationSeconds =
                        TEMPORAL_WINDOW_SECONDS
                )

            if (window == null) {
                windowPeakFrequencies.add(0.0)
                windowConcentrations.add(0.0)
                windowNarrowbandRatios.add(0.0)
                windowRhythmicShares.add(0.0)
                windowBandPowers.add(0.0)
                windowPeakPowers.add(0.0)

                continue
            }

            val spectrum =
                analyzeSpectrum(
                    result =
                        window
                )

            windowPeakFrequencies.add(
                spectrum.peakFrequencyHz
            )

            windowConcentrations.add(
                spectrum.concentrationRatio
            )

            windowNarrowbandRatios.add(
                spectrum.narrowbandRatio
            )

            windowRhythmicShares.add(
                spectrum.rhythmicEnergyShare
            )

            windowBandPowers.add(
                spectrum.bandAveragePower
            )

            windowPeakPowers.add(
                spectrum.peakNeighborhoodPower
            )

            val peakInBand =
                spectrum.peakFrequencyHz in
                        TREMOR_MIN_HZ..
                        TREMOR_MAX_HZ

            val peakCloseToWholeWindow =
                wholeWindowPeakHz > 0.0 &&
                        abs(
                            spectrum.peakFrequencyHz -
                                    wholeWindowPeakHz
                        ) <=
                        TEMPORAL_PEAK_TOLERANCE_HZ

            val concentrated =
                spectrum.concentrationRatio >=
                        MIN_TEMPORAL_CONCENTRATION

            val narrowband =
                spectrum.narrowbandRatio >=
                        MIN_TEMPORAL_NARROWBAND_RATIO

            val rhythmic =
                spectrum.rhythmicEnergyShare >=
                        temporalRhythmicThreshold

            val powerHigh =
                spectrum.bandAveragePower >
                        upperBandPower ||
                        spectrum.peakNeighborhoodPower >
                        upperPeakPower

            val isCandidate =
                peakInBand &&
                        peakCloseToWholeWindow &&
                        concentrated &&
                        narrowband &&
                        rhythmic &&
                        powerHigh

            if (isCandidate) {
                candidateWindowCount += 1

                candidateFrequencies.add(
                    spectrum.peakFrequencyHz
                )

                candidatePowers.add(
                    spectrum.peakNeighborhoodPower
                )
            }
        }

        val hasTemporalCoverage =
            candidateWindowCount >=
                    MIN_TEMPORAL_CANDIDATE_WINDOWS

        val frequencySpread =
            calculateFrequencySpread(
                frequencies =
                    candidateFrequencies
            )

        val frequencyStable =
            hasTemporalCoverage &&
                    frequencySpread <=
                    MAX_TEMPORAL_FREQUENCY_SPREAD_HZ

        val powerRatio =
            calculatePowerRatio(
                values =
                    candidatePowers
            )

        val powerCoefficientOfVariation =
            calculateCoefficientOfVariation(
                values =
                    candidatePowers
            )

        val powerStable =
            hasTemporalCoverage &&
                    powerRatio <=
                    MAX_TEMPORAL_POWER_RATIO &&
                    powerCoefficientOfVariation <=
                    MAX_TEMPORAL_POWER_CV

        return TemporalTremorResult(
            candidateWindowCount =
                candidateWindowCount,

            hasTemporalCoverage =
                hasTemporalCoverage,

            frequencyStable =
                frequencyStable,

            powerStable =
                powerStable,

            isBurstDominated =
                current
                    .accelerationValues
                    .isBurstDominated(),

            candidateFrequencySpreadHz =
                frequencySpread,

            candidatePowerRatio =
                powerRatio,

            candidatePowerCoefficientOfVariation =
                powerCoefficientOfVariation,

            windowPeakFrequenciesHz =
                windowPeakFrequencies,

            windowConcentrations =
                windowConcentrations,

            windowNarrowbandRatios =
                windowNarrowbandRatios,

            windowRhythmicShares =
                windowRhythmicShares,

            windowBandPowers =
                windowBandPowers,

            windowPeakNeighborhoodPowers =
                windowPeakPowers
        )
    }


    /*
     * Calculates severity only after periodic tremor has been
     * confirmed.
     *
     * Logarithmic ratios make the score respond gradually
     * rather than jumping directly to 4 when one feature is
     * many times larger than baseline.
     */
    private fun calculatePersonalSeverity(
        profile: MotionBaselineProfile,
        current: MotionAnalysisResult,
        spectrum: TremorSpectrumResult
    ): PersonalSeverityResult {

        val upperAccelerationP95 =
            personalUpperNormal(
                mean =
                    profile.accelerationP95Mean,

                m2 =
                    profile.accelerationP95M2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperAccelerationVariation =
            personalUpperNormal(
                mean =
                    profile.accelerationVariationMean,

                m2 =
                    profile.accelerationVariationM2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperGyroscopeP95 =
            personalUpperNormal(
                mean =
                    profile.gyroscopeP95Mean,

                m2 =
                    profile.gyroscopeP95M2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperGyroscopeVariation =
            personalUpperNormal(
                mean =
                    profile.gyroscopeVariationMean,

                m2 =
                    profile.gyroscopeVariationM2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperBandPower =
            personalUpperNormal(
                mean =
                    profile.bandAveragePowerMean,

                m2 =
                    profile.bandAveragePowerM2,

                sampleCount =
                    profile.totalWindowCount
            )

        val upperPeakPower =
            personalUpperNormal(
                mean =
                    profile.peakNeighborhoodPowerMean,

                m2 =
                    profile.peakNeighborhoodPowerM2,

                sampleCount =
                    profile.totalWindowCount
            )

        /*
         * Use the stronger relative deviation from P95 or
         * variation for each physical sensor type.
         */
        val accelerationLevel =
            max(
                logarithmicExcessLevel(
                    currentValue =
                        current.accelerationP95.toDouble(),

                    upperNormalValue =
                        upperAccelerationP95
                ),

                logarithmicExcessLevel(
                    currentValue =
                        current.accelerationVariation.toDouble(),

                    upperNormalValue =
                        upperAccelerationVariation
                )
            )

        val gyroscopeLevel =
            max(
                logarithmicExcessLevel(
                    currentValue =
                        current.gyroscopeP95.toDouble(),

                    upperNormalValue =
                        upperGyroscopeP95
                ),

                logarithmicExcessLevel(
                    currentValue =
                        current.gyroscopeVariation.toDouble(),

                    upperNormalValue =
                        upperGyroscopeVariation
                )
            )

        /*
         * PSD represents squared amplitude.
         *
         * Dividing its logarithmic ratio by two converts it
         * approximately into an amplitude-level ratio.
         */
        val spectralLevel =
            (
                    max(
                        logarithmicExcessLevel(
                            currentValue =
                                spectrum.bandAveragePower,

                            upperNormalValue =
                                upperBandPower
                        ),

                        logarithmicExcessLevel(
                            currentValue =
                                spectrum.peakNeighborhoodPower,

                            upperNormalValue =
                                upperPeakPower
                        )
                    ) / 2.0
                    ).coerceAtMost(
                    MAX_SPECTRAL_SEVERITY_LEVEL
                )

        val severityIndex =
            accelerationLevel *
                    ACCELERATION_SEVERITY_WEIGHT +
                    gyroscopeLevel *
                    GYROSCOPE_SEVERITY_WEIGHT +
                    spectralLevel *
                    SPECTRAL_SEVERITY_WEIGHT

        val score =
            when {
                severityIndex <
                        SCORE_TWO_THRESHOLD -> 1

                severityIndex <
                        SCORE_THREE_THRESHOLD -> 2

                severityIndex <
                        SCORE_FOUR_THRESHOLD -> 3

                else -> 4
            }

        return PersonalSeverityResult(
            score =
                score,

            severityIndex =
                severityIndex,

            accelerationLevel =
                accelerationLevel,

            gyroscopeLevel =
                gyroscopeLevel,

            spectralLevel =
                spectralLevel,

            upperAccelerationP95 =
                upperAccelerationP95,

            upperAccelerationVariation =
                upperAccelerationVariation,

            upperGyroscopeP95 =
                upperGyroscopeP95,

            upperGyroscopeVariation =
                upperGyroscopeVariation,

            upperBandPower =
                upperBandPower,

            upperPeakPower =
                upperPeakPower
        )
    }


    private fun analyzeSpectrum(
        result: MotionAnalysisResult
    ): TremorSpectrumResult {

        return TremorSpectrumAnalyzer
            .analyzeAxes(
                xValues =
                    result.accelerationXValues,

                yValues =
                    result.accelerationYValues,

                zValues =
                    result.accelerationZValues,

                timestampsNs =
                    result.accelerationTimestampsNs,

                minHz =
                    TREMOR_MIN_HZ,

                maxHz =
                    TREMOR_MAX_HZ
            )
    }


    /*
     * Creates a time-based motion sub-window.
     *
     * Accelerometer and gyroscope samples are sliced using
     * their actual monotonic timestamps.
     */
    private fun sliceMotionWindow(
        source: MotionAnalysisResult,
        startSeconds: Double,
        requestedDurationSeconds: Double
    ): MotionAnalysisResult? {

        if (
            startSeconds < 0.0 ||
            requestedDurationSeconds <= 0.0 ||
            source.accelerationTimestampsNs.size < 2 ||
            source.gyroscopeTimestampsNs.size < 2
        ) {
            return null
        }

        val commonStartTimestampNs =
            maxOf(
                source
                    .accelerationTimestampsNs
                    .first(),

                source
                    .gyroscopeTimestampsNs
                    .first()
            )

        val commonEndTimestampNs =
            minOf(
                source
                    .accelerationTimestampsNs
                    .last(),

                source
                    .gyroscopeTimestampsNs
                    .last()
            )

        val requestedStartTimestampNs =
            commonStartTimestampNs +
                    (
                            startSeconds *
                                    1_000_000_000.0
                            ).toLong()

        val requestedEndTimestampNs =
            requestedStartTimestampNs +
                    (
                            requestedDurationSeconds *
                                    1_000_000_000.0
                            ).toLong()

        val actualEndTimestampNs =
            minOf(
                requestedEndTimestampNs,
                commonEndTimestampNs
            )

        val actualRequestedDuration =
            (
                    actualEndTimestampNs -
                            requestedStartTimestampNs
                    ) / 1_000_000_000.0

        if (
            requestedStartTimestampNs >=
            commonEndTimestampNs ||
            actualRequestedDuration <
            requestedDurationSeconds * 0.85
        ) {
            return null
        }

        val accelerationRange =
            findTimestampRange(
                timestampsNs =
                    source.accelerationTimestampsNs,

                startTimestampNs =
                    requestedStartTimestampNs,

                endTimestampNs =
                    actualEndTimestampNs
            ) ?: return null

        val gyroscopeRange =
            findTimestampRange(
                timestampsNs =
                    source.gyroscopeTimestampsNs,

                startTimestampNs =
                    requestedStartTimestampNs,

                endTimestampNs =
                    actualEndTimestampNs
            ) ?: return null

        val accelerationValues =
            source.accelerationValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationXValues =
            source.accelerationXValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationYValues =
            source.accelerationYValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationZValues =
            source.accelerationZValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationTimestamps =
            source.accelerationTimestampsNs
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val gyroscopeValues =
            source.gyroscopeValues
                .subList(
                    gyroscopeRange.first,
                    gyroscopeRange.last + 1
                )
                .toList()

        val gyroscopeTimestamps =
            source.gyroscopeTimestampsNs
                .subList(
                    gyroscopeRange.first,
                    gyroscopeRange.last + 1
                )
                .toList()

        val accelerationDuration =
            accelerationTimestamps
                .durationSeconds()

        val gyroscopeDuration =
            gyroscopeTimestamps
                .durationSeconds()

        val durationSeconds =
            minOf(
                accelerationDuration,
                gyroscopeDuration
            )

        val isReliable =
            accelerationValues.isNotEmpty() &&
                    gyroscopeValues.isNotEmpty() &&
                    durationSeconds >=
                    requestedDurationSeconds * 0.85

        return MotionAnalysisResult(
            durationSeconds =
                durationSeconds,

            averageAcceleration =
                accelerationValues.averageOrZero(),

            maxAcceleration =
                accelerationValues.maxOrNull()
                    ?: 0f,

            accelerationVariation =
                accelerationValues.variation(),

            accelerationP95 =
                accelerationValues.percentile95(),

            averageGyroscope =
                gyroscopeValues.averageOrZero(),

            maxGyroscope =
                gyroscopeValues.maxOrNull()
                    ?: 0f,

            gyroscopeVariation =
                gyroscopeValues.variation(),

            gyroscopeP95 =
                gyroscopeValues.percentile95(),

            isReliable =
                isReliable,

            accelerationValues =
                accelerationValues,

            gyroscopeValues =
                gyroscopeValues,

            accelerationXValues =
                accelerationXValues,

            accelerationYValues =
                accelerationYValues,

            accelerationZValues =
                accelerationZValues,

            accelerationTimestampsNs =
                accelerationTimestamps,

            gyroscopeTimestampsNs =
                gyroscopeTimestamps
        )
    }


    private fun findTimestampRange(
        timestampsNs: List<Long>,
        startTimestampNs: Long,
        endTimestampNs: Long
    ): IntRange? {

        val firstIndex =
            timestampsNs.indexOfFirst { timestamp ->
                timestamp >=
                        startTimestampNs
            }

        val lastIndex =
            timestampsNs.indexOfLast { timestamp ->
                timestamp <=
                        endTimestampNs
            }

        if (
            firstIndex < 0 ||
            lastIndex < firstIndex
        ) {
            return null
        }

        return firstIndex..lastIndex
    }


    /*
     * Upper boundary of natural personal motion derived from
     * the accumulated mean and sample standard deviation.
     */
    private fun personalUpperNormal(
        mean: Double,
        m2: Double,
        sampleCount: Int
    ): Double {

        val standardDeviation =
            calculateStandardDeviation(
                m2 =
                    m2,

                sampleCount =
                    sampleCount
            )

        val relativeScaleFloor =
            abs(mean) *
                    MIN_RELATIVE_NORMAL_SCALE

        val scale =
            max(
                standardDeviation,
                max(
                    relativeScaleFloor,
                    MIN_ABSOLUTE_NORMAL_SCALE
                )
            )

        return mean +
                PERSONAL_NORMAL_STANDARD_DEVIATION_MULTIPLIER *
                scale
    }


    private fun personalRhythmicThreshold(
        profile: MotionBaselineProfile,
        genericMinimum: Double
    ): Double {

        val personalUpper =
            personalUpperNormal(
                mean =
                    profile.rhythmicEnergyShareMean,

                m2 =
                    profile.rhythmicEnergyShareM2,

                sampleCount =
                    profile.totalWindowCount
            )

        return max(
            genericMinimum,
            personalUpper
        ).coerceAtMost(
            MAX_PERSONAL_RHYTHMIC_THRESHOLD
        )
    }


    /*
     * Returns log2(current / upperNormal) when current exceeds
     * the personal upper-normal value.
     */
    private fun logarithmicExcessLevel(
        currentValue: Double,
        upperNormalValue: Double
    ): Double {

        if (
            !currentValue.isFinite() ||
            !upperNormalValue.isFinite() ||
            currentValue <=
            upperNormalValue ||
            upperNormalValue <= 0.0
        ) {
            return 0.0
        }

        val ratio =
            currentValue /
                    upperNormalValue

        return ln(ratio) /
                ln(2.0)
    }


    private fun calculateFrequencySpread(
        frequencies: List<Double>
    ): Double {

        val validFrequencies =
            frequencies.filter { frequency ->
                frequency.isFinite() &&
                        frequency > 0.0
            }

        if (validFrequencies.size < 2) {
            return Double.POSITIVE_INFINITY
        }

        return validFrequencies.maxOrNull()!! -
                validFrequencies.minOrNull()!!
    }


    private fun calculatePowerRatio(
        values: List<Double>
    ): Double {

        val positiveValues =
            values.filter { value ->
                value.isFinite() &&
                        value > 0.0
            }

        if (positiveValues.size < 2) {
            return Double.POSITIVE_INFINITY
        }

        val minimum =
            positiveValues.minOrNull()
                ?: return Double.POSITIVE_INFINITY

        val maximum =
            positiveValues.maxOrNull()
                ?: return Double.POSITIVE_INFINITY

        return maximum /
                minimum
    }


    private fun calculateCoefficientOfVariation(
        values: List<Double>
    ): Double {

        val validValues =
            values.filter { value ->
                value.isFinite() &&
                        value >= 0.0
            }

        if (validValues.size < 2) {
            return Double.POSITIVE_INFINITY
        }

        val mean =
            validValues.average()

        if (mean <= 0.0) {
            return Double.POSITIVE_INFINITY
        }

        val variance =
            validValues
                .map { value ->
                    val difference =
                        value -
                                mean

                    difference *
                            difference
                }
                .average()

        return sqrt(
            variance
        ) / mean
    }


    private fun List<Float>.isBurstDominated():
            Boolean {

        if (
            size <
            BURST_SEGMENT_COUNT * 2
        ) {
            return false
        }

        val segmentSize =
            size /
                    BURST_SEGMENT_COUNT

        val segmentAverages =
            (
                    0 until
                            BURST_SEGMENT_COUNT
                    ).map { segmentIndex ->

                    val startIndex =
                        segmentIndex *
                                segmentSize

                    val endIndex =
                        if (
                            segmentIndex ==
                            BURST_SEGMENT_COUNT - 1
                        ) {
                            size
                        } else {
                            startIndex +
                                    segmentSize
                        }

                    subList(
                        startIndex,
                        endIndex
                    ).averageOrZero()
                }

        val sorted =
            segmentAverages.sorted()

        val median =
            sorted[
                sorted.size / 2
            ]

        val largest =
            sorted.last()

        return largest >
                median *
                BURST_DOMINANCE_FACTOR +
                BURST_DOMINANCE_ABSOLUTE_MARGIN
    }


    private fun publishCompletedHandWindow(
        score: Int
    ) {

        val safeScore =
            score.coerceIn(
                minimumValue = 0,
                maximumValue = 4
            )

        if (
            isCollectingVoiceRecordingHandScores
        ) {
            voiceRecordingHandScores.add(
                safeScore
            )

            Log.d(
                "VOICE_HAND_SESSION",
                """
                Added completed hand score.
                score=$safeScore
                collected=$voiceRecordingHandScores
                """.trimIndent()
            )

            return
        }

        distressManager.updateHandScore(
            score =
                safeScore
        )

        distressManager
            .completeMeasurementWindow()

        distressManager
            .printStatus()

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


    private fun logActiveBaseline(
        baseline: MotionBaselineData
    ) {

        val profile =
            baseline.profile

        Log.d(
            "MOTION_FLOW",
            """
            Fixed-size accumulated motion baseline ready.
            sessions=${profile.validSessionCount}
            windows=${profile.totalWindowCount}
            seconds=${profile.totalBaselineSeconds}

            accelerationP95Mean=${profile.accelerationP95Mean}
            accelerationP95Std=${calculateStandardDeviation(
                m2 = profile.accelerationP95M2,
                sampleCount = profile.totalWindowCount
            )}

            gyroscopeP95Mean=${profile.gyroscopeP95Mean}
            gyroscopeP95Std=${calculateStandardDeviation(
                m2 = profile.gyroscopeP95M2,
                sampleCount = profile.totalWindowCount
            )}

            bandPowerMean=${profile.bandAveragePowerMean}
            bandPowerStd=${calculateStandardDeviation(
                m2 = profile.bandAveragePowerM2,
                sampleCount = profile.totalWindowCount
            )}

            peakPowerMean=${profile.peakNeighborhoodPowerMean}
            peakPowerStd=${calculateStandardDeviation(
                m2 = profile.peakNeighborhoodPowerM2,
                sampleCount = profile.totalWindowCount
            )}
            """.trimIndent()
        )
    }


    private fun logTremorEvaluation(
        current: MotionAnalysisResult,
        evaluation: TremorEvaluation
    ) {

        val spectrum =
            evaluation.spectrum

        val temporal =
            evaluation.temporal

        val severity =
            evaluation.severity

        Log.d(
            "MOTION_SCORE",
            """
            finalScore=${severity.score}
            tremorConfirmed=${evaluation.tremorConfirmed}

            currentAccelerationP95=${current.accelerationP95}
            currentAccelerationVariation=${current.accelerationVariation}
            currentGyroscopeP95=${current.gyroscopeP95}
            currentGyroscopeVariation=${current.gyroscopeVariation}

            peakFrequencyHz=${spectrum.peakFrequencyHz}
            concentrationRatio=${spectrum.concentrationRatio}
            narrowbandRatio=${spectrum.narrowbandRatio}
            rhythmicEnergyShare=${spectrum.rhythmicEnergyShare}
            bandAveragePower=${spectrum.bandAveragePower}
            peakNeighborhoodPower=${spectrum.peakNeighborhoodPower}

            wholePeakInBand=${evaluation.wholePeakInBand}
            wholeConcentrated=${evaluation.wholeConcentrated}
            wholeNarrowband=${evaluation.wholeNarrowband}
            wholeRhythmic=${evaluation.wholeRhythmic}
            wholePowerHigh=${evaluation.wholePowerHigh}

            personalRhythmicThreshold=${evaluation.wholeRhythmicThreshold}
            personalUpperBandPower=${evaluation.upperBandPower}
            personalUpperPeakPower=${evaluation.upperPeakPower}

            candidateWindowCount=${temporal.candidateWindowCount}
            hasTemporalCoverage=${temporal.hasTemporalCoverage}
            frequencyStable=${temporal.frequencyStable}
            powerStable=${temporal.powerStable}
            isBurstDominated=${temporal.isBurstDominated}

            temporalPeakFrequencies=${temporal.windowPeakFrequenciesHz}
            temporalConcentrations=${temporal.windowConcentrations}
            temporalNarrowbandRatios=${temporal.windowNarrowbandRatios}
            temporalRhythmicShares=${temporal.windowRhythmicShares}
            temporalBandPowers=${temporal.windowBandPowers}
            temporalPeakPowers=${temporal.windowPeakNeighborhoodPowers}

            candidateFrequencySpreadHz=${temporal.candidateFrequencySpreadHz}
            candidatePowerRatio=${temporal.candidatePowerRatio}
            candidatePowerCv=${temporal.candidatePowerCoefficientOfVariation}

            severityIndex=${severity.severityIndex}
            accelerationSeverityLevel=${severity.accelerationLevel}
            gyroscopeSeverityLevel=${severity.gyroscopeLevel}
            spectralSeverityLevel=${severity.spectralLevel}

            upperAccelerationP95=${severity.upperAccelerationP95}
            upperAccelerationVariation=${severity.upperAccelerationVariation}
            upperGyroscopeP95=${severity.upperGyroscopeP95}
            upperGyroscopeVariation=${severity.upperGyroscopeVariation}
            """.trimIndent()
        )
    }


    /*
     * Calculates mean and M2 using Welford's numerically stable
     * online algorithm.
     */
    private fun calculateFeatureStatistics(
        values: List<Double>
    ): FeatureStatistics {

        require(values.isNotEmpty()) {
            "Cannot calculate statistics from an empty list"
        }

        var count = 0
        var mean = 0.0
        var m2 = 0.0

        values.forEach { value ->

            require(value.isFinite()) {
                "Baseline feature values must be finite"
            }

            count += 1

            val delta =
                value -
                        mean

            mean +=
                delta /
                        count

            val deltaAfterMeanUpdate =
                value -
                        mean

            m2 +=
                delta *
                        deltaAfterMeanUpdate
        }

        return FeatureStatistics(
            mean =
                mean,

            m2 =
                m2.coerceAtLeast(
                    0.0
                ),

            count =
                count
        )
    }


    /*
     * Exactly merges historical aggregate statistics with the
     * temporary windows from one newly accepted session.
     */
    private fun mergeFeatureStatistics(
        existingMean: Double,
        existingM2: Double,
        existingCount: Int,
        newValues: List<Double>
    ): FeatureStatistics {

        require(existingCount > 0) {
            "Existing feature count must be positive"
        }

        require(
            existingMean.isFinite() &&
                    existingM2.isFinite() &&
                    existingM2 >= 0.0
        ) {
            "Existing feature statistics are invalid"
        }

        val newStatistics =
            calculateFeatureStatistics(
                values =
                    newValues
            )

        val combinedCount =
            existingCount +
                    newStatistics.count

        val delta =
            newStatistics.mean -
                    existingMean

        val combinedMean =
            existingMean +
                    delta *
                    newStatistics.count /
                    combinedCount

        val combinedM2 =
            existingM2 +
                    newStatistics.m2 +
                    delta *
                    delta *
                    existingCount *
                    newStatistics.count /
                    combinedCount

        return FeatureStatistics(
            mean =
                combinedMean,

            m2 =
                combinedM2.coerceAtLeast(
                    0.0
                ),

            count =
                combinedCount
        )
    }


    private fun calculateStandardDeviation(
        m2: Double,
        sampleCount: Int
    ): Double {

        if (
            sampleCount < 2 ||
            !m2.isFinite() ||
            m2 <= 0.0
        ) {
            return 0.0
        }

        return sqrt(
            m2 /
                    (
                            sampleCount -
                                    1
                            )
        )
    }


    private fun List<Long>.durationSeconds():
            Double {

        if (size < 2) {
            return 0.0
        }

        return (
                last() -
                        first()
                ) / 1_000_000_000.0
    }


    private fun List<Float>.averageOrZero():
            Float {

        return if (isNotEmpty()) {
            average().toFloat()
        } else {
            0f
        }
    }


    private fun List<Float>.variation():
            Float {

        if (isEmpty()) {
            return 0f
        }

        val average =
            average()

        return map { value ->
            abs(
                value -
                        average
            )
        }.average().toFloat()
    }


    private fun List<Float>.percentile95():
            Float { if (isEmpty()) { return 0f }

        val sorted = sorted()

        val index = ((sorted.size - 1) * 0.95).toInt()

        return sorted[index]
    }


    fun stopTracking() {

        trackingJob?.cancel()
        trackingJob = null

        recordingLifecycleJob?.cancel()
        recordingLifecycleJob = null

        voiceRecordingHandScores.clear()

        isCollectingVoiceRecordingHandScores =
            false

        motionManager.stopContinuous()

        distressManager
            .clearFormHandScore()

        Log.d(
            "MOTION_FLOW",
            "Motion tracking stopped"
        )
    }
}


private data class FeatureStatistics(
    val mean: Double,
    val m2: Double,
    val count: Int
)


private data class TremorEvaluation(
    val tremorConfirmed: Boolean,

    val spectrum: TremorSpectrumResult,
    val temporal: TemporalTremorResult,
    val severity: PersonalSeverityResult,

    val wholePeakInBand: Boolean,
    val wholeConcentrated: Boolean,
    val wholeNarrowband: Boolean,
    val wholeRhythmic: Boolean,
    val wholePowerHigh: Boolean,

    val wholeRhythmicThreshold: Double,

    val upperBandPower: Double,
    val upperPeakPower: Double
)


private data class PersonalSeverityResult(
    val score: Int = 0,

    val severityIndex: Double = 0.0,

    val accelerationLevel: Double = 0.0,
    val gyroscopeLevel: Double = 0.0,
    val spectralLevel: Double = 0.0,

    val upperAccelerationP95: Double = 0.0,
    val upperAccelerationVariation: Double = 0.0,

    val upperGyroscopeP95: Double = 0.0,
    val upperGyroscopeVariation: Double = 0.0,

    val upperBandPower: Double = 0.0,
    val upperPeakPower: Double = 0.0
)


private data class TemporalTremorResult(
    val candidateWindowCount: Int = 0,

    val hasTemporalCoverage: Boolean = false,
    val frequencyStable: Boolean = false,
    val powerStable: Boolean = false,
    val isBurstDominated: Boolean = false,

    val candidateFrequencySpreadHz: Double = Double.POSITIVE_INFINITY,

    val candidatePowerRatio: Double = Double.POSITIVE_INFINITY,

    val candidatePowerCoefficientOfVariation: Double = Double.POSITIVE_INFINITY,

    val windowPeakFrequenciesHz: List<Double> = emptyList(),

    val windowConcentrations: List<Double> = emptyList(),

    val windowNarrowbandRatios: List<Double> = emptyList(),

    val windowRhythmicShares: List<Double> = emptyList(),

    val windowBandPowers: List<Double> = emptyList(),

    val windowPeakNeighborhoodPowers: List<Double> = emptyList()
)