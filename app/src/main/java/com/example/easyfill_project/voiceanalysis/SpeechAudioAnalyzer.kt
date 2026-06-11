package com.example.easyfill_project.voiceanalysis

import kotlin.math.abs

data class SpeechAnalysisResult(
    val finalText: String,
    val durationSeconds: Double,
    val speechRateWordsPerSecond: Double,
    val averageRms: Float,
    val maxRms: Float,
    val rmsVariation: Float,
    val pauseCount: Int,
    val pauseDurationsMs: List<Long>,
    val averagePauseMs: Double,
    val hesitationCount: Int
)

class SpeechAudioAnalyzer {

    private val pauseThresholdMs = 1200L

    private var speechStartTime = 0L
    private var lastPartialTime = 0L
    private var finalText = ""

    private val rmsValues = mutableListOf<Float>()
    private val pauseDurationsMs = mutableListOf<Long>()

    fun startSpeech() {
        speechStartTime = System.currentTimeMillis()
    }

    fun addRms(rms: Float) {
        rmsValues.add(rms)
    }

    fun updatePartialText(text: String) {
        val now = System.currentTimeMillis()

        if (lastPartialTime > 0) {
            val pauseDuration = now - lastPartialTime

            if (pauseDuration >= pauseThresholdMs) {
                pauseDurationsMs.add(pauseDuration)
            }
        }

        lastPartialTime = now
        finalText = text
    }

    fun updateFinalText(text: String) {
        finalText = text
    }

    fun analyze(): SpeechAnalysisResult {
        val endTime = System.currentTimeMillis()

        val durationSeconds =
            if (speechStartTime > 0) (endTime - speechStartTime) / 1000.0 else 0.0

        val words = finalText.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        val speechRate =
            if (durationSeconds > 0) words.size / durationSeconds else 0.0

        val averageRms =
            if (rmsValues.isNotEmpty()) rmsValues.average().toFloat() else 0f

        val maxRms = rmsValues.maxOrNull() ?: 0f

        val rmsVariation =
            if (rmsValues.isNotEmpty()) {
                rmsValues.map { abs(it - averageRms) }.average().toFloat()
            } else 0f

        val hesitationWords = listOf("אממ", "אה", "אהה", "כאילו", "טוב", "בעצם")
        val hesitationCount = words.count { it in hesitationWords }

        return SpeechAnalysisResult(
            finalText = finalText,
            durationSeconds = durationSeconds,
            speechRateWordsPerSecond = speechRate,
            averageRms = averageRms,
            maxRms = maxRms,
            rmsVariation = rmsVariation,
            pauseCount = pauseDurationsMs.size,
            pauseDurationsMs = pauseDurationsMs.toList(),
            averagePauseMs = if (pauseDurationsMs.isNotEmpty()) pauseDurationsMs.average() else 0.0,
            hesitationCount = hesitationCount
        )
    }
}