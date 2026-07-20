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

    companion object {

                /*
         * A recording must contain at least 10 seconds
         * of detected speech before it can participate
         * in distress scoring.
         *
         * Because hand motion is calculated every 5 seconds,
         * a 10-second recording should normally contain
         * approximately two hand-motion windows.
         */
        const val MIN_RELIABLE_DURATION_SECONDS = 10.0
    }

    private val pauseThresholdMs = 1200L

    private var speechStartTime = 0L
    private var speechEndTime = 0L
    private var lastPartialTime = 0L

    private var finalText = ""

    private val rmsValues = mutableListOf<Float>()
    private val pauseDurationsMs = mutableListOf<Long>()

    /**
     * Starts a completely new recording analysis.
     *
     * All information from a previous recording is cleared here.
     */
    fun startSpeech() {

        speechStartTime = System.currentTimeMillis()
        speechEndTime = 0L
        lastPartialTime = 0L

        finalText = ""

        rmsValues.clear()
        pauseDurationsMs.clear()
    }

    /**
     * Saves one RMS value received during the recording.
     */
    fun addRms(rms: Float) {

        // Ignore RMS callbacks received before recording starts.
        if (speechStartTime <= 0L) {
            return
        }

        rmsValues.add(rms)
    }

    /**
     * Receives partial speech-recognition results.
     *
     * The time between partial results is used as an
     * approximation for detecting pauses.
     */
    fun updatePartialText(text: String) {

        if (speechStartTime <= 0L) {
            return
        }

        val now = System.currentTimeMillis()

        if (lastPartialTime > 0L) {

            val pauseDuration = now - lastPartialTime

            if (pauseDuration >= pauseThresholdMs) {
                pauseDurationsMs.add(pauseDuration)
            }
        }

        lastPartialTime = now
        finalText = text
    }

    /**
     * Saves the final speech-recognition text.
     */
    fun updateFinalText(text: String) {
        finalText = text
    }

    /**
     * Marks the exact time at which recording stopped.
     *
     * This should be called before analyze().
     */
    fun stopSpeech() {

        if (speechStartTime > 0L) {
            speechEndTime = System.currentTimeMillis()
        }
    }

    /**
     * Calculates all voice-analysis measurements
     * for the completed recording.
     */
    fun analyze(): SpeechAnalysisResult {

        val endTime =
            when {
                speechEndTime > 0L -> speechEndTime
                speechStartTime > 0L -> System.currentTimeMillis()
                else -> 0L
            }

        val durationSeconds =
            if (
                speechStartTime > 0L &&
                endTime >= speechStartTime
            ) {
                (endTime - speechStartTime) / 1000.0
            } else {
                0.0
            }

        val words =
            finalText
                .trim()
                .split("\\s+".toRegex())
                .filter { word ->
                    word.isNotBlank()
                }

        /*
         * A recording is reliable only when:
         *
         * 1. It lasted at least 15 seconds.
         * 2. Speech recognition detected at least one word.
         *
         * This prevents a silent 15-second recording from
         * being treated as a valid voice measurement.
         */
        val isReliable =
            durationSeconds >= MIN_RELIABLE_DURATION_SECONDS &&
                    words.isNotEmpty()

        val speechRate =
            if (durationSeconds > 0.0) {
                words.size / durationSeconds
            } else {
                0.0
            }

        val averageRms =
            if (rmsValues.isNotEmpty()) {
                rmsValues.average().toFloat()
            } else {
                0f
            }

        val maxRms =
            rmsValues.maxOrNull() ?: 0f

        val rmsVariation =
            if (rmsValues.isNotEmpty()) {

                rmsValues
                    .map { rms ->
                        abs(rms - averageRms)
                    }
                    .average()
                    .toFloat()

            } else {
                0f
            }

        val hesitationWords =
            setOf(
                "אממ",
                "אה",
                "אהה",
                "כאילו",
                "טוב",
                "בעצם"
            )

        val hesitationCount =
            words.count { originalWord ->

                val cleanedWord =
                    originalWord.trim(
                        '.',
                        ',',
                        '?',
                        '!',
                        ':',
                        ';',
                        '"',
                        '\''
                    )

                cleanedWord in hesitationWords
            }

        val averagePauseMs =
            if (pauseDurationsMs.isNotEmpty()) {
                pauseDurationsMs.average()
            } else {
                0.0
            }

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
            averagePauseMs = averagePauseMs,
            hesitationCount = hesitationCount
        )
    }
}