package com.example.easyfill_project.voiceanalysis

import kotlin.math.abs

object SpeechRateScorer {

    private const val HEBREW_NORMAL_SPEECH_RATE = 2.57

    fun calculateVoiceScore(
        baselineSpeechRate: Double?,
        currentSpeechRate: Double
    ): Int {
        if (baselineSpeechRate == null || baselineSpeechRate <= 0.0 || currentSpeechRate <= 0.0) {
            return 0
        }

        val baselineDeviation =
            abs(currentSpeechRate - baselineSpeechRate) / baselineSpeechRate

        val normDeviation =
            abs(currentSpeechRate - HEBREW_NORMAL_SPEECH_RATE) / HEBREW_NORMAL_SPEECH_RATE

        val speechRateScore =
            0.7 * baselineDeviation + 0.3 * normDeviation

        return when {
            speechRateScore >= 0.50 -> 2
            speechRateScore >= 0.30 -> 1
            else -> 0
        }
    }
}