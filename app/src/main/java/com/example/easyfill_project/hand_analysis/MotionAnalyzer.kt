//package com.example.easyfill_project.hand_analysis
//
//import kotlin.math.abs
//import kotlin.math.sqrt
//
//// Receives raw accelerometer/gyroscope values and calculates motion features.
//class MotionAnalyzer {
//
//    private val minReliableDurationSeconds = 10.0
//
//    private var startTime = 0L
//
//    private val accelerationValues = mutableListOf<Float>()
//    private val gyroscopeValues = mutableListOf<Float>()
//
//    fun start() {
//        startTime = System.currentTimeMillis()
//        accelerationValues.clear()
//        gyroscopeValues.clear()
//    }
//
//    fun addAccelerometer(x: Float, y: Float, z: Float) {
//        val magnitude = sqrt(x * x + y * y + z * z)
//        accelerationValues.add(magnitude)
//    }
//
//    fun addGyroscope(x: Float, y: Float, z: Float) {
//        val magnitude = sqrt(x * x + y * y + z * z)
//        gyroscopeValues.add(magnitude)
//    }
//
//    fun analyze(): MotionAnalysisResult {
//        val durationSeconds =
//            (System.currentTimeMillis() - startTime) / 1000.0
//
//        val avgAcc = accelerationValues.averageOrZero()
//        val maxAcc = accelerationValues.maxOrNull() ?: 0f
//        val accVariation = accelerationValues.variationFrom(avgAcc)
//
//        val avgGyro = gyroscopeValues.averageOrZero()
//        val maxGyro = gyroscopeValues.maxOrNull() ?: 0f
//        val gyroVariation = gyroscopeValues.variationFrom(avgGyro)
//
//        // Added: personalized percentile calculation.
//        val accP95 = accelerationValues.percentile95()
//        val gyroP95 = gyroscopeValues.percentile95()
//
//        // Changed: fixed threshold was removed.
//        val shakeCount = 0
//
//        return MotionAnalysisResult(
//            durationSeconds = durationSeconds,
//
//            averageAcceleration = avgAcc,
//            maxAcceleration = maxAcc,
//            accelerationVariation = accVariation,
//
//            averageGyroscope = avgGyro,
//            maxGyroscope = maxGyro,
//            gyroscopeVariation = gyroVariation,
//
//            shakeCount = shakeCount,
//            isReliable = durationSeconds >= minReliableDurationSeconds,
//
//            accelerationP95 = accP95,
//            gyroscopeP95 = gyroP95,
//
//            accelerationExceedCount = 0,
//
//            // Added: copy raw values for comparison in current 5-sec windows.
//            accelerationValues = accelerationValues.toList(),
//            gyroscopeValues = gyroscopeValues.toList()
//
//        )
//    }
//
//    private fun List<Float>.averageOrZero(): Float =
//        if (isNotEmpty()) average().toFloat() else 0f
//
//    private fun List<Float>.variationFrom(avg: Float): Float =
//        if (isNotEmpty()) map { abs(it - avg) }.average().toFloat() else 0f
//
//    private fun List<Float>.percentile95(): Float {
//        if (isEmpty()) return 0f
//
//        val sorted = sorted()
//        val index = ((sorted.size - 1) * 0.95).toInt()
//        return sorted[index]
//    }
//}










///////////////////////////////////////////////////////////

package com.example.easyfill_project.hand_analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Frequency range preserved by the motion filters.
 *
 * The high-pass filter reduces gravity and slow phone
 * orientation changes.
 *
 * The low-pass filter reduces high-frequency sensor noise.
 */
private const val TREMOR_BAND_HIGH_PASS_HZ = 2.0
private const val TREMOR_BAND_LOW_PASS_HZ = 20.0

/*
 * Maximum continuous sensor history.
 *
 * Twelve seconds are kept so the same continuous collection
 * can support:
 *
 * - five-second live tremor windows
 * - one ten-second baseline-update candidate
 */
private const val MAX_CONTINUOUS_BUFFER_SECONDS = 12.0

/*
 * Minimum acceptable duration for a ten-second baseline
 * collection.
 *
 * A small tolerance is allowed because Android sensor
 * scheduling is not perfectly exact.
 */
private const val MIN_BASELINE_RELIABLE_DURATION_SECONDS = 9.5

/*
 * Receives accelerometer and gyroscope samples, filters them
 * and creates motion-analysis results.
 */
class MotionAnalyzer {

    /*
     * Filtered acceleration and gyroscope magnitudes.
     */
    private val accelerationValues =
        mutableListOf<Float>()

    private val gyroscopeValues =
        mutableListOf<Float>()

    /*
     * Filtered signed acceleration axes.
     *
     * Signed values preserve repeated direction changes and
     * are required for tremor-frequency analysis.
     */
    private val accelerationXValues =
        mutableListOf<Float>()

    private val accelerationYValues =
        mutableListOf<Float>()

    private val accelerationZValues =
        mutableListOf<Float>()

    /*
     * Monotonic SensorEvent timestamps.
     *
     * The acceleration timestamps are aligned with:
     *
     * - accelerationValues
     * - accelerationXValues
     * - accelerationYValues
     * - accelerationZValues
     *
     * The gyroscope timestamps are aligned with:
     *
     * - gyroscopeValues
     */
    private val accelerationTimestampsNs =
        mutableListOf<Long>()

    private val gyroscopeTimestampsNs =
        mutableListOf<Long>()

    /*
     * false:
     * Collect one discrete baseline measurement.
     *
     * true:
     * Maintain a rolling continuous sensor buffer.
     */
    private var continuousMode = false

    /*
     * Every axis needs an independent filter instance because
     * each filter keeps its own previous state.
     */
    private var accXFilter =
        TremorBandPassFilter()

    private var accYFilter =
        TremorBandPassFilter()

    private var accZFilter =
        TremorBandPassFilter()

    private var gyroXFilter =
        TremorBandPassFilter()

    private var gyroYFilter =
        TremorBandPassFilter()

    private var gyroZFilter =
        TremorBandPassFilter()

    /*
     * Previous timestamps are used to calculate the real time
     * between sensor samples.
     */
    private var previousAccelTimestampNs: Long? =
        null

    private var previousGyroTimestampNs: Long? =
        null

    /*
     * Starts one discrete sensor collection.
     *
     * Used when no baseline exists and the first ten-second
     * baseline must be created.
     */
    fun start() {
        continuousMode = false
        resetState()
    }

    /*
     * Starts continuous rolling collection.
     *
     * Used for both:
     *
     * - live five-second tremor analysis
     * - ten-second baseline-update candidates
     */
    fun startContinuous() {
        continuousMode = true
        resetState()
    }

    /*
     * Clears all sensor samples and filter state.
     */
    private fun resetState() {
        accelerationValues.clear()
        gyroscopeValues.clear()

        accelerationXValues.clear()
        accelerationYValues.clear()
        accelerationZValues.clear()

        accelerationTimestampsNs.clear()
        gyroscopeTimestampsNs.clear()

        accXFilter =
            TremorBandPassFilter()

        accYFilter =
            TremorBandPassFilter()

        accZFilter =
            TremorBandPassFilter()

        gyroXFilter =
            TremorBandPassFilter()

        gyroYFilter =
            TremorBandPassFilter()

        gyroZFilter =
            TremorBandPassFilter()

        previousAccelTimestampNs = null
        previousGyroTimestampNs = null
    }

    /*
     * Adds one accelerometer sensor event.
     */
    fun addAccelerometer(
        x: Float,
        y: Float,
        z: Float,
        timestampNs: Long
    ) {
        val previousTimestampNs =
            previousAccelTimestampNs

        /*
         * Sensor timestamps should be strictly increasing.
         *
         * Duplicate or out-of-order events are ignored so the
         * timestamp lists remain valid for time-based slicing.
         */
        if (
            previousTimestampNs != null &&
            timestampNs <= previousTimestampNs
        ) {
            return
        }

        val dtSeconds =
            if (previousTimestampNs != null) {
                (
                        timestampNs -
                                previousTimestampNs
                        ) / 1_000_000_000.0
            } else {
                0.0
            }

        val filteredX =
            accXFilter.process(
                input = x,
                dtSeconds = dtSeconds
            )

        val filteredY =
            accYFilter.process(
                input = y,
                dtSeconds = dtSeconds
            )

        val filteredZ =
            accZFilter.process(
                input = z,
                dtSeconds = dtSeconds
            )

        /*
         * The first sample initializes the filters.
         *
         * It is not stored because no valid sample interval is
         * available yet.
         */
        if (previousTimestampNs != null) {
            val magnitude =
                sqrt(
                    filteredX * filteredX +
                            filteredY * filteredY +
                            filteredZ * filteredZ
                )

            /*
             * Add all aligned acceleration values together.
             */
            accelerationValues.add(
                magnitude
            )

            accelerationXValues.add(
                filteredX
            )

            accelerationYValues.add(
                filteredY
            )

            accelerationZValues.add(
                filteredZ
            )

            accelerationTimestampsNs.add(
                timestampNs
            )

            if (continuousMode) {
                trimOldAccelerationEntries(
                    latestTimestampNs =
                        timestampNs
                )
            }
        }

        previousAccelTimestampNs =
            timestampNs
    }

    /*
     * Adds one gyroscope sensor event.
     */
    fun addGyroscope(
        x: Float,
        y: Float,
        z: Float,
        timestampNs: Long
    ) {
        val previousTimestampNs =
            previousGyroTimestampNs

        if (
            previousTimestampNs != null &&
            timestampNs <= previousTimestampNs
        ) {
            return
        }

        val dtSeconds =
            if (previousTimestampNs != null) {
                (
                        timestampNs -
                                previousTimestampNs
                        ) / 1_000_000_000.0
            } else {
                0.0
            }

        val filteredX =
            gyroXFilter.process(
                input = x,
                dtSeconds = dtSeconds
            )

        val filteredY =
            gyroYFilter.process(
                input = y,
                dtSeconds = dtSeconds
            )

        val filteredZ =
            gyroZFilter.process(
                input = z,
                dtSeconds = dtSeconds
            )

        if (previousTimestampNs != null) {
            val magnitude =
                sqrt(
                    filteredX * filteredX +
                            filteredY * filteredY +
                            filteredZ * filteredZ
                )

            gyroscopeValues.add(
                magnitude
            )

            gyroscopeTimestampsNs.add(
                timestampNs
            )

            if (continuousMode) {
                trimOldGyroscopeEntries(
                    latestTimestampNs =
                        timestampNs
                )
            }
        }

        previousGyroTimestampNs =
            timestampNs
    }

    /*
     * Removes acceleration samples older than the maximum
     * continuous-buffer duration.
     *
     * Every aligned list is trimmed together.
     */
    private fun trimOldAccelerationEntries(
        latestTimestampNs: Long
    ) {
        val maxAgeNs =
            (
                    MAX_CONTINUOUS_BUFFER_SECONDS *
                            1_000_000_000.0
                    ).toLong()

        while (
            accelerationTimestampsNs.isNotEmpty() &&
            latestTimestampNs -
            accelerationTimestampsNs.first() >
            maxAgeNs
        ) {
            accelerationTimestampsNs.removeAt(0)

            accelerationValues.removeAt(0)

            accelerationXValues.removeAt(0)
            accelerationYValues.removeAt(0)
            accelerationZValues.removeAt(0)
        }
    }

    /*
     * Removes gyroscope samples older than the maximum
     * continuous-buffer duration.
     */
    private fun trimOldGyroscopeEntries(
        latestTimestampNs: Long
    ) {
        val maxAgeNs =
            (
                    MAX_CONTINUOUS_BUFFER_SECONDS *
                            1_000_000_000.0
                    ).toLong()

        while (
            gyroscopeTimestampsNs.isNotEmpty() &&
            latestTimestampNs -
            gyroscopeTimestampsNs.first() >
            maxAgeNs
        ) {
            gyroscopeTimestampsNs.removeAt(0)
            gyroscopeValues.removeAt(0)
        }
    }

    /*
     * Analyzes all samples collected since start().
     *
     * Used for the first baseline when no saved baseline
     * exists.
     */
    fun analyze(): MotionAnalysisResult {
        return buildResult(
            accValues =
                accelerationValues,

            gyroValues =
                gyroscopeValues,

            accXValues =
                accelerationXValues,

            accYValues =
                accelerationYValues,

            accZValues =
                accelerationZValues,

            accTimestampsNs =
                accelerationTimestampsNs,

            gyroTimestampsNs =
                gyroscopeTimestampsNs,

            minimumReliableDurationSeconds =
                MIN_BASELINE_RELIABLE_DURATION_SECONDS
        )
    }

    /*
     * Creates a rolling snapshot without clearing continuous
     * sensor collection.
     */
    fun snapshot(
        windowSeconds: Double
    ): MotionAnalysisResult {
        if (windowSeconds <= 0.0) {
            return emptyResult()
        }

        val latestAccelerationTimestamp =
            accelerationTimestampsNs
                .lastOrNull()

        val latestGyroscopeTimestamp =
            gyroscopeTimestampsNs
                .lastOrNull()

        val referenceTimestampNs =
            listOfNotNull(
                latestAccelerationTimestamp,
                latestGyroscopeTimestamp
            ).maxOrNull()
                ?: return emptyResult()

        val windowNs =
            (
                    windowSeconds *
                            1_000_000_000.0
                    ).toLong()

        val accelerationStartIndex =
            firstIndexWithinWindow(
                timestamps =
                    accelerationTimestampsNs,

                referenceTimestampNs =
                    referenceTimestampNs,

                windowNs =
                    windowNs
            )

        val gyroscopeStartIndex =
            firstIndexWithinWindow(
                timestamps =
                    gyroscopeTimestampsNs,

                referenceTimestampNs =
                    referenceTimestampNs,

                windowNs =
                    windowNs
            )

        val accSlice =
            sliceFromIndex(
                values =
                    accelerationValues,

                startIndex =
                    accelerationStartIndex
            )

        val gyroSlice =
            sliceFromIndex(
                values =
                    gyroscopeValues,

                startIndex =
                    gyroscopeStartIndex
            )

        val accXSlice =
            sliceFromIndex(
                values =
                    accelerationXValues,

                startIndex =
                    accelerationStartIndex
            )

        val accYSlice =
            sliceFromIndex(
                values =
                    accelerationYValues,

                startIndex =
                    accelerationStartIndex
            )

        val accZSlice =
            sliceFromIndex(
                values =
                    accelerationZValues,

                startIndex =
                    accelerationStartIndex
            )

        val accelerationTimestampSlice =
            sliceFromIndex(
                values =
                    accelerationTimestampsNs,

                startIndex =
                    accelerationStartIndex
            )

        val gyroscopeTimestampSlice =
            sliceFromIndex(
                values =
                    gyroscopeTimestampsNs,

                startIndex =
                    gyroscopeStartIndex
            )

        return buildResult(
            accValues =
                accSlice,

            gyroValues =
                gyroSlice,

            accXValues =
                accXSlice,

            accYValues =
                accYSlice,

            accZValues =
                accZSlice,

            accTimestampsNs =
                accelerationTimestampSlice,

            gyroTimestampsNs =
                gyroscopeTimestampSlice,

            /*
             * At least 90% of the requested window must be
             * represented by both sensors.
             */
            minimumReliableDurationSeconds =
                windowSeconds * 0.9
        )
    }

    /*
     * Finds the first sample whose timestamp is inside the
     * requested time window.
     */
    private fun firstIndexWithinWindow(
        timestamps: List<Long>,
        referenceTimestampNs: Long,
        windowNs: Long
    ): Int {
        return timestamps.indexOfFirst { timestampNs ->
            referenceTimestampNs -
                    timestampNs <=
                    windowNs
        }
    }

    /*
     * Copies a list from startIndex to its end.
     */
    private fun <T> sliceFromIndex(
        values: List<T>,
        startIndex: Int
    ): List<T> {
        return if (
            startIndex >= 0 &&
            startIndex < values.size
        ) {
            values
                .subList(
                    startIndex,
                    values.size
                )
                .toList()
        } else {
            emptyList()
        }
    }

    /*
     * Builds one immutable motion result.
     *
     * List sizes are aligned defensively before statistics are
     * calculated.
     */
    private fun buildResult(
        accValues: List<Float>,
        gyroValues: List<Float>,

        accXValues: List<Float>,
        accYValues: List<Float>,
        accZValues: List<Float>,

        accTimestampsNs: List<Long>,
        gyroTimestampsNs: List<Long>,

        minimumReliableDurationSeconds: Double
    ): MotionAnalysisResult {
        val alignedAccelerationCount =
            minOf(
                accValues.size,
                accXValues.size,
                accYValues.size,
                accZValues.size,
                accTimestampsNs.size
            )

        val alignedGyroscopeCount =
            minOf(
                gyroValues.size,
                gyroTimestampsNs.size
            )

        val safeAccValues =
            accValues.takeLast(
                alignedAccelerationCount
            )

        val safeAccXValues =
            accXValues.takeLast(
                alignedAccelerationCount
            )

        val safeAccYValues =
            accYValues.takeLast(
                alignedAccelerationCount
            )

        val safeAccZValues =
            accZValues.takeLast(
                alignedAccelerationCount
            )

        val safeAccTimestamps =
            accTimestampsNs.takeLast(
                alignedAccelerationCount
            )

        val safeGyroValues =
            gyroValues.takeLast(
                alignedGyroscopeCount
            )

        val safeGyroTimestamps =
            gyroTimestampsNs.takeLast(
                alignedGyroscopeCount
            )

        val accelerationDurationSeconds =
            safeAccTimestamps
                .durationSeconds()

        val gyroscopeDurationSeconds =
            safeGyroTimestamps
                .durationSeconds()

        /*
         * Acceleration duration is stored because the spectral
         * analysis is performed on acceleration axes.
         */
        val durationSeconds =
            accelerationDurationSeconds

        val averageAcceleration =
            safeAccValues.averageOrZero()

        val maxAcceleration =
            safeAccValues.maxOrNull()
                ?: 0f

        val accelerationVariation =
            safeAccValues.variationFrom(
                average =
                    averageAcceleration
            )

        val accelerationP95 =
            safeAccValues.percentile95()

        val averageGyroscope =
            safeGyroValues.averageOrZero()

        val maxGyroscope =
            safeGyroValues.maxOrNull()
                ?: 0f

        val gyroscopeVariation =
            safeGyroValues.variationFrom(
                average =
                    averageGyroscope
            )

        val gyroscopeP95 =
            safeGyroValues.percentile95()

        val hasRequiredSensorData =
            safeAccValues.isNotEmpty() &&
                    safeGyroValues.isNotEmpty()

        val bothSensorsCoverWindow =
            accelerationDurationSeconds >=
                    minimumReliableDurationSeconds &&
                    gyroscopeDurationSeconds >=
                    minimumReliableDurationSeconds

        val isReliable =
            hasRequiredSensorData &&
                    bothSensorsCoverWindow

        return MotionAnalysisResult(
            durationSeconds =
                durationSeconds,

            averageAcceleration =
                averageAcceleration,

            maxAcceleration =
                maxAcceleration,

            accelerationVariation =
                accelerationVariation,

            accelerationP95 =
                accelerationP95,

            averageGyroscope =
                averageGyroscope,

            maxGyroscope =
                maxGyroscope,

            gyroscopeVariation =
                gyroscopeVariation,

            gyroscopeP95 =
                gyroscopeP95,

            isReliable =
                isReliable,

            accelerationValues =
                safeAccValues,

            gyroscopeValues =
                safeGyroValues,

            accelerationXValues =
                safeAccXValues,

            accelerationYValues =
                safeAccYValues,

            accelerationZValues =
                safeAccZValues,

            accelerationTimestampsNs =
                safeAccTimestamps,

            gyroscopeTimestampsNs =
                safeGyroTimestamps
        )
    }

    private fun emptyResult():
            MotionAnalysisResult {
        return MotionAnalysisResult(
            durationSeconds = 0.0,

            averageAcceleration = 0f,
            maxAcceleration = 0f,
            accelerationVariation = 0f,
            accelerationP95 = 0f,

            averageGyroscope = 0f,
            maxGyroscope = 0f,
            gyroscopeVariation = 0f,
            gyroscopeP95 = 0f,

            isReliable = false
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

    /*
     * Mean absolute deviation from the current window mean.
     */
    private fun List<Float>.variationFrom(
        average: Float
    ): Float {
        return if (isNotEmpty()) {
            map { value ->
                abs(
                    value -
                            average
                )
            }.average().toFloat()
        } else {
            0f
        }
    }

    private fun List<Float>.percentile95():
            Float {
        if (isEmpty()) {
            return 0f
        }

        val sortedValues =
            sorted()

        val index =
            (
                    (
                            sortedValues.size -
                                    1
                            ) * 0.95
                    ).toInt()

        return sortedValues[index]
    }
}

/*
 * Band-pass filter for one signed sensor axis.
 *
 * A first-order high-pass filter is followed by a first-order
 * low-pass filter.
 */
private class TremorBandPassFilter {

    private val highPass =
        OnePoleHighPass(
            cutoffHz =
                TREMOR_BAND_HIGH_PASS_HZ
        )

    private val lowPass =
        OnePoleLowPass(
            cutoffHz =
                TREMOR_BAND_LOW_PASS_HZ
        )

    fun process(
        input: Float,
        dtSeconds: Double
    ): Float {
        val highPassed =
            highPass.process(
                input = input,
                dtSeconds = dtSeconds
            )

        return lowPass.process(
            input = highPassed,
            dtSeconds = dtSeconds
        )
    }
}

/*
 * First-order high-pass filter.
 *
 * Passes faster changes while reducing gravity and slow phone
 * orientation changes.
 */
private class OnePoleHighPass(
    private val cutoffHz: Double
) {

    private var previousInput: Float? =
        null

    private var previousOutput: Float =
        0f

    fun process(
        input: Float,
        dtSeconds: Double
    ): Float {
        val lastInput =
            previousInput

        if (
            lastInput == null ||
            dtSeconds <= 0.0
        ) {
            previousInput = input
            previousOutput = 0f
            return 0f
        }

        val rc =
            1.0 /
                    (
                            2.0 *
                                    PI *
                                    cutoffHz
                            )

        val alpha =
            (
                    rc /
                            (
                                    rc +
                                            dtSeconds
                                    )
                    ).toFloat()

        val output =
            alpha *
                    (
                            previousOutput +
                                    input -
                                    lastInput
                            )

        previousInput = input
        previousOutput = output

        return output
    }
}

/*
 * First-order low-pass filter.
 *
 * Reduces high-frequency sensor noise after slow movement has
 * already been reduced.
 */
private class OnePoleLowPass(
    private val cutoffHz: Double
) {

    private var previousOutput: Float? =
        null

    fun process(
        input: Float,
        dtSeconds: Double
    ): Float {
        if (dtSeconds <= 0.0) {
            previousOutput = input
            return input
        }

        val rc =
            1.0 /
                    (2.0 * PI * cutoffHz)

        val alpha =
            (dtSeconds / (rc + dtSeconds)).toFloat()

        val lastOutput =
            previousOutput
                ?: input

        val output =
            lastOutput +
                    alpha *
                    (
                            input -
                                    lastOutput
                            )

        previousOutput = output

        return output
    }
}