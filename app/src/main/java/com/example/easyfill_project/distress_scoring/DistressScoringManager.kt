package com.example.easyfill_project.distress_scoring

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

object DistressScoringManager {

    // Hand movement score: 0–4.
    private val _handScore = MutableStateFlow(0)
    val handScore: StateFlow<Int> = _handScore

    // Voice analysis score: 0–4.
    private val _voiceScore = MutableStateFlow(0)
    val voiceScore: StateFlow<Int> = _voiceScore

    // Face score: currently not used, but kept for future use.
    private val _faceScore = MutableStateFlow(0)
    val faceScore: StateFlow<Int> = _faceScore

    // Field-behavior score: 0–4.
    private val _formBehaviorScore = MutableStateFlow(0)
    val formBehaviorScore: StateFlow<Int> = _formBehaviorScore

    /**
     * Current interaction mode.
     *
     * FORM_FILLING:
     * The user fills fields without voice recording.
     *
     * VOICE_RECORDING:
     * The user is currently recording speech.
     */
    private val _mode = MutableStateFlow(DistressMode.FORM_FILLING)
    val mode: StateFlow<DistressMode> = _mode

    // Current combined distress score: 0–4.
    private val _totalScore = MutableStateFlow(0)
    val totalScore: StateFlow<Int> = _totalScore

    /**
     * Emits one event for every completed measurement window.
     *
     * This is different from totalScore.
     *
     * totalScore stores the current value.
     * completedWindows reports that another measurement window has ended.
     *
     * SharedFlow is used because two consecutive windows may contain
     * the exact same distress level.
     */
    private val _completedWindows =
        MutableSharedFlow<DistressWindowResult>(
            extraBufferCapacity = 10
        )

    val completedWindows: SharedFlow<DistressWindowResult> =
        _completedWindows.asSharedFlow()

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

    /**
     * Recalculates the current combined distress level.
     *
     * This function updates the current StateFlow value,
     * but it does not count as a completed measurement window.
     */
    private fun updateTotal() {
        val hand = _handScore.value
        val voice = _voiceScore.value
        val form = _formBehaviorScore.value

        val weightedScore = when (_mode.value) {

            // Voice is the main signal while recording.
            DistressMode.VOICE_RECORDING -> {
                voice * 0.60 + hand * 0.40
            }

            // Form behavior is the main signal while filling fields.
            DistressMode.FORM_FILLING -> {
                form * 0.60 + hand * 0.40
            }
        }

        _totalScore.value =
            weightedScore
                .roundToInt()
                .coerceIn(0, 4)

        printStatus()
    }

    /**
     * Call this function exactly once when a real measurement window ends.
     *
     * At the moment, MotionTrackingController calls it after every
     * completed 5-second hand-motion window.
     *
     * Do not call this function from updateTotal(), because updateTotal()
     * may run several times inside one measurement period.
     */
    fun completeMeasurementWindow() {
        val result = DistressWindowResult(
            level = _totalScore.value,
            handScore = _handScore.value,
            voiceScore = _voiceScore.value,
            formBehaviorScore = _formBehaviorScore.value,
            faceScore = _faceScore.value,
            mode = _mode.value
        )

        val emitted = _completedWindows.tryEmit(result)

        Log.d(
            "DISTRESS_WINDOW",
            """
            Completed window:
            level=${result.level}
            hand=${result.handScore}
            voice=${result.voiceScore}
            form=${result.formBehaviorScore}
            face=${result.faceScore}
            mode=${result.mode}
            emitted=$emitted
            """.trimIndent()
        )
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