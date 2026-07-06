package com.example.easyfill_project.hand_analysis

import kotlin.math.abs
import kotlin.math.sqrt

// Receives raw accelerometer/gyroscope values and calculates motion features.
class MotionAnalyzer {

    private val minReliableDurationSeconds = 10.0

    private var startTime = 0L

    private val accelerationValues = mutableListOf<Float>()
    private val gyroscopeValues = mutableListOf<Float>()

    fun start() {
        startTime = System.currentTimeMillis()
        accelerationValues.clear()
        gyroscopeValues.clear()
    }

    fun addAccelerometer(x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)
        accelerationValues.add(magnitude)
    }

    fun addGyroscope(x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)
        gyroscopeValues.add(magnitude)
    }

    fun analyze(): MotionAnalysisResult {
        val durationSeconds =
            (System.currentTimeMillis() - startTime) / 1000.0

        val avgAcc = accelerationValues.averageOrZero()
        val maxAcc = accelerationValues.maxOrNull() ?: 0f
        val accVariation = accelerationValues.variationFrom(avgAcc)

        val avgGyro = gyroscopeValues.averageOrZero()
        val maxGyro = gyroscopeValues.maxOrNull() ?: 0f
        val gyroVariation = gyroscopeValues.variationFrom(avgGyro)

        // Added: personalized percentile calculation.
        val accP95 = accelerationValues.percentile95()
        val gyroP95 = gyroscopeValues.percentile95()

        // Changed: fixed threshold was removed.
        val shakeCount = 0

        return MotionAnalysisResult(
            durationSeconds = durationSeconds,

            averageAcceleration = avgAcc,
            maxAcceleration = maxAcc,
            accelerationVariation = accVariation,

            averageGyroscope = avgGyro,
            maxGyroscope = maxGyro,
            gyroscopeVariation = gyroVariation,

            shakeCount = shakeCount,
            isReliable = durationSeconds >= minReliableDurationSeconds,

            accelerationP95 = accP95,
            gyroscopeP95 = gyroP95,

            accelerationExceedCount = 0,

            // Added: copy raw values for comparison in current 5-sec windows.
            accelerationValues = accelerationValues.toList(),
            gyroscopeValues = gyroscopeValues.toList()

        )
    }

    private fun List<Float>.averageOrZero(): Float =
        if (isNotEmpty()) average().toFloat() else 0f

    private fun List<Float>.variationFrom(avg: Float): Float =
        if (isNotEmpty()) map { abs(it - avg) }.average().toFloat() else 0f

    private fun List<Float>.percentile95(): Float {
        if (isEmpty()) return 0f

        val sorted = sorted()
        val index = ((sorted.size - 1) * 0.95).toInt()
        return sorted[index]
    }
}