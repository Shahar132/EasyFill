package com.example.easyfill_project.distress_scoring

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

object DistressScoringManager {

    /*
     * ============================================================
     * CURRENT DISTRESS SCORES
     * ============================================================
     */

    // Current representative hand-movement score: 0–4.
    private val _handScore = MutableStateFlow(0)
    val handScore: StateFlow<Int> = _handScore

    // Current voice-analysis score: 0–4.
    private val _voiceScore = MutableStateFlow(0)
    val voiceScore: StateFlow<Int> = _voiceScore

    // Face score: currently unused, but kept for future development.
    private val _faceScore = MutableStateFlow(0)
    val faceScore: StateFlow<Int> = _faceScore

    // Current form-behavior score: 0–4.
    private val _formBehaviorScore = MutableStateFlow(0)
    val formBehaviorScore: StateFlow<Int> = _formBehaviorScore

    /**
     * Current interaction mode.
     *
     * FORM_FILLING:
     * The user is filling fields without recording.
     *
     * VOICE_RECORDING:
     * The user is recording speech.
     */
    private val _mode =
        MutableStateFlow(DistressMode.FORM_FILLING)

    val mode: StateFlow<DistressMode> = _mode

    // Current combined distress score: 0–4.
    private val _totalScore = MutableStateFlow(0)
    val totalScore: StateFlow<Int> = _totalScore

    /*
     * ============================================================
     * FORM-FILLING WINDOW EVENTS
     * ============================================================
     */

    /**
     * Emits one event after every completed form-filling
     * measurement window.
     *
     * These events are used by DistressConfirmationManager
     * for the two-consecutive-window confirmation rule.
     */
    private val _completedWindows =
        MutableSharedFlow<DistressWindowResult>(
            extraBufferCapacity = 10
        )

    val completedWindows: SharedFlow<DistressWindowResult> =
        _completedWindows.asSharedFlow()

    /*
     * ============================================================
     * VOICE-RECORDING SESSION INPUTS
     * ============================================================
     */

    /**
     * Final hand average for the current recording.
     *
     * This value is calculated by MotionTrackingController.
     *
     * It remains a Double so that precision is preserved
     * until the final weighted calculation.
     */
    private var recordingHandAverage: Double? = null

    /**
     * Final voice score for the current recording.
     *
     * This value is calculated after the user's voice analysis
     * is compared with the stored Firestore baseline.
     */
    private var recordingVoiceScore: Int? = null

    /**
     * Prevents the same recording result from being emitted twice.
     */
    private var recordingResultPublished = false

    /*
     * ============================================================
     * VOICE-RECORDING LIFECYCLE EVENTS
     * ============================================================
     */

    /**
     * Tells MotionTrackingController that a new recording started.
     *
     * MotionTrackingController should:
     *
     * 1. Clear old recording hand-window scores.
     * 2. Begin collecting hand scores for the new recording.
     */
    private val _voiceRecordingStarted =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1
        )

    val voiceRecordingStarted: SharedFlow<Unit> =
        _voiceRecordingStarted.asSharedFlow()

    /**
     * Tells MotionTrackingController that audio recording stopped.
     *
     * MotionTrackingController should:
     *
     * 1. Stop collecting hand windows.
     * 2. Calculate the average hand score.
     * 3. Submit the average to this manager.
     */
    private val _voiceRecordingStopped =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1
        )

    val voiceRecordingStopped: SharedFlow<Unit> =
        _voiceRecordingStopped.asSharedFlow()

    /*
     * ============================================================
     * COMPLETED VOICE-RECORDING EVENTS
     * ============================================================
     */

    /**
     * Emits exactly one result for each fully analyzed recording.
     *
     * This is separate from completedWindows because voice
     * recordings do not use the two-consecutive-window rule.
     */
    private val _completedVoiceRecordings =
        MutableSharedFlow<VoiceRecordingDistressResult>(
            extraBufferCapacity = 10
        )

    val completedVoiceRecordings:
            SharedFlow<VoiceRecordingDistressResult> =
        _completedVoiceRecordings.asSharedFlow()

    /*
     * ============================================================
     * VOICE-RECORDING SESSION FUNCTIONS
     * ============================================================
     */

    /**
     * Starts a new voice-recording distress session.
     *
     * Call this exactly once when the user presses the
     * microphone button and recording begins.
     */
    fun beginVoiceRecordingSession() {

        /*
         * Remove all results from a previous recording.
         */
        recordingHandAverage = null
        recordingVoiceScore = null
        recordingResultPublished = false

        /*
         * Clear old displayed voice and hand values.
         *
         * This prevents a previous recording's score from being
         * displayed as part of the new recording.
         */
        _handScore.value = 0
        _voiceScore.value = 0
        _totalScore.value = 0

        /*
         * Keep the manager in VOICE_RECORDING mode until both:
         *
         * 1. The hand average arrives.
         * 2. The voice score arrives.
         */
        _mode.value = DistressMode.VOICE_RECORDING

        /*
         * Tell MotionTrackingController to clear its old list
         * and begin collecting hand windows.
         */
        val emitted =
            _voiceRecordingStarted.tryEmit(Unit)

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
            New recording session started.
            startEventEmitted=$emitted
            """.trimIndent()
        )

        printStatus()
    }

    /**
     * Reports that audio recording has stopped.
     *
     * This function does not calculate the final distress result.
     *
     * It asks MotionTrackingController to calculate and submit
     * the final hand average.
     */
    fun requestVoiceRecordingStop() {

        if (_mode.value != DistressMode.VOICE_RECORDING) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                """
                Recording stop ignored.
                There is no active voice-recording session.
                """.trimIndent()
            )
            return
        }

        val emitted =
            _voiceRecordingStopped.tryEmit(Unit)

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
            Recording stop requested.
            stopEventEmitted=$emitted
            Waiting for final hand average and voice score.
            """.trimIndent()
        )
    }

    /**
     * Receives the final hand average from
     * MotionTrackingController.
     *
     * Example:
     *
     * Window scores:
     * [1, 2, 2]
     *
     * Average:
     * 1.666...
     *
     * The average is not rounded here.
     */
    fun submitVoiceRecordingHandAverage(
        average: Double
    ) {

        if (_mode.value != DistressMode.VOICE_RECORDING) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                """
                Hand average ignored.
                Voice-recording mode is not active.
                receivedAverage=$average
                """.trimIndent()
            )
            return
        }

        /*
         * coerceIn does not round the value.
         *
         * It only ensures that the average remains
         * inside the valid range of 0.0–4.0.
         */
        recordingHandAverage =
            average.coerceIn(0.0, 4.0)

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
            Recording hand average received.
            handAverage=$recordingHandAverage
            """.trimIndent()
        )

        /*
         * If the voice score has already arrived,
         * the result will now be calculated.
         *
         * Otherwise, the manager continues waiting.
         */
        tryPublishVoiceRecordingResult()
    }

    /**
     * Receives the final voice score after speech analysis
     * and Firestore baseline comparison have finished.
     */
    fun submitVoiceRecordingVoiceScore(
        score: Int
    ) {

        if (_mode.value != DistressMode.VOICE_RECORDING) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                """
                Voice score ignored.
                Voice-recording mode is not active.
                receivedScore=$score
                """.trimIndent()
            )
            return
        }

        recordingVoiceScore =
            score.coerceIn(0, 4)

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
            Recording voice score received.
            voiceScore=$recordingVoiceScore
            """.trimIndent()
        )

        /*
         * If the hand average has already arrived,
         * the result will now be calculated.
         *
         * Otherwise, the manager continues waiting.
         */
        tryPublishVoiceRecordingResult()
    }

    /**
     * Calculates and publishes the recording result only
     * after both required inputs are available.
     *
     * It does not matter which input arrives first.
     */
    private fun tryPublishVoiceRecordingResult() {

        /*
         * Never publish the same recording twice.
         */
        if (recordingResultPublished) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                "Recording result was already published."
            )
            return
        }

        /*
         * Wait if MotionTrackingController has not yet
         * submitted the recording hand average.
         */
        val handAverage =
            recordingHandAverage ?: run {

                Log.d(
                    "VOICE_RECORDING_SESSION",
                    "Waiting for recording hand average."
                )

                return
            }

        /*
         * Wait if voice analysis has not yet submitted
         * the final voice score.
         */
        val voiceScore =
            recordingVoiceScore ?: run {

                Log.d(
                    "VOICE_RECORDING_SESSION",
                    "Waiting for recording voice score."
                )

                return
            }

        /*
         * Both values are now available.
         *
         * Keep the hand average as a Double.
         *
         * Example:
         *
         * voice = 3
         * handAverage = 1.5
         *
         * weightedScore =
         * 3 × 0.60 + 1.5 × 0.40
         *
         * weightedScore =
         * 1.8 + 0.6
         *
         * weightedScore =
         * 2.4
         */
        val weightedScore =
            voiceScore * 0.60 +
                    handAverage * 0.40

        /*
         * Round only once, after the complete weighted
         * calculation has finished.
         *
         * coerceIn then guarantees that the final integer
         * remains inside the valid 0–4 range.
         */
        val finalLevel =
            weightedScore
                .roundToInt()
                .coerceIn(0, 4)

        val result =
            VoiceRecordingDistressResult(
                level = finalLevel,
                voiceScore = voiceScore,
                handAverage = handAverage,
                weightedScore = weightedScore
            )

        /*
         * Mark the recording as published before emitting,
         * preventing another callback from publishing it again.
         */
        recordingResultPublished = true

        /*
         * Update the public StateFlow values.
         *
         * The actual weighted calculation above used the precise
         * Double hand average.
         *
         * _handScore is an Int StateFlow, so we round only the
         * displayed representative hand value here.
         */
        _voiceScore.value = voiceScore

        _handScore.value =
            handAverage
                .roundToInt()
                .coerceIn(0, 4)

        _totalScore.value = finalLevel

        val emitted =
            _completedVoiceRecordings.tryEmit(result)

        Log.d(
            "VOICE_RECORDING_RESULT",
            """
            Completed voice-recording result:
            voiceScore=$voiceScore
            handAverage=$handAverage
            weightedScore=$weightedScore
            finalLevel=$finalLevel
            emitted=$emitted
            """.trimIndent()
        )

        /*
         * The recording result has already been calculated
         * with VOICE_RECORDING weights.
         *
         * It is now safe to return to form-filling mode.
         *
         * We intentionally do not call updateTotal() here,
         * because that would immediately replace the recording
         * result using form-filling weights.
         */
        _mode.value = DistressMode.FORM_FILLING

        printStatus()
    }

    /**
     * Cancels an incomplete voice-recording session.
     *
     * Use this if:
     *
     * - Firestore baseline loading fails.
     * - The user is not authenticated.
     * - Voice analysis fails.
     * - The recording must be abandoned.
     */
    fun cancelVoiceRecordingSession() {

        /*
         * Clear pending recording inputs.
         */
        recordingHandAverage = null
        recordingVoiceScore = null
        recordingResultPublished = false

        /*
         * Return to form mode before sending the stop event.
         *
         * MotionTrackingController may calculate an average after
         * receiving the stop event, but this manager will ignore it
         * because VOICE_RECORDING mode is no longer active.
         */
        _mode.value = DistressMode.FORM_FILLING

        /*
         * Tell MotionTrackingController to stop collecting and
         * clear the current recording hand session.
         */
        val stopEventEmitted =
            _voiceRecordingStopped.tryEmit(Unit)

        /*
         * Remove incomplete recording values.
         */
        _voiceScore.value = 0
        _totalScore.value = 0

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
            Voice-recording session cancelled.
            stopEventEmitted=$stopEventEmitted
            """.trimIndent()
        )

        printStatus()
    }

    /*
     * ============================================================
     * NORMAL SCORE UPDATE FUNCTIONS
     * ============================================================
     */

    /**
     * Updates the current hand score during normal form filling.
     *
     * Individual recording hand windows are no longer stored here.
     * MotionTrackingController owns and averages those windows.
     */
    fun updateHandScore(score: Int) {

        _handScore.value =
            score.coerceIn(0, 4)

        updateTotal()
    }

    /**
     * Updates the regular current voice score.
     *
     * Do not call this for a completed recording.
     *
     * Completed recording voice scores must use:
     *
     * submitVoiceRecordingVoiceScore(score)
     */
    fun updateVoiceScore(score: Int) {

        _voiceScore.value =
            score.coerceIn(0, 4)

        updateTotal()
    }

    fun updateFaceScore(score: Int) {

        _faceScore.value =
            score.coerceIn(0, 4)

        updateTotal()
    }

    fun updateFormBehaviorScore(score: Int) {

        _formBehaviorScore.value =
            score.coerceIn(0, 4)

        updateTotal()
    }

    /**
     * Changes the general interaction mode.
     *
     * Use this for normal form-filling state changes.
     *
     * Do not use this to start a voice recording.
     * Start recordings with beginVoiceRecordingSession().
     */
    fun setMode(mode: DistressMode) {

        _mode.value = mode
        updateTotal()
    }

    /**
     * Recalculates the current score for normal live state updates.
     *
     * This function does not emit a completed measurement event.
     */
    private fun updateTotal() {

        val hand = _handScore.value
        val voice = _voiceScore.value
        val form = _formBehaviorScore.value

        val weightedScore =
            when (_mode.value) {

                DistressMode.VOICE_RECORDING -> {
                    voice * 0.60 +
                            hand * 0.40
                }

                DistressMode.FORM_FILLING -> {
                    form * 0.60 +
                            hand * 0.40
                }
            }

        _totalScore.value =
            weightedScore
                .roundToInt()
                .coerceIn(0, 4)

        printStatus()
    }

    /*
     * ============================================================
     * COMPLETED FORM-FILLING WINDOWS
     * ============================================================
     */

    /**
     * Publishes one completed normal measurement window.
     *
     * This should be used only during FORM_FILLING.
     */
    fun completeMeasurementWindow() {

        /*
         * Defensive protection:
         *
         * Even if another class accidentally calls this function
         * during recording, no normal window will be emitted.
         */
        if (_mode.value != DistressMode.FORM_FILLING) {

            Log.d(
                "DISTRESS_WINDOW",
                """
                Window ignored because mode is ${_mode.value}.
                Normal completed windows are only allowed during FORM_FILLING.
                """.trimIndent()
            )

            return
        }

        val result =
            DistressWindowResult(
                level = _totalScore.value,
                handScore = _handScore.value,
                voiceScore = _voiceScore.value,
                formBehaviorScore =
                    _formBehaviorScore.value,
                faceScore = _faceScore.value,
                mode = _mode.value
            )

        val emitted =
            _completedWindows.tryEmit(result)

        Log.d(
            "DISTRESS_WINDOW",
            """
            Completed form-filling window:
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