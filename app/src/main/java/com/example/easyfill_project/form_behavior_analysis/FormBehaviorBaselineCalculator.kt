package com.example.easyfill_project.form_behavior_analysis

import android.os.SystemClock
import kotlin.math.pow
import kotlin.math.sqrt

object FormBehaviorBaselineCalculator {

    fun calculate(samples: List<FieldBehaviorSample>): FormBehaviorBaseline? {
        if (samples.isEmpty()) {
            return null
        }

        return FormBehaviorBaseline(
            sampleCount = samples.size,
            calculatedAtMs = SystemClock.elapsedRealtime(),

            avgDwellTimeMs = samples.map { it.dwellTimeMs.toDouble() }.averageValue(),
            stdDwellTimeMs = samples.map { it.dwellTimeMs.toDouble() }.standardDeviation(),

            avgThinkingTimeMs = samples.map { it.thinkingTimeMs.toDouble() }.averageValue(),
            stdThinkingTimeMs = samples.map { it.thinkingTimeMs.toDouble() }.standardDeviation(),

            avgTypingMsPerInsertedChar = samples.map { it.typingMsPerInsertedChar }.averageValue(),
            stdTypingMsPerInsertedChar = samples.map { it.typingMsPerInsertedChar }.standardDeviation(),

            avgReviewTimeMs = samples.map { it.reviewTimeMs.toDouble() }.averageValue(),
            stdReviewTimeMs = samples.map { it.reviewTimeMs.toDouble() }.standardDeviation(),

            avgMaxIdleTimeMs = samples.map { it.maxIdleTimeMs.toDouble() }.averageValue(),
            stdMaxIdleTimeMs = samples.map { it.maxIdleTimeMs.toDouble() }.standardDeviation(),

            avgIdleEvents = samples.map { it.idleEvents.toDouble() }.averageValue(),
            stdIdleEvents = samples.map { it.idleEvents.toDouble() }.standardDeviation(),

            avgDeleteRatio = samples.map { it.deleteRatio }.averageValue(),
            stdDeleteRatio = samples.map { it.deleteRatio }.standardDeviation(),

            avgLongPauses = samples.map { it.longPauses.toDouble() }.averageValue(),
            stdLongPauses = samples.map { it.longPauses.toDouble() }.standardDeviation()
        )
    }

    private fun List<Double>.averageValue(): Double {
        if (isEmpty()) return 0.0
        return average()
    }

    private fun List<Double>.standardDeviation(): Double {
        if (size < 2) return 0.0

        val avg = average()
        val variance = sumOf { value ->
            (value - avg).pow(2)
        } / (size - 1)

        return sqrt(variance)
    }
}