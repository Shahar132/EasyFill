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


    private val distressManager = DistressScoringManager
    private val motionManager = MotionSensorManager(context)
    private val baselineRepository = MotionBaselineRepository()

    private var job: Job? = null


    fun startTracking(scope: CoroutineScope) {
        job?.cancel()

        job = scope.launch {
            Log.d("MOTION_FLOW", "Starting 10 sec baseline")

            motionManager.start()
            delay(10_000)

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

        // Check whether the gyroscope detected meaningful movement.
        // This helps distinguish real hand movement from simply holding
        // the phone at a different angle (which mainly affects the accelerometer).
        val gyroVariationHigh =
            current.gyroscopeVariation > baseline.gyroscopeVariation * GYRO_VARIATION_FACTOR

        val gyroP95High =
            gyroExceedRatio > GYRO_EXCEED_RATIO_THRESHOLD

        val hasGyroscopeMovement =
            gyroVariationHigh || gyroP95High

        var score = 0

        // Rule 1: More than 10% of current acceleration values passed the baseline acceleration P95.
        // Count this only if the gyroscope also detected movement.
        // This prevents a stable phone angle or holding position from raising the score.
        if (exceedRatio > EXCEED_RATIO_THRESHOLD && hasGyroscopeMovement) {
            score += 1
        }

        // Rule 2: acceleration variation is much higher than baseline.
        // Count it only if gyroscope also detected movement,
        // to avoid scoring stable phone angle / holding position.
        if (
            current.accelerationVariation > baseline.accelerationVariation * ACC_VARIATION_FACTOR &&
            hasGyroscopeMovement
        ) {
            score += 1
        }

        // Rule 3: Gyroscope variation is much higher than the user's baseline.
        if (gyroVariationHigh) {
            score += 1
        }

        // Rule 4: More than 10% of current gyroscope values passed the baseline gyroscope P95.
        if (gyroP95High) {
            score += 1
        }

        Log.d(
            "MOTION_SCORE",
            "score=$score, " +
                    "accExceedCount=$exceedCount, " +
                    "accExceedRatio=$exceedRatio, " +
                    "gyroExceedCount=$gyroExceedCount, " +
                    "gyroExceedRatio=$gyroExceedRatio, " +
                    "baselineAccP95=${baseline.accelerationP95}, " +
                    "baselineGyroP95=${baseline.gyroscopeP95}, " +
                    "currentAccVar=${current.accelerationVariation}, " +
                    "accVarThreshold=${baseline.accelerationVariation * ACC_VARIATION_FACTOR}, " +
                    "currentGyroVar=${current.gyroscopeVariation}, " +
                    "gyroVarThreshold=${baseline.gyroscopeVariation * GYRO_VARIATION_FACTOR}"
        )

        // First update the hand score.
        // This also recalculates the current combined total score.
        distressManager.updateHandScore(score)

        // Report that one complete 5-second measurement window has ended.
        // This must be called after updateHandScore(), because the confirmation
        // manager needs the newly calculated combined total score.
        distressManager.completeMeasurementWindow()

        distressManager.printStatus()

        if (distressManager.isDistressDetected()) {
            Log.d("DISTRESS", "Distress detected")
        }
    }

    fun stopTracking() {
        job?.cancel()
        motionManager.stopAndAnalyze()
        Log.d("MOTION_FLOW", "Motion tracking stopped")
    }
}