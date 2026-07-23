package com.example.easyfill_project.face_analysis

import android.util.Log
import kotlin.math.ceil

/**
 * Creates the user's initial raw facial baseline.
 */
class FaceBaselineManager {

    private val baselineFrames =
        mutableListOf<FaceFrameData>()

    private var calibrationStartTimestampMs: Long? =
        null

    private var nextBaselineCheckElapsedMs =
        INITIAL_CALIBRATION_DURATION_MS

    private var currentBaseline: FaceBaseline? =
        null

    private var isCalibrating =
        false

    private var calibrationAttempt =
        0

    private var lastCollectedFrameCount =
        0

    private var lastValidWindowCount =
        0

    /**
     * Starts a completely new baseline calibration attempt.
     */
    @Synchronized
    fun startCalibration() {
        baselineFrames.clear()

        calibrationStartTimestampMs = null

        nextBaselineCheckElapsedMs =
            INITIAL_CALIBRATION_DURATION_MS

        currentBaseline = null

        lastCollectedFrameCount = 0
        lastValidWindowCount = 0

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
     * Adds one facial frame to the calibration.
     *
     * Returns true exactly once when a complete
     * and valid baseline has been created.
     */
    @Synchronized
    fun addFrame(
        frameData: FaceFrameData
    ): Boolean {
        if (!isCalibrating) {
            return false
        }

        if (calibrationStartTimestampMs == null) {
            calibrationStartTimestampMs =
                frameData.timestampMs
        }

        /*
         * All frames are collected here.
         * Invalid frames are filtered when windows are built.
         */
        baselineFrames.add(frameData)

        val startTimestamp =
            calibrationStartTimestampMs
                ?: frameData.timestampMs

        val elapsed =
            frameData.timestampMs -
                    startTimestamp

        if (elapsed < nextBaselineCheckElapsedMs) {
            return false
        }

        val validWindows =
            buildValidWindows()

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

        if (validWindows.size >= requiredWindows) {
            val baseline =
                buildRawBaseline(
                    validWindows = validWindows
                )

            if (baseline != null) {
                currentBaseline = baseline
                isCalibrating = false

                lastCollectedFrameCount =
                    baselineFrames.size

                lastValidWindowCount =
                    validWindows.size

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

        if (elapsed >= MAX_CALIBRATION_DURATION_MS) {
            restartCalibrationAttempt(
                firstFrame = frameData
            )

            return false
        }

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
     * Returns the baseline currently held in memory.
     */
    @Synchronized
    fun getCurrentBaseline(): FaceBaseline? {
        return currentBaseline
    }

    /**
     * Uses a previously saved baseline.
     *
     * The saved baseline is accepted only when it
     * contains every currently required raw feature.
     */
    @Synchronized
    fun useSavedBaseline(
        baseline: FaceBaseline
    ) {
        require(
            FaceStats.rawFeatures.all { feature ->
                feature in baseline.rawMetrics
            }
        ) {
            "Saved face baseline is missing required raw metrics."
        }

        currentBaseline = baseline
        isCalibrating = false

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
     * Returns the number of frames collected
     * during the current or most recent calibration.
     */
    @Synchronized
    fun getCollectedFrameCount(): Int {
        return if (isCalibrating) {
            baselineFrames.size
        } else {
            lastCollectedFrameCount
        }
    }

    /**
     * Returns the number of valid 500 ms windows.
     */
    @Synchronized
    fun getValidWindowCount(): Int {
        return if (isCalibrating) {
            buildValidWindows().size
        } else {
            lastValidWindowCount
        }
    }

    /**
     * Returns whether calibration is currently active.
     */
    @Synchronized
    fun isCalibrationRunning(): Boolean {
        return isCalibrating
    }

    /**
     * Returns the current calibration attempt number.
     */
    @Synchronized
    fun getCalibrationAttempt(): Int {
        return calibrationAttempt
    }

    /**
     * Attempts to create a baseline from
     * the frames currently held in memory.
     */
    @Synchronized
    fun buildRawBaseline(): FaceBaseline? {
        return buildRawBaseline(
            validWindows = buildValidWindows()
        )
    }

    /**
     * Creates the raw baseline from valid windows.
     *
     * Every raw feature receives its own median,
     * MAD and sample count.
     */
    private fun buildRawBaseline(
        validWindows: List<List<FaceFrameData>>
    ): FaceBaseline? {
        if (validWindows.size < MIN_VALID_WINDOWS) {
            return null
        }

        val metrics =
            FaceStats.rawFeatures.associateWith { feature ->
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

                val median =
                    FaceStats.median(windowMedians)

                BaselineMetric(
                    median = median,
                    mad = FaceStats.mad(
                        values = windowMedians,
                        median = median
                    ),
                    sampleCount = windowMedians.size
                )
            }

        if (metrics.size != FaceStats.rawFeatures.size) {
            Log.e(
                TAG,
                "Failed to create all required raw baseline metrics"
            )

            return null
        }

        return FaceBaseline(
            rawMetrics = metrics
        )
    }

    /**
     * Groups technically valid frames into
     * reliable 500 ms calibration windows.
     */
    private fun buildValidWindows():
            List<List<FaceFrameData>> {

        val validFrames =
            baselineFrames.filter(
                ::isValidCalibrationFrame
            )

        if (validFrames.isEmpty()) {
            return emptyList()
        }

        val firstTimestamp =
            validFrames.first().timestampMs

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
                frames.size >=
                        MIN_VALID_FRAMES_PER_WINDOW
            }
    }

    /**
     * A calibration frame must contain valid
     * Blendshapes and reliable eyebrow geometry.
     */
    private fun isValidCalibrationFrame(
        frame: FaceFrameData
    ): Boolean {
        val geometry =
            frame.browGeometry
                ?: return false

        return FaceStats.isValidFrame(frame) &&
                geometry.isReliable &&
                geometry.interEyeDistance.isFinite() &&
                geometry.interEyeDistance > 0f
    }

    /**
     * Calculates how many valid windows are required
     * according to the elapsed calibration time.
     */
    private fun calculateRequiredValidWindowCount(
        elapsedTimeMs: Long
    ): Int {
        val possibleWindows =
            (elapsedTimeMs / WINDOW_DURATION_MS)
                .toInt()
                .coerceAtLeast(1)

        val requiredByCoverage =
            ceil(
                possibleWindows *
                        MIN_VALID_WINDOW_COVERAGE
            ).toInt()

        return maxOf(
            MIN_VALID_WINDOWS,
            requiredByCoverage
        )
    }

    /**
     * Restarts calibration after the maximum
     * duration was reached without enough data.
     */
    private fun restartCalibrationAttempt(
        firstFrame: FaceFrameData
    ) {
        baselineFrames.clear()

        if (isValidCalibrationFrame(firstFrame)) {
            baselineFrames.add(firstFrame)
        }

        calibrationStartTimestampMs =
            firstFrame.timestampMs

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

        private const val INITIAL_CALIBRATION_DURATION_MS =
            10_000L

        private const val CALIBRATION_EXTENSION_MS =
            5_000L

        private const val MAX_CALIBRATION_DURATION_MS =
            60_000L

        private const val WINDOW_DURATION_MS =
            500L

        private const val MIN_VALID_FRAMES_PER_WINDOW =
            5

        private const val MIN_VALID_WINDOWS =
            15

        private const val MIN_VALID_WINDOW_COVERAGE =
            0.75f
    }
}