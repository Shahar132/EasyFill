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

    private fun updateTotal() {
        _totalScore.value =
            _handScore.value + _voiceScore.value + _faceScore.value

        printStatus()
    }

    fun isDistressDetected(): Boolean {
        return _totalScore.value >= 2
    }

    fun printStatus() {
        Log.d(
            "DISTRESS_SCORE",
            """
            Hand score = ${_handScore.value}
            Voice score = ${_voiceScore.value}
            Face score = ${_faceScore.value}
            Total score = ${_totalScore.value}
            Distress = ${isDistressDetected()}
            """.trimIndent()
        )
    }
}