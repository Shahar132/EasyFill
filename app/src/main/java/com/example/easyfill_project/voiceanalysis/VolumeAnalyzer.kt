package com.example.easyfill_project.voiceanalysis

import kotlin.math.log10
import kotlin.math.sqrt

object VolumeAnalyzer {

    // Gets raw audio samples from AudioRecord
    // buffer = the audio numbers
    // read = how many samples were actually recorded
    fun calculateRms(buffer: ShortArray, read: Int): Double {
        if (read <= 0) return 0.0

        var sum = 0.0

        // Go over each audio sample
        for (i in 0 until read) {
            val sample = buffer[i].toDouble()

            // Square the sample so negative/positive values both count as energy
            sum += sample * sample
        }

        // RMS = average loudness/energy of the audio chunk
        return sqrt(sum / read)
    }

    // Converts RMS into decibels-like volume value
    fun rmsToDb(rms: Double): Double {
        // coerceAtLeast(1.0) prevents log10(0), which would crash/return invalid value
        return 20 * log10(rms.coerceAtLeast(1.0))
    }
}