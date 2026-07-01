package com.example.easyfill_project.distress_scoring

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DistressScoringManager {

    private val _handScore = MutableStateFlow(0)
    val handScore: StateFlow<Int> = _handScore

    private val _voiceScore = MutableStateFlow(0)
    val voiceScore: StateFlow<Int> = _voiceScore

    private val _faceScore = MutableStateFlow(0)
    val faceScore: StateFlow<Int> = _faceScore

    private val _formBehaviorScore = MutableStateFlow(0)
    val formBehaviorScore: StateFlow<Int> = _formBehaviorScore

    private val _totalScore = MutableStateFlow(0)
    val totalScore: StateFlow<Int> = _totalScore

    fun updateHandScore(score: Int) {
        _handScore.value = score
        updateTotal()
    }

    fun updateVoiceScore(score: Int) {
        _voiceScore.value = score
        updateTotal()
    }

    fun updateFaceScore(score: Int) {
        _faceScore.value = score
        updateTotal()
    }

    fun updateFormBehaviorScore(score: Int) {
        _formBehaviorScore.value = score
        updateTotal()
    }

    private fun updateTotal() {
        _totalScore.value =
            _handScore.value +
                    _voiceScore.value +
                    _faceScore.value +
                    _formBehaviorScore.value

        printStatus()
    }

    fun isDistressDetected(): Boolean {
        return _totalScore.value > 0
    }

    fun printStatus() {
        Log.d(
            "DISTRESS_SCORE",
            """
            Hand score = ${_handScore.value}
            Voice score = ${_voiceScore.value}
            Face score = ${_faceScore.value}
            Form behavior score = ${_formBehaviorScore.value}
            Total score = ${_totalScore.value}
            Distress = ${isDistressDetected()}
            """.trimIndent()
        )
    }

    fun resetCurrentScoresAfterAlertRead() {
        _handScore.value = 0
        _voiceScore.value = 0
        _faceScore.value = 0
        _formBehaviorScore.value = 0

        updateTotal()
    }



}