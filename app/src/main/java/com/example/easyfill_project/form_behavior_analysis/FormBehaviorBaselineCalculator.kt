//package com.example.easyfill_project.form_behavior_analysis
//// it takes several FieldBehaviorSample objects and creates one FormBehaviorBaseline
//// by calculating average and standard deviation for each behavior parameter.
//
//import android.os.SystemClock
//// Used to save the time when the baseline was calculated.
//
//import kotlin.math.pow
//// Used for square calculation in standard deviation.
//
//import kotlin.math.sqrt
//// Used for square root in standard deviation.
//
//object FormBehaviorBaselineCalculator {
//    // Singleton object responsible for creating a baseline from samples.
//
//    fun calculate(samples: List<FieldBehaviorSample>): FormBehaviorBaseline? {
//        // Receives a list of field behavior samples and returns a baseline.
//
//        if (samples.isEmpty()) {
//            return null
//        }
//        // If there are no samples, baseline cannot be created.
//
//        return FormBehaviorBaseline(
//            // Create and return the baseline object.
//
//            sampleCount = samples.size,
//            // Number of samples used to build the baseline.
//
//            calculatedAtMs = SystemClock.elapsedRealtime(),
//            // Time when this baseline was created.
//
//            avgDwellTimeMs = samples.map { it.dwellTimeMs.toDouble() }.averageValue(),
//            stdDwellTimeMs = samples.map { it.dwellTimeMs.toDouble() }.standardDeviation(),
//            // Average and standard deviation of time spent in field.
//
//            avgThinkingTimeMs = samples.map { it.thinkingTimeMs.toDouble() }.averageValue(),
//            stdThinkingTimeMs = samples.map { it.thinkingTimeMs.toDouble() }.standardDeviation(),
//            // Average and standard deviation of thinking time before typing.
//
//            avgTypingMsPerInsertedChar = samples.map { it.typingMsPerInsertedChar }.averageValue(),
//            stdTypingMsPerInsertedChar = samples.map { it.typingMsPerInsertedChar }.standardDeviation(),
//            // Average and standard deviation of typing speed per inserted character.
//
//            avgReviewTimeMs = samples.map { it.reviewTimeMs.toDouble() }.averageValue(),
//            stdReviewTimeMs = samples.map { it.reviewTimeMs.toDouble() }.standardDeviation(),
//            // Average and standard deviation of review time after typing.
//
//            avgMaxIdleTimeMs = samples.map { it.maxIdleTimeMs.toDouble() }.averageValue(),
//            stdMaxIdleTimeMs = samples.map { it.maxIdleTimeMs.toDouble() }.standardDeviation(),
//            // Average and standard deviation of longest idle time.
//
//            avgIdleEvents = samples.map { it.idleEvents.toDouble() }.averageValue(),
//            stdIdleEvents = samples.map { it.idleEvents.toDouble() }.standardDeviation(),
//            // Average and standard deviation of idle events count.
//
//            avgDeleteRatio = samples.map { it.deleteRatio }.averageValue(),
//            stdDeleteRatio = samples.map { it.deleteRatio }.standardDeviation(),
//            // Average and standard deviation of delete ratio.
//
//            avgLongPauses = samples.map { it.longPauses.toDouble() }.averageValue(),
//            stdLongPauses = samples.map { it.longPauses.toDouble() }.standardDeviation()
//            // Average and standard deviation of long pauses.
//        )
//    }
//
//    private fun List<Double>.averageValue(): Double {
//        // Helper function to calculate average safely.
//
//        if (isEmpty()) return 0.0
//        // If list is empty, return 0 instead of crashing.
//
//        return average()
//        // Kotlin built-in average calculation.
//    }
//
//    private fun List<Double>.standardDeviation(): Double {
//        // Helper function to calculate standard deviation.
//
//        if (size < 2) return 0.0
//        // Need at least 2 values to calculate meaningful standard deviation.
//
//        val avg = average()
//        // Calculate the average of the list.
//
//        val variance = sumOf { value ->
//            (value - avg).pow(2)
//        } / (size - 1)
//        // Calculate sample variance.
//
//        return sqrt(variance)
//        // Standard deviation is the square root of variance.
//    }
//}

///////////////////////////////////////////////

package com.example.easyfill_project.form_behavior_analysis

import kotlin.math.sqrt

/*
 * Creates and updates the user's accumulated form-behavior
 * baseline.
 *
 * Historical FieldBehaviorSample objects are not required
 * after their statistics have been merged into the baseline.
 */
object FormBehaviorBaselineCalculator {

    /*
     * Creates the first baseline from the initial valid field
     * samples collected during one form session.
     */
    fun calculate(
        samples: List<FieldBehaviorSample>
    ): FormBehaviorBaseline? {

        val validSamples =
            samples.filter { sample ->
                sample.isValidForBaseline()
            }

        if (validSamples.isEmpty()) {
            return null
        }

        val dwellTimeStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.dwellTimeMs.toDouble()
                    }
            )

        val thinkingTimeStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.thinkingTimeMs.toDouble()
                    }
            )

        val typingSpeedStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.typingMsPerInsertedChar
                    }
            )

        val reviewTimeStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.reviewTimeMs.toDouble()
                    }
            )

        val maxIdleTimeStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.maxIdleTimeMs.toDouble()
                    }
            )

        val idleEventsStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.idleEvents.toDouble()
                    }
            )

        val deleteRatioStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.deleteRatio
                    }
            )

        val longPausesStats =
            calculateStats(
                values =
                    validSamples.map { sample ->
                        sample.longPauses.toDouble()
                    }
            )

        return FormBehaviorBaseline(
            sampleCount =
                validSamples.size,

            validSessionCount =
                1,

            /*
             * This value is stored in Firebase, so wall-clock
             * time is used instead of elapsedRealtime().
             */
            calculatedAtMs =
                System.currentTimeMillis(),

            avgDwellTimeMs =
                dwellTimeStats.mean,

            stdDwellTimeMs =
                dwellTimeStats.standardDeviation,

            dwellTimeM2 =
                dwellTimeStats.m2,

            avgThinkingTimeMs =
                thinkingTimeStats.mean,

            stdThinkingTimeMs =
                thinkingTimeStats.standardDeviation,

            thinkingTimeM2 =
                thinkingTimeStats.m2,

            avgTypingMsPerInsertedChar =
                typingSpeedStats.mean,

            stdTypingMsPerInsertedChar =
                typingSpeedStats.standardDeviation,

            typingMsPerInsertedCharM2 =
                typingSpeedStats.m2,

            avgReviewTimeMs =
                reviewTimeStats.mean,

            stdReviewTimeMs =
                reviewTimeStats.standardDeviation,

            reviewTimeM2 =
                reviewTimeStats.m2,

            avgMaxIdleTimeMs =
                maxIdleTimeStats.mean,

            stdMaxIdleTimeMs =
                maxIdleTimeStats.standardDeviation,

            maxIdleTimeM2 =
                maxIdleTimeStats.m2,

            avgIdleEvents =
                idleEventsStats.mean,

            stdIdleEvents =
                idleEventsStats.standardDeviation,

            idleEventsM2 =
                idleEventsStats.m2,

            avgDeleteRatio =
                deleteRatioStats.mean,

            stdDeleteRatio =
                deleteRatioStats.standardDeviation,

            deleteRatioM2 =
                deleteRatioStats.m2,

            avgLongPauses =
                longPausesStats.mean,

            stdLongPauses =
                longPausesStats.standardDeviation,

            longPausesM2 =
                longPausesStats.m2
        )
    }

    /*
     * Merges new field samples into an existing baseline.
     *
     * The previous samples are not needed because their
     * information is already represented by:
     *
     * - sampleCount
     * - mean
     * - M2
     */
    fun merge(
        existingBaseline: FormBehaviorBaseline,
        newSamples: List<FieldBehaviorSample>
    ): FormBehaviorBaseline? {

        if (existingBaseline.sampleCount <= 0) {
            return calculate(
                samples =
                    newSamples
            )
        }

        val validNewSamples =
            newSamples.filter { sample ->
                sample.isValidForBaseline()
            }

        if (validNewSamples.isEmpty()) {
            return null
        }

        val previousCount =
            existingBaseline.sampleCount

        val newCount =
            validNewSamples.size

        val combinedCount =
            previousCount +
                    newCount

        val dwellTimeStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgDwellTimeMs,

                existingM2 =
                    existingBaseline.dwellTimeM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.dwellTimeMs.toDouble()
                    }
            )

        val thinkingTimeStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgThinkingTimeMs,

                existingM2 =
                    existingBaseline.thinkingTimeM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.thinkingTimeMs.toDouble()
                    }
            )

        val typingSpeedStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgTypingMsPerInsertedChar,

                existingM2 =
                    existingBaseline.typingMsPerInsertedCharM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.typingMsPerInsertedChar
                    }
            )

        val reviewTimeStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgReviewTimeMs,

                existingM2 =
                    existingBaseline.reviewTimeM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.reviewTimeMs.toDouble()
                    }
            )

        val maxIdleTimeStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgMaxIdleTimeMs,

                existingM2 =
                    existingBaseline.maxIdleTimeM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.maxIdleTimeMs.toDouble()
                    }
            )

        val idleEventsStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgIdleEvents,

                existingM2 =
                    existingBaseline.idleEventsM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.idleEvents.toDouble()
                    }
            )

        val deleteRatioStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgDeleteRatio,

                existingM2 =
                    existingBaseline.deleteRatioM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.deleteRatio
                    }
            )

        val longPausesStats =
            mergeStats(
                existingCount =
                    previousCount,

                existingMean =
                    existingBaseline.avgLongPauses,

                existingM2 =
                    existingBaseline.longPausesM2,

                newValues =
                    validNewSamples.map { sample ->
                        sample.longPauses.toDouble()
                    }
            )

        return FormBehaviorBaseline(
            sampleCount =
                combinedCount,

            validSessionCount =
                existingBaseline.validSessionCount +
                        1,

            calculatedAtMs =
                System.currentTimeMillis(),

            avgDwellTimeMs =
                dwellTimeStats.mean,

            stdDwellTimeMs =
                dwellTimeStats.standardDeviation,

            dwellTimeM2 =
                dwellTimeStats.m2,

            avgThinkingTimeMs =
                thinkingTimeStats.mean,

            stdThinkingTimeMs =
                thinkingTimeStats.standardDeviation,

            thinkingTimeM2 =
                thinkingTimeStats.m2,

            avgTypingMsPerInsertedChar =
                typingSpeedStats.mean,

            stdTypingMsPerInsertedChar =
                typingSpeedStats.standardDeviation,

            typingMsPerInsertedCharM2 =
                typingSpeedStats.m2,

            avgReviewTimeMs =
                reviewTimeStats.mean,

            stdReviewTimeMs =
                reviewTimeStats.standardDeviation,

            reviewTimeM2 =
                reviewTimeStats.m2,

            avgMaxIdleTimeMs =
                maxIdleTimeStats.mean,

            stdMaxIdleTimeMs =
                maxIdleTimeStats.standardDeviation,

            maxIdleTimeM2 =
                maxIdleTimeStats.m2,

            avgIdleEvents =
                idleEventsStats.mean,

            stdIdleEvents =
                idleEventsStats.standardDeviation,

            idleEventsM2 =
                idleEventsStats.m2,

            avgDeleteRatio =
                deleteRatioStats.mean,

            stdDeleteRatio =
                deleteRatioStats.standardDeviation,

            deleteRatioM2 =
                deleteRatioStats.m2,

            avgLongPauses =
                longPausesStats.mean,

            stdLongPauses =
                longPausesStats.standardDeviation,

            longPausesM2 =
                longPausesStats.m2
        )
    }

    /*
     * Calculates statistics for a new group of samples.
     */
    private fun calculateStats(
        values: List<Double>
    ): RunningStatistics {

        require(values.isNotEmpty()) {
            "Cannot calculate statistics from an empty list"
        }

        val mean =
            values.average()

        val m2 =
            values.sumOf { value ->

                val difference =
                    value -
                            mean

                difference *
                        difference
            }

        return RunningStatistics(
            mean =
                mean,

            m2 =
                m2.coerceAtLeast(
                    minimumValue =
                        0.0
                ),

            standardDeviation =
                calculateStandardDeviation(
                    count =
                        values.size,

                    m2 =
                        m2
                )
        )
    }

    /*
     * Combines the statistics of the previous baseline with
     * the statistics of a new group of field samples.
     *
     * This is a parallel variance merge and does not require
     * the original historical values.
     */
    private fun mergeStats(
        existingCount: Int,
        existingMean: Double,
        existingM2: Double,
        newValues: List<Double>
    ): RunningStatistics {

        val newStats =
            calculateStats(
                values =
                    newValues
            )

        val newCount =
            newValues.size

        val combinedCount =
            existingCount +
                    newCount

        val meanDifference =
            newStats.mean -
                    existingMean

        val combinedMean =
            existingMean +
                    meanDifference *
                    newCount /
                    combinedCount

        val combinedM2 =
            existingM2 +
                    newStats.m2 +
                    meanDifference *
                    meanDifference *
                    existingCount *
                    newCount /
                    combinedCount

        val safeM2 =
            combinedM2.coerceAtLeast(
                minimumValue =
                    0.0
            )

        return RunningStatistics(
            mean =
                combinedMean,

            m2 =
                safeM2,

            standardDeviation =
                calculateStandardDeviation(
                    count =
                        combinedCount,

                    m2 =
                        safeM2
                )
        )
    }

    /*
     * Calculates sample standard deviation.
     */
    private fun calculateStandardDeviation(
        count: Int,
        m2: Double
    ): Double {

        if (count < 2) {
            return 0.0
        }

        return sqrt(
            m2 /
                    (
                            count -
                                    1
                            )
        )
    }

    /*
     * Ensures one invalid calculated value cannot corrupt the
     * accumulated baseline.
     */
    private fun FieldBehaviorSample.isValidForBaseline():
            Boolean {

        return fieldId.isNotBlank() &&
                dwellTimeMs >= 0L &&
                thinkingTimeMs >= 0L &&
                reviewTimeMs >= 0L &&
                maxIdleTimeMs >= 0L &&
                idleEvents >= 0 &&
                longPauses >= 0 &&
                typingMsPerInsertedChar.isFinite() &&
                typingMsPerInsertedChar >= 0.0 &&
                deleteRatio.isFinite() &&
                deleteRatio in 0.0..1.0
    }
}


/*
 * Internal statistics used while creating or merging one
 * baseline feature.
 */
private data class RunningStatistics(
    val mean: Double,
    val m2: Double,
    val standardDeviation: Double
)