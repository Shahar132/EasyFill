package com.example.easyfill_project.distress_scoring

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

object DistressScoringManager {

    // Hand movement score: 0–4
    private val _handScore = MutableStateFlow(0)
    val handScore: StateFlow<Int> = _handScore

    // Voice analysis score: 0–4
    private val _voiceScore = MutableStateFlow(0)
    val voiceScore: StateFlow<Int> = _voiceScore

    // Face score: currently not used, but kept for future use
    private val _faceScore = MutableStateFlow(0)
    val faceScore: StateFlow<Int> = _faceScore

    // Field behavior score: 0–4
    private val _formBehaviorScore = MutableStateFlow(0)
    val formBehaviorScore: StateFlow<Int> = _formBehaviorScore

    // Current interaction mode:
    // FORM_FILLING = user is filling form fields (not using recording)
    // VOICE_RECORDING = user is recording speech
    private val _mode = MutableStateFlow(DistressMode.FORM_FILLING)
    val mode: StateFlow<DistressMode> = _mode

    // Final combined distress score: 0–4
    private val _totalScore = MutableStateFlow(0)
    val totalScore: StateFlow<Int> = _totalScore

    fun updateHandScore(score: Int) {
        _handScore.value = score.coerceIn(0, 4)
        updateTotal()
    }

    fun updateVoiceScore(score: Int) {
        _voiceScore.value = score.coerceIn(0, 4)
        updateTotal()
    }

    fun updateFaceScore(score: Int) {
        _faceScore.value = score.coerceIn(0, 4)
        updateTotal()
    }

    fun updateFormBehaviorScore(score: Int) {
        _formBehaviorScore.value = score.coerceIn(0, 4)
        updateTotal()
    }

    fun setMode(mode: DistressMode) {
        _mode.value = mode
        updateTotal()
    }


    //the mode decides which analyses count right now and the weights.
    private fun updateTotal() {
        val hand = _handScore.value
        val voice = _voiceScore.value
        val form = _formBehaviorScore.value

        val weightedScore = when (_mode.value) {

            // When user is recording:
            // Voice is the main signal, hand is supporting.
            DistressMode.VOICE_RECORDING -> {
                voice * 0.60 + hand * 0.40
            }

            // When user is filling the form without speech:
            // Field behavior is the main signal, hand is supporting.
            DistressMode.FORM_FILLING -> {
                form * 0.60 + hand * 0.40
            }
        }

        _totalScore.value = weightedScore.roundToInt().coerceIn(0, 4)

        printStatus()
    }

    fun isDistressDetected(): Boolean {
        return _totalScore.value >= 2
    }

    fun printStatus() {
        Log.d(
            "DISTRESS_SCORE",
            """
            Mode = ${_mode.value}
            Hand score = ${_handScore.value}
            Voice score = ${_voiceScore.value}
            Form behavior score = ${_formBehaviorScore.value}
            Face score = ${_faceScore.value}
            Total score = ${_totalScore.value}
            Distress = ${isDistressDetected()}
            """.trimIndent()
        )
    }
}