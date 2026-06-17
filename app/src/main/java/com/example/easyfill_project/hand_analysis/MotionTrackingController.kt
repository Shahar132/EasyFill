package com.example.easyfill_project.hand_analysis

import android.content.Context
import android.util.Log
import com.example.easyfill_project.distress_scoring.DistressScoringManager
import kotlinx.coroutines.*


private const val EXCEED_RATIO_THRESHOLD = 0.10f//10%
private const val ACC_VARIATION_FACTOR = 1.5f
private const val GYRO_VARIATION_FACTOR = 1.5f

private const val GYRO_EXCEED_RATIO_THRESHOLD = 0.10f

class MotionTrackingController(
    context: Context
) {


    private val distressManager = DistressScoringManager()

    private val motionManager = MotionSensorManager(context)
    private val baselineRepository = MotionBaselineRepository()

    private var job: Job? = null


    fun startTracking(scope: CoroutineScope) {
        job?.cancel()

        job = scope.launch {
            Log.d("MOTION_FLOW", "Starting 30 sec baseline")

            motionManager.start()
            delay(30_000)

            val baselineResult = motionManager.stopAndAnalyze()

            baselineRepository.saveBaseline(baselineResult)

            Log.d("MOTION_FLOW", "Baseline result: $baselineResult")
            Log.d("MOTION_FLOW", "Baseline acceleration P95: ${baselineResult.accelerationP95}")

            while (isActive) {
                motionManager.start()
                delay(5_000)

                val currentResult = motionManager.stopAndAnalyze()

                Log.d("MOTION_CURRENT", currentResult.toString())

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
        val exceedCount = current.accelerationValues.count {
            it > baseline.accelerationP95
        }

        val exceedRatio =
            if (current.accelerationValues.isNotEmpty()) {
                exceedCount.toFloat() / current.accelerationValues.size
            } else {
                0f
            }

        val gyroExceedCount = current.gyroscopeValues.count {
            it > baseline.gyroscopeP95
        }

        val gyroExceedRatio =
            if (current.gyroscopeValues.isNotEmpty()) {
                gyroExceedCount.toFloat() / current.gyroscopeValues.size
            } else {
                0f
            }

        var score = 0

        // Rule 1: more than 10% of current values passed baseline P95.
        if (exceedRatio > EXCEED_RATIO_THRESHOLD) {
            score += 1
        }

        // Rule 2: acceleration variation is much higher than baseline.
        if (current.accelerationVariation > baseline.accelerationVariation * ACC_VARIATION_FACTOR) {
            score += 1
        }

        // Rule 3: gyroscope variation is much higher than baseline.
        if (current.gyroscopeVariation > baseline.gyroscopeVariation * GYRO_VARIATION_FACTOR) {
            score += 1
        }

        // Rule 4: more than 10% of current gyroscope values passed baseline gyroscope P95.
        if (gyroExceedRatio > GYRO_EXCEED_RATIO_THRESHOLD) {
            score += 1
        }

        Log.d(
            "MOTION_SCORE",
            "score=$score, exceedCount=$exceedCount, exceedRatio=$exceedRatio, baselineP95=${baseline.accelerationP95}"
        )

        distressManager.updateHandScore(score)

        distressManager.printStatus()

        if(distressManager.isDistressDetected()){
            Log.d("DISTRESS","Distress detected")
        }
    }

    fun stopTracking() {
        job?.cancel()
        motionManager.stopAndAnalyze()
        Log.d("MOTION_FLOW", "Motion tracking stopped")
    }
}