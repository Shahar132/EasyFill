package com.example.easyfill_project.hand_analysis
//this class  manages first 30 sec baseline + later 5 sec samples.
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

class MotionTrackingController(
    context: Context
) {
    private val motionManager = MotionSensorManager(context)
    private val baselineRepository = MotionBaselineRepository()

    private var job: Job? = null

    fun startTracking(scope: CoroutineScope) {
        job?.cancel()

        job = scope.launch {
            Log.d("MOTION_FLOW", "Starting 30 sec baseline")

            motionManager.start()
            delay(30_000)

            val baselineResult = motionManager.stopAndAnalyze()//stopping after 30 seconds for baseline

            baselineRepository.saveBaseline(baselineResult)//save to firestore

            Log.d("MOTION_FLOW", "Baseline result: $baselineResult")
            //starts a new short sample, analyzes it
            while (isActive) {
                motionManager.start()
                delay(5_000)//sample each 5 seconds

                val currentResult = motionManager.stopAndAnalyze()

                Log.d("MOTION_CURRENT", currentResult.toString())//current measure

                // Later:
                // compare currentResult to baseline from Firestore
            }
        }
    }

    fun stopTracking() {
        job?.cancel()
        motionManager.stopAndAnalyze()
        Log.d("MOTION_FLOW", "Motion tracking stopped")
    }
}