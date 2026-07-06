package com.example.easyfill_project.form_behavior_analysis
// it takes several FieldBehaviorSample objects and creates one FormBehaviorBaseline
// by calculating average and standard deviation for each behavior parameter.

import android.os.SystemClock
// Used to save the time when the baseline was calculated.

import kotlin.math.pow
// Used for square calculation in standard deviation.

import kotlin.math.sqrt
// Used for square root in standard deviation.

object FormBehaviorBaselineCalculator {
    // Singleton object responsible for creating a baseline from samples.

    fun calculate(samples: List<FieldBehaviorSample>): FormBehaviorBaseline? {
        // Receives a list of field behavior samples and returns a baseline.

        if (samples.isEmpty()) {
            return null
        }
        // If there are no samples, baseline cannot be created.

        return FormBehaviorBaseline(
            // Create and return the baseline object.

            sampleCount = samples.size,
            // Number of samples used to build the baseline.

            calculatedAtMs = SystemClock.elapsedRealtime(),
            // Time when this baseline was created.

            avgDwellTimeMs = samples.map { it.dwellTimeMs.toDouble() }.averageValue(),
            stdDwellTimeMs = samples.map { it.dwellTimeMs.toDouble() }.standardDeviation(),
            // Average and standard deviation of time spent in field.

            avgThinkingTimeMs = samples.map { it.thinkingTimeMs.toDouble() }.averageValue(),
            stdThinkingTimeMs = samples.map { it.thinkingTimeMs.toDouble() }.standardDeviation(),
            // Average and standard deviation of thinking time before typing.

            avgTypingMsPerInsertedChar = samples.map { it.typingMsPerInsertedChar }.averageValue(),
            stdTypingMsPerInsertedChar = samples.map { it.typingMsPerInsertedChar }.standardDeviation(),
            // Average and standard deviation of typing speed per inserted character.

            avgReviewTimeMs = samples.map { it.reviewTimeMs.toDouble() }.averageValue(),
            stdReviewTimeMs = samples.map { it.reviewTimeMs.toDouble() }.standardDeviation(),
            // Average and standard deviation of review time after typing.

            avgMaxIdleTimeMs = samples.map { it.maxIdleTimeMs.toDouble() }.averageValue(),
            stdMaxIdleTimeMs = samples.map { it.maxIdleTimeMs.toDouble() }.standardDeviation(),
            // Average and standard deviation of longest idle time.

            avgIdleEvents = samples.map { it.idleEvents.toDouble() }.averageValue(),
            stdIdleEvents = samples.map { it.idleEvents.toDouble() }.standardDeviation(),
            // Average and standard deviation of idle events count.

            avgDeleteRatio = samples.map { it.deleteRatio }.averageValue(),
            stdDeleteRatio = samples.map { it.deleteRatio }.standardDeviation(),
            // Average and standard deviation of delete ratio.

            avgLongPauses = samples.map { it.longPauses.toDouble() }.averageValue(),
            stdLongPauses = samples.map { it.longPauses.toDouble() }.standardDeviation()
            // Average and standard deviation of long pauses.
        )
    }

    private fun List<Double>.averageValue(): Double {
        // Helper function to calculate average safely.

        if (isEmpty()) return 0.0
        // If list is empty, return 0 instead of crashing.

        return average()
        // Kotlin built-in average calculation.
    }

    private fun List<Double>.standardDeviation(): Double {
        // Helper function to calculate standard deviation.

        if (size < 2) return 0.0
        // Need at least 2 values to calculate meaningful standard deviation.

        val avg = average()
        // Calculate the average of the list.

        val variance = sumOf { value ->
            (value - avg).pow(2)
        } / (size - 1)
        // Calculate sample variance.

        return sqrt(variance)
        // Standard deviation is the square root of variance.
    }
}