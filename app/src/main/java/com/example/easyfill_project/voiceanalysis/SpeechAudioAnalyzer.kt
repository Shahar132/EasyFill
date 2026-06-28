package com.example.easyfill_project.voiceanalysis

import kotlin.math.abs

data class SpeechAnalysisResult(
    val finalText: String,
    val durationSeconds: Double,
    val isReliable: Boolean,
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

    private var speechEndTime = 0L


//    fun startSpeech() {
//        speechStartTime = System.currentTimeMillis()
//    }

//    fun startSpeech() {
//        speechStartTime = System.currentTimeMillis()
//        speechEndTime = 0L
//        lastPartialTime = 0L
//        finalText = ""
//
//        rmsValues.clear()
//        pauseDurationsMs.clear()
//    }

    fun startSpeech() {
        // Start a new clean speech analysis session.
        // This does not reset the saved baseline.
        // It only clears data from the previous recording.
        speechStartTime = System.currentTimeMillis()
        speechEndTime = 0L
        lastPartialTime = 0L
        finalText = ""

        rmsValues.clear()
        pauseDurationsMs.clear()
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
        val endTime =
            if (speechEndTime > 0L) speechEndTime else System.currentTimeMillis()

        val durationSeconds =
            if (speechStartTime > 0) (endTime - speechStartTime) / 1000.0 else 0.0


        val minReliableDurationSeconds = 15.0
        val isReliable = durationSeconds >= minReliableDurationSeconds

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
            isReliable = isReliable,
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


    fun stopSpeech() {
        speechEndTime = System.currentTimeMillis()
    }
}