package com.example.easyfill_project.hand_analysis

import kotlin.math.abs
import kotlin.math.sqrt

//Receives raw accelerometer/gyroscope values and calculates features like variation, max movement, and shake count.

class MotionAnalyzer {

    private val minReliableDurationSeconds = 15.0
    private val shakeThreshold = 15f

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

        val shakeCount = accelerationValues.count { it > shakeThreshold }

        return MotionAnalysisResult(
            durationSeconds = durationSeconds,
            averageAcceleration = avgAcc,
            maxAcceleration = maxAcc,
            accelerationVariation = accVariation,
            averageGyroscope = avgGyro,
            maxGyroscope = maxGyro,
            gyroscopeVariation = gyroVariation,
            shakeCount = shakeCount,
            isReliable = durationSeconds >= minReliableDurationSeconds
        )
    }

    private fun List<Float>.averageOrZero(): Float =
        if (isNotEmpty()) average().toFloat() else 0f

    private fun List<Float>.variationFrom(avg: Float): Float =
        if (isNotEmpty()) map { abs(it - avg) }.average().toFloat() else 0f
}