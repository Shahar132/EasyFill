package com.example.easyfill_project.distress_scoring

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stores the current distress score of each active collector.
 *
 * Every collector must provide a score from 0 to 4.
 * The final alert decision will later use score history
 * and repeated snapshots instead of one immediate value.
 */
object DistressScoringManager {

    private val _handScore =
        MutableStateFlow(0)

    val handScore: StateFlow<Int> =
        _handScore

    private val _voiceScore =
        MutableStateFlow(0)

    val voiceScore: StateFlow<Int> =
        _voiceScore

    private val _faceScore =
        MutableStateFlow(0)

    val faceScore: StateFlow<Int> =
        _faceScore

    private val _formBehaviorScore =
        MutableStateFlow(0)

    val formBehaviorScore: StateFlow<Int> =
        _formBehaviorScore

    private val _totalScore =
        MutableStateFlow(0)

    val totalScore: StateFlow<Int> =
        _totalScore

    fun updateHandScore(
        score: Int
    ) {
        _handScore.value =
            normalizeScore(score)

        updateTotal()
    }

    fun updateVoiceScore(
        score: Int
    ) {
        _voiceScore.value =
            normalizeScore(score)

        updateTotal()
    }

    fun updateFaceScore(
        score: Int
    ) {
        _faceScore.value =
            normalizeScore(score)

        updateTotal()
    }

    fun updateFormBehaviorScore(
        score: Int
    ) {
        _formBehaviorScore.value =
            normalizeScore(score)

        updateTotal()
    }

    /**
     * Returns true when at least one collector
     * currently reports a possible distress signal.
     *
     * This is not yet the final alert decision.
     */
    fun hasActiveSignal(): Boolean {
        return _totalScore.value > 0
    }

    /**
     * Temporary compatibility function.
     *
     * The final implementation will require
     * repeated elevated snapshots before returning true.
     */
    fun isDistressDetected(): Boolean {
        return hasActiveSignal()
    }

    fun resetCurrentScoresAfterAlertRead() {

        _handScore.value = 0
        _voiceScore.value = 0
        _faceScore.value = 0
        _formBehaviorScore.value = 0

        updateTotal()
    }

    private fun normalizeScore(
        score: Int
    ): Int {
        return score.coerceIn(
            minimumValue = 0,
            maximumValue = 4
        )
    }

    private fun updateTotal() {

        _totalScore.value =
            _handScore.value +
                    _voiceScore.value +
                    _faceScore.value +
                    _formBehaviorScore.value

        printStatus()
    }

    private fun printStatus() {

        Log.d(
            TAG,
            "Hand=${_handScore.value} | " +
                    "Voice=${_voiceScore.value} | " +
                    "Face=${_faceScore.value} | " +
                    "Form=${_formBehaviorScore.value} | " +
                    "Total=${_totalScore.value}"
        )
    }

    private const val TAG =
        "DISTRESS_SCORE"
}