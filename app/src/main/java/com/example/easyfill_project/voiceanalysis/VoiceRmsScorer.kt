package com.example.easyfill_project.voiceanalysis

object VoiceRmsScorer {

    private const val MILD_FACTOR = 1.5
    private const val HIGH_FACTOR = 2.0

    fun calculateScore(
        baselineVariation: Double?,
        currentVariation: Double
    ): Int {

        if (baselineVariation == null ||
            baselineVariation <= 0.0 ||
            currentVariation <= 0.0
        ) {
            return 0
        }

        return when {
            currentVariation >= baselineVariation * HIGH_FACTOR -> 2
            currentVariation >= baselineVariation * MILD_FACTOR -> 1
            else -> 0
        }
    }
}