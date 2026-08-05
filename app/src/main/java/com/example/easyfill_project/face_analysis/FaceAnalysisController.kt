package com.example.easyfill_project.face_analysis

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Main controller of the facial-analysis flow.
 *
 * Responsibilities:
 * 1. Loads an existing facial baseline from Firebase.
 * 2. Starts a new calibration when no baseline exists.
 * 3. Receives facial data extracted from camera frames.
 * 4. Sends the frame data to the facial distress analyzer.
 * 5. Returns the calculated facial distress result and score.
 * 6. Saves newly created or learned baseline values to Firebase.
 */
class FaceAnalysisController(

    context: Context,

    // Repository responsible for reading and writing the baseline in Firebase.
    private val repository: FaceBaselineRepository =
        FaceBaselineRepository(),

    // Called whenever the current phase or status changes.
    private val onStateChanged:
        (FaceAnalysisState) -> Unit = {},

    // Called whenever a complete facial-distress result is created.
    private val onResult:
        (FaceDistressResult) -> Unit = {},

    // Called with the facial distress score between 0 and 4.
    private val onScoreReady:
        (Int) -> Unit = {}
) {

    // Creates and stores the personal facial baseline.
    private val baselineManager =
        FaceBaselineManager()

    /*
     * Saves facial-analysis results only while
     * a controlled evaluation session is active.
     */
    private val evaluationLogger =
        FaceEvaluationLogger(
            context.applicationContext
        )

    // Indicates whether the controller is currently running.
    @Volatile
    private var isActive =
        false

    // Stores the current stage of facial analysis.
    @Volatile
    private var phase =
        FaceAnalysisPhase.IDLE

    // Compares current facial behavior with the personal baseline.
    private var analyzer:
            FaceDistressAnalyzer? = null

    // Prevents multiple derived-baseline saves at the same time.
    private var derivedSaveInProgress =
        false

    // Timestamp of the first valid frame in the warm-up period.
    private var analysisWarmupStartTimestampMs: Long? =
        null

    // Number of valid facial frames collected during warm-up.
    private var analysisWarmupValidFrameCount =
        0

    // Indicates whether the camera warm-up has completed.
    private var analysisWarmupCompleted =
        false

    /**
     * Starts one controlled facial evaluation session.
     */
    fun startFaceEvaluationSession(
        participantId: String,
        scenario: String,
        expectedContributor: FaceDistressContributor,
        expectedLevel: Int
    ): File {

        return evaluationLogger.startSession(
            participantId =
                participantId,

            scenario =
                scenario,

            expectedContributor =
                expectedContributor,

            expectedLevel =
                expectedLevel
        )
    }

    /**
     * Stops the currently active facial evaluation session.
     */
    fun stopFaceEvaluationSession() {

        evaluationLogger.stopSession()
    }

    /**
     * Indicates whether facial evaluation is currently active.
     */
    val isFaceEvaluationSessionActive: Boolean
        get() =
            evaluationLogger.isSessionActive

    /**
     * Starts the facial-analysis flow.
     */
    fun start() {

        // Do not start the controller twice.
        if (isActive) {
            return
        }

        isActive = true

        analyzer = null
        derivedSaveInProgress = false

        resetAnalysisWarmup()

        phase =
            FaceAnalysisPhase.LOADING_BASELINE

        emitState(
            phase =
                FaceAnalysisPhase.LOADING_BASELINE,
            message =
                "Checking Firebase for a saved facial baseline"
        )

        repository.getBaseline(

            onSuccess = { savedBaseline ->

                // Ignore the result if the controller stopped while loading.
                if (!isActive) {
                    return@getBaseline
                }

                if (savedBaseline == null) {

                    startCalibration(
                        message =
                            "No saved baseline found. Collecting a new baseline"
                    )

                } else {

                    // Store the saved baseline in memory.
                    baselineManager.useSavedBaseline(
                        savedBaseline
                    )

                    // Create the analyzer using the saved personal baseline.
                    analyzer =
                        FaceDistressAnalyzer(
                            savedBaseline
                        )

                    /*
                     * Restart the warm-up because the camera may still
                     * be stabilizing when the baseline finishes loading.
                     */
                    resetAnalysisWarmup()

                    phase =
                        FaceAnalysisPhase.ANALYZING

                    emitState(
                        phase =
                            FaceAnalysisPhase.ANALYZING,
                        message =
                            "Saved facial baseline loaded",
                        baseline =
                            savedBaseline
                    )

                    Log.d(
                        TAG,
                        "Saved baseline loaded | waiting for camera warm-up"
                    )
                }
            },

            onFailure = { exception ->

                if (!isActive) {
                    return@getBaseline
                }

                Log.e(
                    TAG,
                    "Baseline load failed. Starting local calibration",
                    exception
                )

                startCalibration(
                    message =
                        "Firebase load failed. Collecting a local baseline"
                )
            }
        )
    }

    /**
     * Receives facial measurements from one detected camera frame.
     */
    @Synchronized
    fun onFrameData(
        frameData: FaceFrameData
    ) {

        if (!isActive) {
            return
        }

        when (phase) {

            FaceAnalysisPhase.CALIBRATING ->
                handleCalibrationFrame(
                    frameData
                )

            FaceAnalysisPhase.SAVING_BASELINE,
            FaceAnalysisPhase.ANALYZING ->
                handleAnalysisFrame(
                    frameData
                )

            else ->
                Unit
        }
    }

    /**
     * Called when no face is detected in the current camera frame.
     */
    @Synchronized
    fun onFaceMissing(
        timestampMs: Long
    ) {

        if (!isActive) {
            return
        }

        /*
         * If the face disappears before warm-up is completed,
         * restart the warm-up from the next valid facial frame.
         */
        if (!analysisWarmupCompleted) {

            if (
                analysisWarmupStartTimestampMs != null ||
                analysisWarmupValidFrameCount > 0
            ) {

                analysisWarmupStartTimestampMs = null
                analysisWarmupValidFrameCount = 0

                Log.d(
                    TAG,
                    "Camera warm-up restarted because the face was missing"
                )
            }

            onScoreReady(0)

            return
        }

        val historyReset =
            analyzer
                ?.onFaceMissing(timestampMs)
                ?: false

        /*
         * Clear the facial score when the analyzer clears
         * its recent facial history.
         */
        if (historyReset) {

            Log.d(
                TAG,
                "Facial history reset after missing face"
            )

            onScoreReady(0)
        }
    }

    /**
     * Stops facial analysis and clears its active state.
     */
    @Synchronized
    fun stop() {

        isActive = false

        evaluationLogger.stopSession()

        phase =
            FaceAnalysisPhase.IDLE

        analyzer?.reset()
        analyzer = null

        derivedSaveInProgress = false

        analysisWarmupStartTimestampMs = null
        analysisWarmupValidFrameCount = 0
        analysisWarmupCompleted = false

        // Do not leave an old facial distress score active.
        onScoreReady(0)

        emitState(
            phase =
                FaceAnalysisPhase.IDLE,
            message =
                "Face analysis stopped"
        )

        Log.d(
            TAG,
            "Face analysis stopped and facial score cleared"
        )
    }

    /**
     * Starts a new personal facial-baseline calibration.
     */
    private fun startCalibration(
        message: String
    ) {

        baselineManager.startCalibration()

        analyzer = null
        derivedSaveInProgress = false

        analysisWarmupStartTimestampMs = null
        analysisWarmupValidFrameCount = 0
        analysisWarmupCompleted = false

        onScoreReady(0)

        phase =
            FaceAnalysisPhase.CALIBRATING

        emitState(
            phase =
                FaceAnalysisPhase.CALIBRATING,
            message =
                message
        )
    }

    /**
     * Adds one facial frame to the baseline-calibration process.
     */
    private fun handleCalibrationFrame(
        frameData: FaceFrameData
    ) {

        val completed =
            baselineManager.addFrame(
                frameData
            )

        /*
         * No periodic UI update is sent here.
         *
         * The calibration continues internally until a valid
         * personal baseline is completed.
         */
        if (!completed) {
            return
        }

        val baseline =
            baselineManager.getCurrentBaseline()

        if (baseline == null) {

            phase =
                FaceAnalysisPhase.ERROR

            onScoreReady(0)

            emitState(
                phase =
                    FaceAnalysisPhase.ERROR,
                message =
                    "Baseline creation failed"
            )

            return
        }

        val frameCount =
            baselineManager.getCollectedFrameCount()

        val validWindowCount =
            baselineManager.getValidWindowCount()

        analyzer =
            FaceDistressAnalyzer(
                baseline
            )

        /*
         * A new baseline is created only after the camera has already
         * collected facial frames for at least ten seconds.
         *
         * Therefore, another warm-up is unnecessary in this case.
         */
        analysisWarmupStartTimestampMs = null
        analysisWarmupValidFrameCount = 0
        analysisWarmupCompleted = true

        onScoreReady(0)

        phase =
            FaceAnalysisPhase.SAVING_BASELINE

        emitState(
            phase =
                FaceAnalysisPhase.SAVING_BASELINE,
            message =
                "Baseline completed. Saving to Firebase",
            baseline =
                baseline,
            collectedFrames =
                frameCount,
            validWindows =
                validWindowCount
        )

        repository.saveRawBaseline(
            baseline =
                baseline,
            collectedFrameCount =
                frameCount,
            validWindowCount =
                validWindowCount,

            onSuccess = {

                if (!isActive) {
                    return@saveRawBaseline
                }

                phase =
                    FaceAnalysisPhase.ANALYZING

                emitState(
                    phase =
                        FaceAnalysisPhase.ANALYZING,
                    message =
                        "Facial baseline saved and ready",
                    baseline =
                        baseline,
                    collectedFrames =
                        frameCount,
                    validWindows =
                        validWindowCount
                )
            },

            onFailure = { exception ->

                if (!isActive) {
                    return@saveRawBaseline
                }

                Log.e(
                    TAG,
                    "Baseline created but Firebase save failed",
                    exception
                )

                /*
                 * Continue using the new baseline locally,
                 * even if saving it in Firebase failed.
                 */
                phase =
                    FaceAnalysisPhase.ANALYZING

                emitState(
                    phase =
                        FaceAnalysisPhase.ANALYZING,
                    message =
                        "Baseline ready locally. Firebase save failed",
                    baseline =
                        baseline,
                    collectedFrames =
                        frameCount,
                    validWindows =
                        validWindowCount
                )
            }
        )
    }

    /**
     * Sends one facial frame to the distress analyzer.
     */
    private fun handleAnalysisFrame(
        frameData: FaceFrameData
    ) {

        val currentAnalyzer =
            analyzer
                ?: return

        /*
         * When a saved baseline is loaded, collect stable facial data
         * before beginning distress analysis.
         *
         * Both conditions must be met:
         * 1. At least two seconds have passed.
         * 2. At least twenty valid facial frames were collected.
         */
        if (!analysisWarmupCompleted) {

            if (!isValidWarmupFrame(frameData)) {

                Log.v(
                    TAG,
                    "Ignoring invalid facial frame during camera warm-up"
                )

                return
            }

            val warmupStart =
                analysisWarmupStartTimestampMs
                    ?: frameData.timestampMs.also { timestamp ->

                        analysisWarmupStartTimestampMs =
                            timestamp

                        Log.d(
                            TAG,
                            "Camera warm-up started | " +
                                    "minimumDuration=${ANALYSIS_WARMUP_DURATION_MS}ms | " +
                                    "minimumValidFrames=$MINIMUM_WARMUP_VALID_FRAMES"
                        )
                    }

            analysisWarmupValidFrameCount += 1

            val warmupElapsed =
                frameData.timestampMs -
                        warmupStart

            val enoughTimePassed =
                warmupElapsed >=
                        ANALYSIS_WARMUP_DURATION_MS

            val enoughValidFrames =
                analysisWarmupValidFrameCount >=
                        MINIMUM_WARMUP_VALID_FRAMES

            if (
                !enoughTimePassed ||
                !enoughValidFrames
            ) {
                return
            }

            analysisWarmupCompleted = true

            Log.d(
                TAG,
                "Camera warm-up completed | " +
                        "elapsed=${warmupElapsed}ms | " +
                        "validFrames=$analysisWarmupValidFrameCount"
            )
        }

        /*
         * addFrame may return null until enough reliable frames
         * have been collected for a complete analysis result.
         */
        val result =
            currentAnalyzer.addFrame(
                frameData
            ) ?: return

        Log.d(
            FACE_RESULT_TAG,
            "Facial result | " +
                    "level=${result.level} | " +
                    "result=$result"
        )

        /*
         * The logger ignores this call when no controlled
         * face-evaluation session is active.
         */
        evaluationLogger.appendResult(
            result =
                result,

            baseline =
                currentAnalyzer
                    .getBaselineUsedForLastResult()
        )

        // Send the complete result to the observing component.
        onResult(result)

        // Send the facial level to the global distress system.
        onScoreReady(
            result.level.coerceIn(
                minimumValue = 0,
                maximumValue = 4
            )
        )

        // Do not start another derived-baseline save while one is active.
        if (derivedSaveInProgress) {
            return
        }

        val updatedBaseline =
            currentAnalyzer
                .consumePendingBaselineUpdate()
                ?: return

        derivedSaveInProgress = true

        repository.saveDerivedBaseline(
            baseline =
                updatedBaseline,

            onSuccess = {

                derivedSaveInProgress = false

                if (isActive) {

                    emitState(
                        phase =
                            phase,
                        message =
                            "Facial analysis is running",
                        baseline =
                            updatedBaseline,
                        collectedFrames =
                            baselineManager.getCollectedFrameCount(),
                        validWindows =
                            baselineManager.getValidWindowCount()
                    )
                }
            },

            onFailure = { exception ->

                derivedSaveInProgress = false

                Log.e(
                    TAG,
                    "Failed to save learned derived baseline",
                    exception
                )
            }
        )
    }

    /**
     * Checks whether a facial frame is reliable enough
     * to participate in the camera warm-up period.
     */
    private fun isValidWarmupFrame(
        frameData: FaceFrameData
    ): Boolean {

        val geometry =
            frameData.browGeometry
                ?: return false

        return FaceStats.isValidFrame(frameData) &&
                geometry.isReliable &&
                geometry.interEyeDistance.isFinite() &&
                geometry.interEyeDistance > 0f
    }

    /**
     * Starts a new camera stabilization period.
     */
    private fun resetAnalysisWarmup() {

        analysisWarmupStartTimestampMs = null
        analysisWarmupValidFrameCount = 0
        analysisWarmupCompleted = false

        // A warm-up period must never leave an old score active.
        onScoreReady(0)
    }

    /**
     * Creates and emits the current facial-analysis state.
     */
    private fun emitState(
        phase: FaceAnalysisPhase,
        message: String,
        baseline: FaceBaseline? =
            baselineManager.getCurrentBaseline(),
        collectedFrames: Int =
            baselineManager.getCollectedFrameCount(),
        validWindows: Int =
            baselineManager.getValidWindowCount()
    ) {

        onStateChanged(
            FaceAnalysisState(
                phase =
                    phase,
                message =
                    message,
                baselineReady =
                    baseline != null,
                rawMetricCount =
                    baseline
                        ?.rawMetrics
                        ?.size
                        ?: 0,
                derivedMetricCount =
                    baseline
                        ?.derivedMetrics
                        ?.size
                        ?: 0,
                collectedFrameCount =
                    collectedFrames,
                validWindowCount =
                    validWindows
            )
        )
    }

    companion object {

        private const val TAG =
            "FACE_ANALYSIS"

        private const val FACE_RESULT_TAG = "FACE_RESULT_DEBUG"

        // Minimum camera stabilization time.
        private const val ANALYSIS_WARMUP_DURATION_MS = 2_000L

        // Minimum number of valid facial frames required during warm-up.
        private const val MINIMUM_WARMUP_VALID_FRAMES = 20
    }
}