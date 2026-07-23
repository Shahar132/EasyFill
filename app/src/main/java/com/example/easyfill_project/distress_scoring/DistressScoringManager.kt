package com.example.easyfill_project.distress_scoring

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt
import android.os.SystemClock

object DistressScoringManager {

    /*
     * ============================================================
     * CURRENT DISTRESS SCORES
     * ============================================================
     */

    /*
  * Public StateFlows continue using Int values so the existing
  * UI and DistressSnapshot code do not need to change.
  *
  * Separate nullable Double values below are used for the real
  * weighted calculation.
  *
  * null means:
  * the modality is unavailable.
  *
  * 0.0 means:
  * the modality was analyzed and found no distress.
  */

    // Representative hand score displayed by the UI.
    private val _handScore =
        MutableStateFlow(0)

    val handScore: StateFlow<Int> =
        _handScore

    // Representative voice score displayed by the UI.
    private val _voiceScore =
        MutableStateFlow(0)

    val voiceScore: StateFlow<Int> =
        _voiceScore

    // Representative face score displayed by the UI.
    private val _faceScore =
        MutableStateFlow(0)

    val faceScore: StateFlow<Int> =
        _faceScore

    // Representative form-behavior score displayed by the UI.
    private val _formBehaviorScore =
        MutableStateFlow(0)

    val formBehaviorScore: StateFlow<Int> =
        _formBehaviorScore

    /*
     * Precise form-filling values used for weighted fusion.
     *
     * These are nullable so unavailable information is not
     * incorrectly treated as a calm score of zero.
     */
    private var currentFormHandScore: Double? =
        null

    private var currentFormFaceScore: Double? =
        null

    private var currentFormBehaviorScore: Double? =
        null

    /*
     * Timestamp belonging to the most recent reliable face score.
     *
     * Face results are produced frequently, but the last score must
     * not remain active forever after the user leaves the camera.
     */
    private var currentFormFaceTimestampMs: Long? =
        null

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
    /*
 * Final modality results for the current recording.
 *
 * A null score means that the modality completed but did not
 * have enough reliable information.
 */
    private var recordingHandAverage: Double? =
        null

    private var recordingVoiceScore: Double? =
        null

    private var recordingFaceAverage: Double? =
        null

    /*
     * Completion must be stored separately from the score.
     *
     * Example:
     *
     * recordingFaceCompleted = true
     * recordingFaceAverage = null
     *
     * means face analysis finished, but no reliable face samples
     * were available during the recording.
     */
    private var recordingHandCompleted =
        false

    private var recordingVoiceCompleted =
        false

    private var recordingFaceCompleted =
        false

    /*
     * Prevents publishing the same recording result twice.
     */
    private var recordingResultPublished =
        false

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
        /*
 * Clear every result and completion flag from the
 * previous recording.
 */
        recordingHandAverage = null
        recordingVoiceScore = null
        recordingFaceAverage = null

        recordingHandCompleted = false
        recordingVoiceCompleted = false
        recordingFaceCompleted = false

        recordingResultPublished = false

        /*
         * Clear old displayed voice and hand values.
         *
         * This prevents a previous recording's score from being
         * displayed as part of the new recording.
         */
        /*
 * Remove values displayed from the previous recording.
 */
        _handScore.value = 0
        _voiceScore.value = 0
        _faceScore.value = 0
        _totalScore.value = 0

        /*
          * Keep the manager in VOICE_RECORDING mode until:
          *
          * 1. Hand processing completes.
          * 2. Voice processing completes.
          * 3. Face processing completes.
          *
          * A component may complete with a null score when its
          * information was unavailable.
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
            Waiting for hand, voice and face analysis to complete.
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
    /**
     * Receives the completed hand result for the current recording.
     *
     * average:
     *
     * 0.0 means reliable hand analysis found no distress.
     * null means no reliable hand window was available.
     */
    fun submitVoiceRecordingHandAverage(
        average: Double?
    ) {
        if (_mode.value != DistressMode.VOICE_RECORDING) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                """
            Hand result ignored.
            Voice-recording mode is not active.
            receivedAverage=$average
            """.trimIndent()
            )

            return
        }

        /*
         * Store the score when available.
         *
         * Keep null when hand information was unavailable.
         */
        recordingHandAverage =
            average?.coerceIn(
                minimumValue = 0.0,
                maximumValue = 4.0
            )

        /*
         * Processing finished even when the result is null.
         */
        recordingHandCompleted = true

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
        Recording hand analysis completed.
        handAvailable=${recordingHandAverage != null}
        handAverage=$recordingHandAverage
        """.trimIndent()
        )

        tryPublishVoiceRecordingResult()
    }

    /**
     * Receives the final voice score after speech analysis
     * and Firestore baseline comparison have finished.
     */
    /**
     * Receives the completed voice result for the current recording.
     *
     * score:
     *
     * 0 means reliable voice analysis found no distress.
     * null means voice information was unavailable or unreliable.
     */
    fun submitVoiceRecordingVoiceScore(
        score: Int?
    ) {
        if (_mode.value != DistressMode.VOICE_RECORDING) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                """
            Voice result ignored.
            Voice-recording mode is not active.
            receivedScore=$score
            """.trimIndent()
            )

            return
        }

        /*
         * Preserve the voice value as Double for the final
         * weighted calculation.
         */
        recordingVoiceScore =
            score
                ?.coerceIn(0, 4)
                ?.toDouble()

        recordingVoiceCompleted = true

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
        Recording voice analysis completed.
        voiceAvailable=${recordingVoiceScore != null}
        voiceScore=$recordingVoiceScore
        """.trimIndent()
        )

        tryPublishVoiceRecordingResult()
    }

    /**
     * Receives the average of all reliable face results collected
     * during the current recording.
     *
     * average:
     *
     * 0.0 means face analysis was reliable and showed no distress.
     * null means no reliable face result was available.
     */
    fun submitVoiceRecordingFaceAverage(
        average: Double?
    ) {
        if (_mode.value != DistressMode.VOICE_RECORDING) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                """
            Face result ignored.
            Voice-recording mode is not active.
            receivedAverage=$average
            """.trimIndent()
            )

            return
        }

        recordingFaceAverage =
            average?.coerceIn(
                minimumValue = 0.0,
                maximumValue = 4.0
            )

        recordingFaceCompleted = true

        Log.d(
            "VOICE_RECORDING_SESSION",
            """
        Recording face analysis completed.
        faceAvailable=${recordingFaceAverage != null}
        faceAverage=$recordingFaceAverage
        """.trimIndent()
        )

        tryPublishVoiceRecordingResult()
    }

    /**
     * Publishes the final recording result only after all three
     * modality pipelines have finished.
     *
     * A completed modality may have a null score. In that case,
     * the remaining available weights are normalized.
     */
    private fun tryPublishVoiceRecordingResult() {

        /*
         * Never publish one recording more than once.
         */
        if (recordingResultPublished) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                "Recording result was already published."
            )

            return
        }

        /*
         * Wait for completion rather than waiting for non-null scores.
         *
         * A null score may be a valid completed result meaning
         * that the modality was unavailable.
         */
        if (!recordingHandCompleted) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                "Waiting for recording hand analysis."
            )

            return
        }

        if (!recordingVoiceCompleted) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                "Waiting for recording voice analysis."
            )

            return
        }

        if (!recordingFaceCompleted) {
            Log.d(
                "VOICE_RECORDING_SESSION",
                "Waiting for recording face analysis."
            )

            return
        }

        /*
         * Recording weights:
         *
         * Voice = 35%
         * Face  = 35%
         * Hand  = 30%
         *
         * Missing modalities are excluded and the available
         * weights are normalized.
         */
        val weightedScore =
            calculateNormalizedWeightedScore(
                components = listOf(
                    WeightedComponent(
                        score = recordingVoiceScore,
                        weight = RECORDING_VOICE_WEIGHT
                    ),

                    WeightedComponent(
                        score = recordingFaceAverage,
                        weight = RECORDING_FACE_WEIGHT
                    ),

                    WeightedComponent(
                        score = recordingHandAverage,
                        weight = RECORDING_HAND_WEIGHT
                    )
                )
            ) ?: 0.0

        /*
         * Round only after the complete weighted calculation.
         */
        val finalLevel =
            weightedScore
                .roundToInt()
                .coerceIn(0, 4)

        /*
 * Preserve null values inside the completed result.
 *
 * Do not replace an unavailable modality with zero.
 *
 * zero:
 * available and calm.
 *
 * null:
 * unavailable.
 */
        val result =
            VoiceRecordingDistressResult(
                level = finalLevel,

                /*
                 * Voice is internally stored as Double for weighting,
                 * but the voice scoring system produces an integer
                 * distress level.
                 */
                voiceScore =
                    recordingVoiceScore
                        ?.roundToInt()
                        ?.coerceIn(0, 4),

                /*
                 * Keep the precise recording face average.
                 */
                faceAverage =
                    recordingFaceAverage,

                /*
                 * Keep the precise recording hand average.
                 */
                handAverage =
                    recordingHandAverage,

                /*
                 * Availability values show which modalities actually
                 * participated in the normalized weighted result.
                 */
                voiceAvailable =
                    recordingVoiceScore != null,

                faceAvailable =
                    recordingFaceAverage != null,

                handAvailable =
                    recordingHandAverage != null,

                weightedScore =
                    weightedScore
            )

        /*
         * Mark the result before emitting it.
         */
        recordingResultPublished = true

        /*
         * Update representative public scores.
         *
         * These public StateFlows remain integers for the
         * existing UI.
         */
        _voiceScore.value =
            recordingVoiceScore
                ?.roundToInt()
                ?.coerceIn(0, 4)
                ?: 0

        _faceScore.value =
            recordingFaceAverage
                ?.roundToInt()
                ?.coerceIn(0, 4)
                ?: 0

        _handScore.value =
            recordingHandAverage
                ?.roundToInt()
                ?.coerceIn(0, 4)
                ?: 0

        _totalScore.value =
            finalLevel

        val emitted =
            _completedVoiceRecordings.tryEmit(
                result
            )

        Log.d(
            "VOICE_RECORDING_RESULT",
            """
            Completed multimodal recording result:
        
            voiceCompleted=$recordingVoiceCompleted
            voiceAvailable=${result.voiceAvailable}
            voiceScore=${result.voiceScore}
        
            faceCompleted=$recordingFaceCompleted
            faceAvailable=${result.faceAvailable}
            faceAverage=${result.faceAverage}
        
            handCompleted=$recordingHandCompleted
            handAvailable=${result.handAvailable}
            handAverage=${result.handAverage}
        
            weightedScore=${result.weightedScore}
            finalLevel=${result.level}
            emitted=$emitted
            """.trimIndent()
                )

        /*
         * The final recording result has already been calculated
         * using recording weights.
         *
         * Return to form mode without calling updateTotal(),
         * because that would immediately overwrite the result
         * using form weights.
         */
        _mode.value =
            DistressMode.FORM_FILLING

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
        recordingFaceAverage = null

        recordingHandCompleted = false
        recordingVoiceCompleted = false
        recordingFaceCompleted = false

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
        _faceScore.value = 0
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
    /**
     * Updates the latest hand score during form filling.
     */
    fun updateHandScore(
        score: Int
    ) {
        val safeScore =
            score.coerceIn(0, 4)

        /*
         * Store the exact available score internally.
         */
        currentFormHandScore =
            safeScore.toDouble()

        /*
         * Keep the existing UI StateFlow updated.
         */
        _handScore.value =
            safeScore

        updateTotal()
    }

    /**
     * Marks hand information as unavailable.
     *
     * This is different from a valid hand score of zero.
     */
    fun clearFormHandScore() {
        currentFormHandScore = null
        _handScore.value = 0

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

    /**
     * Stores the latest reliable continuous face score during
     * normal form filling.
     *
     * score is kept as Float/Double until final weighting.
     */
    fun updateFormFaceScore(
        score: Float,
        timestampMs: Long
    ) {
        if (_mode.value != DistressMode.FORM_FILLING) {
            /*
             * Recording face results are collected separately
             * and submitted as one final average.
             */
            return
        }

        if (!score.isFinite()) {
            return
        }

        val safeScore =
            score.coerceIn(
                minimumValue = 0f,
                maximumValue = 4f
            )

        currentFormFaceScore =
            safeScore.toDouble()

        /*
 * Store the local monotonic reception time.
 *
 * This guarantees that the timestamp uses the same clock
 * as getFreshFormFaceScore().
 */
        currentFormFaceTimestampMs =
            SystemClock.uptimeMillis()

        /*
         * Round only for the representative UI value.
         *
         * Weighted fusion still uses currentFormFaceScore.
         */
        _faceScore.value =
            safeScore
                .roundToInt()
                .coerceIn(0, 4)

        updateTotal()

        Log.d(
            "DISTRESS_FACE_SCORE",
            """
        Reliable form face score received:
        continuousScore=$safeScore
        displayedLevel=${_faceScore.value}
        timestampMs=$timestampMs
        """.trimIndent()
        )
    }

    /**
     * Marks the form face component as unavailable.
     *
     * Called when the form screen closes.
     */
    fun clearFormFaceScore() {
        currentFormFaceScore = null
        currentFormFaceTimestampMs = null
        _faceScore.value = 0

        updateTotal()

        Log.d(
            "DISTRESS_FACE_SCORE",
            "Form face score cleared."
        )
    }

    /**
     * Updates the latest available form-behavior score.
     */
    fun updateFormBehaviorScore(
        score: Int
    ) {
        val safeScore =
            score.coerceIn(0, 4)

        currentFormBehaviorScore =
            safeScore.toDouble()

        _formBehaviorScore.value =
            safeScore

        updateTotal()
    }

    /**
     * Marks form-behavior information as unavailable.
     *
     * This should later replace places where zero currently means
     * that no active field information exists.
     */
    fun clearFormBehaviorScore() {
        currentFormBehaviorScore = null
        _formBehaviorScore.value = 0

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
    /**
     * Recalculates the current live form-filling score.
     *
     * Recording mode does not calculate its final result here.
     * Recording results are calculated only after hand, voice
     * and face processing have all completed.
     */
    private fun updateTotal() {

        when (_mode.value) {

            DistressMode.VOICE_RECORDING -> {
                /*
                 * Do not calculate an incomplete recording score.
                 *
                 * The final recording score is published only by
                 * tryPublishVoiceRecordingResult().
                 */
                printStatus()
                return
            }

            DistressMode.FORM_FILLING -> {

                /*
                 * Use the face score only if it is still recent.
                 */
                val freshFaceScore =
                    getFreshFormFaceScore()

                /*
                 * Form-filling weights:
                 *
                 * Field behavior = 30%
                 * Face analysis  = 35%
                 * Hand movement  = 35%
                 */
                val weightedScore =
                    calculateNormalizedWeightedScore(
                        components = listOf(
                            WeightedComponent(
                                score =
                                    currentFormBehaviorScore,
                                weight =
                                    FORM_BEHAVIOR_WEIGHT
                            ),

                            WeightedComponent(
                                score =
                                    freshFaceScore,
                                weight =
                                    FORM_FACE_WEIGHT
                            ),

                            WeightedComponent(
                                score =
                                    currentFormHandScore,
                                weight =
                                    FORM_HAND_WEIGHT
                            )
                        )
                    )

                /*
                 * If no modality is available, the public total
                 * returns to zero.
                 */
                _totalScore.value =
                    weightedScore
                        ?.roundToInt()
                        ?.coerceIn(0, 4)
                        ?: 0
            }
        }

        printStatus()
    }

    /**
     * Returns the most recent face score only while it is fresh.
     *
     * A stale score is cleared so it cannot affect later
     * form-filling windows after the face disappears.
     */
    private fun getFreshFormFaceScore():
            Double? {

        val score =
            currentFormFaceScore
                ?: return null

        val timestampMs =
            currentFormFaceTimestampMs
                ?: return null

        val ageMs =
            SystemClock.uptimeMillis() -
                    timestampMs

        if (
            ageMs < 0L ||
            ageMs > FACE_SCORE_FRESHNESS_MS
        ) {
            currentFormFaceScore = null
            currentFormFaceTimestampMs = null
            _faceScore.value = 0

            Log.d(
                "DISTRESS_FACE_SCORE",
                "Face score became unavailable because it was stale. ageMs=$ageMs"
            )

            return null
        }

        return score
    }

    /**
     * Calculates a weighted score using only available modalities.
     *
     * Example:
     *
     * Field = 2.0 with weight 0.30
     * Face  = null
     * Hand  = 3.0 with weight 0.35
     *
     * availableWeight = 0.65
     *
     * result =
     * (2.0 × 0.30 + 3.0 × 0.35) / 0.65
     */
    private fun calculateNormalizedWeightedScore(
        components: List<WeightedComponent>
    ): Double? {

        val availableComponents =
            components.filter { component ->
                component.score != null &&
                        component.score.isFinite() &&
                        component.weight > 0.0
            }

        if (availableComponents.isEmpty()) {
            return null
        }

        val availableWeight =
            availableComponents.sumOf { component ->
                component.weight
            }

        if (availableWeight <= 0.0) {
            return null
        }

        val weightedSum =
            availableComponents.sumOf { component ->
                component.score!! *
                        component.weight
            }

        return (
                weightedSum /
                        availableWeight
                ).coerceIn(
                minimumValue = 0.0,
                maximumValue = 4.0
            )
    }

    /**
     * One optional score and its configured weight.
     */
    private data class WeightedComponent(
        val score: Double?,
        val weight: Double
    )

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

        /*
 * Recalculate with the most recent available modality values.
 */
        updateTotal()

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

        Displayed hand score = ${_handScore.value}
        Form hand available = ${currentFormHandScore != null}

        Displayed voice score = ${_voiceScore.value}

        Displayed face score = ${_faceScore.value}
        Form face stored = ${'$'}{currentFormFaceScore != null}

        Displayed form score = ${_formBehaviorScore.value}
        Form behavior available = ${currentFormBehaviorScore != null}

        Recording hand completed = $recordingHandCompleted
        Recording hand average = $recordingHandAverage

        Recording voice completed = $recordingVoiceCompleted
        Recording voice score = $recordingVoiceScore

        Recording face completed = $recordingFaceCompleted
        Recording face average = $recordingFaceAverage

        Total score = ${_totalScore.value}
        Distress = ${isDistressDetected()}
        """.trimIndent()
        )
    }

    /*
 * ============================================================
 * MULTIMODAL WEIGHTS AND AVAILABILITY
 * ============================================================
 */

    /*
     * Form-filling weights.
     */
    private const val FORM_BEHAVIOR_WEIGHT =
        0.30

    private const val FORM_FACE_WEIGHT =
        0.35

    private const val FORM_HAND_WEIGHT =
        0.35

    /*
     * Recording weights.
     */
    private const val RECORDING_VOICE_WEIGHT =
        0.35

    private const val RECORDING_FACE_WEIGHT =
        0.35

    private const val RECORDING_HAND_WEIGHT =
        0.30

    /*
     * A reliable face result is normally produced approximately
     * every 500 ms.
     *
     * Three seconds allows short detection interruptions without
     * keeping an old score active for too long.
     */
    private const val FACE_SCORE_FRESHNESS_MS =
        3_000L
}