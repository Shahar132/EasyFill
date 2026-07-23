package com.example.easyfill_project.face_analysis

import android.util.Log
import kotlin.math.ceil

/**
 * Creates and manages the user's initial facial baseline.
 * This class is used during calibration.
 *
 * It:
 * 1. Collects facial data from camera frames.
 * 2. Filters unreliable frames.
 * 3. Groups valid frames into short time windows.
 * 4. Checks whether enough reliable data was collected.
 * 5. Calculates the user's normal facial measurements.
 * 6. Creates a FaceBaseline object.
 *
 * This class does not calculate the distress score.
 * Distress analysis is performed later by FaceDistressAnalyzer.
 */
class FaceBaselineManager {

    /**
     * Stores the facial measurements collected during calibration.
     *
     * Each FaceFrameData object represents the measurements
     * extracted from one detected face in one camera frame.
     */
    private val baselineFrames =
        mutableListOf<FaceFrameData>()

    /**
     * Timestamp of the first frame in the current calibration attempt.
     *
     * It is initially null because calibration timing should start
     * only when the first usable camera frame is received.
     */
    private var calibrationStartTimestampMs: Long? =
        null

    /**
     * The elapsed time at which the manager should next check
     * whether enough valid data exists to create a baseline.
     *
     * The first check occurs after 10 seconds.
     */
    private var nextBaselineCheckElapsedMs =
        INITIAL_CALIBRATION_DURATION_MS

    /**
     * The baseline currently stored in memory.
     *
     * It remains null until calibration succeeds,
     * or until a saved baseline is loaded.
     */
    private var currentBaseline: FaceBaseline? =
        null

    /**
     * Indicates whether this manager is currently collecting
     * frames for a new baseline.
     */
    private var isCalibrating =
        false

    /**
     * Counts how many calibration attempts have started.
     *
     * If an attempt reaches the maximum duration without
     * enough reliable information, another attempt begins.
     */
    private var calibrationAttempt =
        0

    /**
     * Stores the number of frames used in the most recently
     * completed calibration.
     */
    private var lastCollectedFrameCount =
        0

    /**
     * Stores the number of valid time windows found in the
     * most recently completed calibration.
     */
    private var lastValidWindowCount =
        0

    /**
     * Starts a completely new calibration attempt.
     *
     * Any old calibration information is removed.
     */
    @Synchronized
    fun startCalibration() {

        // Remove frames from a previous attempt.
        baselineFrames.clear()

        // Timing begins when the first new frame arrives.
        calibrationStartTimestampMs = null

        // The first baseline check will occur after 10 seconds.
        nextBaselineCheckElapsedMs =
            INITIAL_CALIBRATION_DURATION_MS

        // A new baseline has not been created yet.
        currentBaseline = null

        // Reset previous result counters.
        lastCollectedFrameCount = 0
        lastValidWindowCount = 0

        // Record that a new attempt has started.
        calibrationAttempt += 1

        isCalibrating = true

        Log.d(
            TAG,
            "Calibration started | " +
                    "attempt=$calibrationAttempt | " +
                    "requiredRawFeatures=${FaceStats.rawFeatures.size}"
        )
    }

    /**
     * Adds one detected facial frame to the calibration process.
     *
     * Returns:
     *
     * false:
     * - calibration is still running
     * - not enough time has passed
     * - not enough reliable data exists
     *
     * true:
     * - a complete and valid baseline has just been created
     *
     * It returns true only once for a successful calibration.
     */
    @Synchronized
    fun addFrame(
        frameData: FaceFrameData
    ): Boolean {

        // Ignore frames if calibration is not active.
        if (!isCalibrating) {
            return false
        }

        // Use the first received frame as the calibration start time.
        if (calibrationStartTimestampMs == null) {
            calibrationStartTimestampMs =
                frameData.timestampMs
        }

        /*
         * Every received frame is temporarily stored.
         *
         * The frame is not filtered here.
         * Invalid frames are removed later when valid
         * time windows are created.
         */
        baselineFrames.add(frameData)

        val startTimestamp =
            calibrationStartTimestampMs
                ?: frameData.timestampMs

        // Calculate how long calibration has been running.
        val elapsed =
            frameData.timestampMs -
                    startTimestamp

        /*
         * Do not try to create the baseline before the next
         * scheduled check time.
         *
         * Initially this means waiting for at least 10 seconds.
         */
        if (elapsed < nextBaselineCheckElapsedMs) {
            return false
        }

        // Group reliable frames into valid 500 ms windows.
        val validWindows =
            buildValidWindows()

        /*
         * Calculate how many valid windows are required
         * according to the total elapsed time.
         */
        val requiredWindows =
            calculateRequiredValidWindowCount(
                elapsedTimeMs = elapsed
            )

        Log.d(
            TAG,
            "Calibration check | " +
                    "attempt=$calibrationAttempt | " +
                    "elapsed=$elapsed | " +
                    "frames=${baselineFrames.size} | " +
                    "validWindows=${validWindows.size} | " +
                    "required=$requiredWindows"
        )

        /*
         * Try to create the baseline only when enough
         * valid windows have been collected.
         */
        if (validWindows.size >= requiredWindows) {

            val baseline =
                buildRawBaseline(
                    validWindows = validWindows
                )

            if (baseline != null) {

                // Save the new baseline in memory.
                currentBaseline = baseline

                // Calibration has finished successfully.
                isCalibrating = false

                // Keep statistics for the UI or logs.
                lastCollectedFrameCount =
                    baselineFrames.size

                lastValidWindowCount =
                    validWindows.size

                // Raw frames are no longer needed after baseline creation.
                baselineFrames.clear()

                Log.d(
                    TAG,
                    "Calibration completed | " +
                            "attempt=$calibrationAttempt | " +
                            "frames=$lastCollectedFrameCount | " +
                            "validWindows=$lastValidWindowCount | " +
                            "rawMetrics=${baseline.rawMetrics.size}"
                )

                return true
            }
        }

        /*
         * If calibration has already run for 60 seconds
         * without enough reliable data, restart it.
         */
        if (elapsed >= MAX_CALIBRATION_DURATION_MS) {

            restartCalibrationAttempt(
                firstFrame = frameData
            )

            return false
        }

        /*
         * Not enough reliable data exists yet.
         *
         * Extend calibration by another five seconds,
         * up to the maximum duration of 60 seconds.
         */
        nextBaselineCheckElapsedMs =
            minOf(
                nextBaselineCheckElapsedMs +
                        CALIBRATION_EXTENSION_MS,
                MAX_CALIBRATION_DURATION_MS
            )

        Log.d(
            TAG,
            "Calibration extended | " +
                    "attempt=$calibrationAttempt | " +
                    "nextCheck=$nextBaselineCheckElapsedMs"
        )

        return false
    }

    /**
     * Returns the baseline currently stored in memory.
     *
     * The result is null when:
     * - no saved baseline has been loaded
     * - calibration has not completed successfully
     */
    @Synchronized
    fun getCurrentBaseline(): FaceBaseline? {
        return currentBaseline
    }

    /**
     * Loads an existing baseline into this manager.
     *
     * This is used when FaceAnalysisController finds
     * a saved baseline in Firebase.
     */
    @Synchronized
    fun useSavedBaseline(
        baseline: FaceBaseline
    ) {

        /*
         * Verify that the loaded baseline contains every
         * facial feature currently required by the application.
         *
         * If any required feature is missing, require()
         * throws an IllegalArgumentException.
         */
        require(
            FaceStats.rawFeatures.all { feature ->
                feature in baseline.rawMetrics
            }
        ) {
            "Saved face baseline is missing required raw metrics."
        }

        // Store the saved baseline in memory.
        currentBaseline = baseline

        // No calibration is needed because a valid baseline exists.
        isCalibrating = false

        // Remove any unfinished calibration information.
        baselineFrames.clear()

        calibrationStartTimestampMs = null

        nextBaselineCheckElapsedMs =
            INITIAL_CALIBRATION_DURATION_MS

        lastCollectedFrameCount = 0
        lastValidWindowCount = 0

        Log.d(
            TAG,
            "Saved baseline ready | " +
                    "raw=${baseline.rawMetrics.size} | " +
                    "derived=${baseline.derivedMetrics.size}"
        )
    }

    /**
     * Returns the number of frames collected during
     * the current or most recently completed calibration.
     */
    @Synchronized
    fun getCollectedFrameCount(): Int {

        return if (isCalibrating) {

            // During calibration, return the current list size.
            baselineFrames.size

        } else {

            // After completion, return the stored final count.
            lastCollectedFrameCount
        }
    }

    /**
     * Returns the number of reliable 500 ms windows.
     */
    @Synchronized
    fun getValidWindowCount(): Int {

        return if (isCalibrating) {

            // Recalculate the currently available valid windows.
            buildValidWindows().size

        } else {

            // Return the count from the completed calibration.
            lastValidWindowCount
        }
    }

    /**
     * Returns true while calibration is active.
     */
    @Synchronized
    fun isCalibrationRunning(): Boolean {
        return isCalibrating
    }

    /**
     * Returns the current calibration-attempt number.
     */
    @Synchronized
    fun getCalibrationAttempt(): Int {
        return calibrationAttempt
    }

    /**
     * Tries to create a baseline manually from the
     * frames currently stored in memory.
     */
    @Synchronized
    fun buildRawBaseline(): FaceBaseline? {

        return buildRawBaseline(
            validWindows = buildValidWindows()
        )
    }

    /**
     * Creates a baseline from the provided valid windows.
     *
     * For every facial feature, the code calculates:
     *
     * - median: the user's typical value
     * - MAD: how much that value normally varies
     * - sampleCount: how many windows were used
     */
    private fun buildRawBaseline(
        validWindows: List<List<FaceFrameData>>
    ): FaceBaseline? {

        // Do not create a baseline from too little information.
        if (validWindows.size < MIN_VALID_WINDOWS) {
            return null
        }

        /*
         * Create one BaselineMetric for every required
         * raw facial feature.
         */
        val metrics =
            FaceStats.rawFeatures.associateWith { feature ->

                /*
                 * Each valid window may contain multiple frames.
                 *
                 * First calculate the median value of this facial
                 * feature inside each 500 ms window.
                 */
                val windowMedians =
                    validWindows.map { frames ->

                        FaceStats.median(
                            frames.map { frame ->

                                FaceStats.rawValue(
                                    frame = frame,
                                    feature = feature
                                )
                            }
                        )
                    }

                /*
                 * Calculate the overall median across all windows.
                 *
                 * This represents the typical baseline value
                 * for this facial feature.
                 */
                val median =
                    FaceStats.median(windowMedians)

                BaselineMetric(

                    // The user's normal value for this feature.
                    median = median,

                    /*
                     * Median Absolute Deviation:
                     *
                     * Represents how much this feature normally
                     * changes around the median.
                     */
                    mad = FaceStats.mad(
                        values = windowMedians,
                        median = median
                    ),

                    // Number of valid windows used for the metric.
                    sampleCount = windowMedians.size
                )
            }

        /*
         * Ensure that a baseline metric was created
         * for every required facial feature.
         */
        if (metrics.size != FaceStats.rawFeatures.size) {

            Log.e(
                TAG,
                "Failed to create all required raw baseline metrics"
            )

            return null
        }

        // Create and return the complete facial baseline.
        return FaceBaseline(
            rawMetrics = metrics
        )
    }

    /**
     * Builds reliable 500 ms windows from the collected frames.
     *
     * A window is accepted only when it contains at least
     * five technically valid facial frames.
     */
    private fun buildValidWindows():
            List<List<FaceFrameData>> {

        /*
         * Remove frames that do not contain valid facial data
         * or reliable eyebrow geometry.
         */
        val validFrames =
            baselineFrames.filter(
                ::isValidCalibrationFrame
            )

        if (validFrames.isEmpty()) {
            return emptyList()
        }

        val firstTimestamp =
            validFrames.first().timestampMs

        /*
         * Group frames according to their timestamp.
         *
         * Examples:
         *
         * 0–499 ms    → window 0
         * 500–999 ms  → window 1
         * 1000–1499 ms → window 2
         */
        return validFrames
            .groupBy { frame ->

                (
                        frame.timestampMs -
                                firstTimestamp
                        ) / WINDOW_DURATION_MS
            }
            .toSortedMap()
            .values
            .filter { frames ->

                /*
                 * Keep only windows with at least five
                 * reliable frames.
                 */
                frames.size >=
                        MIN_VALID_FRAMES_PER_WINDOW
            }
    }

    /**
     * Checks whether a frame is reliable enough
     * to participate in baseline calibration.
     */
    private fun isValidCalibrationFrame(
        frame: FaceFrameData
    ): Boolean {

        /*
         * Eyebrow geometry is required.
         *
         * If it is missing, this frame is rejected.
         */
        val geometry =
            frame.browGeometry
                ?: return false

        return FaceStats.isValidFrame(frame) &&

                // Eyebrow measurements must be marked reliable.
                geometry.isReliable &&

                // Distance must be a valid numeric value.
                geometry.interEyeDistance.isFinite() &&

                // Distance between the eyes must be greater than zero.
                geometry.interEyeDistance > 0f
    }

    /**
     * Calculates how many valid windows are required
     * based on the elapsed calibration duration.
     */
    private fun calculateRequiredValidWindowCount(
        elapsedTimeMs: Long
    ): Int {

        /*
         * Calculate how many 500 ms windows could theoretically
         * exist during the elapsed calibration period.
         *
         * Example:
         *
         * 10 seconds / 0.5 seconds = 20 possible windows.
         */
        val possibleWindows =
            (elapsedTimeMs / WINDOW_DURATION_MS)
                .toInt()
                .coerceAtLeast(1)

        /*
         * Require at least 75% of the theoretically possible
         * windows to contain enough reliable frames.
         *
         * Example:
         *
         * 20 possible windows × 0.75 = 15 valid windows.
         */
        val requiredByCoverage =
            ceil(
                possibleWindows *
                        MIN_VALID_WINDOW_COVERAGE
            ).toInt()

        /*
         * Never accept fewer than 15 valid windows,
         * even if the percentage calculation produces less.
         */
        return maxOf(
            MIN_VALID_WINDOWS,
            requiredByCoverage
        )
    }

    /**
     * Restarts calibration when the maximum duration was reached
     * without collecting enough reliable facial data.
     */
    private fun restartCalibrationAttempt(
        firstFrame: FaceFrameData
    ) {

        // Remove data from the unsuccessful attempt.
        baselineFrames.clear()

        /*
         * Reuse the latest frame as the first frame of the
         * new attempt, but only when it is valid.
         */
        if (isValidCalibrationFrame(firstFrame)) {
            baselineFrames.add(firstFrame)
        }

        // Restart timing from the latest frame.
        calibrationStartTimestampMs =
            firstFrame.timestampMs

        // First check in the new attempt occurs after 10 seconds.
        nextBaselineCheckElapsedMs =
            INITIAL_CALIBRATION_DURATION_MS

        currentBaseline = null

        calibrationAttempt += 1
        isCalibrating = true

        Log.d(
            TAG,
            "Calibration restarted | " +
                    "attempt=$calibrationAttempt"
        )
    }

    companion object {

        private const val TAG =
            "FACE_BASELINE"

        /**
         * Perform the first baseline-creation check
         * after 10 seconds.
         */
        private const val INITIAL_CALIBRATION_DURATION_MS =
            10_000L

        /**
         * If there is not enough reliable data,
         * extend calibration by five seconds.
         */
        private const val CALIBRATION_EXTENSION_MS =
            5_000L

        /**
         * Maximum duration for one calibration attempt:
         * 60 seconds.
         */
        private const val MAX_CALIBRATION_DURATION_MS =
            60_000L

        /**
         * Frames are grouped into windows of 500 milliseconds.
         */
        private const val WINDOW_DURATION_MS =
            500L

        /**
         * A 500 ms window must contain at least five
         * valid frames to be accepted.
         */
        private const val MIN_VALID_FRAMES_PER_WINDOW =
            5

        /**
         * At least 15 valid windows are required
         * to create the baseline.
         */
        private const val MIN_VALID_WINDOWS =
            15

        /**
         * At least 75% of the possible windows must be valid.
         */
        private const val MIN_VALID_WINDOW_COVERAGE =
            0.75f
    }
}