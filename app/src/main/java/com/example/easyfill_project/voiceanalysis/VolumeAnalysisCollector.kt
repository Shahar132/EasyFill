package com.example.easyfill_project.voiceanalysis

import android.util.Log
import kotlin.math.sqrt

class VolumeAnalysisCollector {

    private val volumeValues = mutableListOf<Double>()
    private var startTimeMillis: Long = 0L

    fun start() {
        volumeValues.clear()
        startTimeMillis = System.currentTimeMillis()
    }

    fun addVolume(volumeDb: Double) {
        volumeValues.add(volumeDb)

        Log.d("VOICE_VOLUME", "Current volume dB: $volumeDb")
    }

    fun stopAndAnalyze(): VolumeAnalysisResult? {
        val durationSeconds =
            (System.currentTimeMillis() - startTimeMillis) / 1000.0

        // Ignore silence 0 volume / invalid readings
        val validValues = volumeValues.filter { it > 1.0 }

        if (durationSeconds < 10 || validValues.isEmpty()) {
            Log.d(
                "VOICE_VOLUME",
                "Not enough valid audio data: $durationSeconds seconds"
            )
            return null
        }

        Log.d("VOICE_VOLUME", "All valid values: $validValues")

        val averageVolume = validValues.average()
        val maxVolume = validValues.maxOrNull() ?: 0.0
        val minVolume = validValues.minOrNull() ?: 0.0
        val volumeRange = maxVolume - minVolume
        val volumeVariation = standardDeviation(validValues)

        val volumeStressScore = when {
            volumeRange > 25 -> 30
            volumeRange > 15 -> 20
            volumeRange > 8 -> 10
            else -> 0
        }

        val result = VolumeAnalysisResult(
            durationSeconds = durationSeconds,
            averageVolume = averageVolume,
            maxVolume = maxVolume,
            minVolume = minVolume,
            volumeRange = volumeRange,
            volumeVariation = volumeVariation,
            volumeStressScore = volumeStressScore
        )

        Log.d("VOICE_VOLUME", "Result: $result")

        return result
    }

    private fun standardDeviation(values: List<Double>): Double {
        val average = values.average()
        val variance = values.map { (it - average) * (it - average) }.average()
        return sqrt(variance)
    }
}

data class VolumeAnalysisResult(
    val durationSeconds: Double,
    val averageVolume: Double,
    val maxVolume: Double,
    val minVolume: Double,
    val volumeRange: Double,
    val volumeVariation: Double,
    val volumeStressScore: Int
)