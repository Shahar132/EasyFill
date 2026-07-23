package com.example.easyfill_project.face_analysis

import android.util.Log

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

//in simple words when we call faceAnalysisController.start(),
//Does this user already have a facial baseline?
//A facial baseline represents the user’s usual facial behavior, such as their normal eye and eyebrow measurements.
//If a saved baseline exists: Loading baseline → create FaceDistressAnalyzer → begin analyzing live frames
//If no saved baseline exists: Collect camera frames  startCalibration() → create a personal baseline→ save it to Firebase→ begin live analysis
//Open face analysis, Load saved baseline from Firebase ,Receive facial data from each camera frame
//Compare recent facial data with the baseline,Produce distress score

//Frame 1 → analyze → no baseline update
//Frame 2 → analyze → no baseline update
//Frame 3 → analyze → no complete result yet
//Frame 4 → analyze → distress result
//Frame 5 → analyze → no baseline update
//...
//Only occasionally, when the analyzer has enough reliable evidence
// that the user's normal facial behavior should be adjusted, it returns an updated baseline.
//It is not replaced by every new frame.
//It stays mostly stable and is updated only occasionally when the analyzer explicitly decides an update is appropriate.

class FaceAnalysisController(

    // Repository responsible for reading and writing the baseline in Firebase.
    private val repository: FaceBaselineRepository =
        FaceBaselineRepository(),

    // Called whenever the current phase or status of the analysis changes.
    private val onStateChanged:
        (FaceAnalysisState) -> Unit = {},

    // Called whenever a complete facial-distress result is created.
    private val onResult:
        (FaceDistressResult) -> Unit = {},

    // Called with the final facial distress level.
    // The expected score is between 0 and 4.
    private val onScoreReady:
        (Int) -> Unit = {}
) {

    // Creates and stores the personal facial baseline.
    private val baselineManager =
        FaceBaselineManager()

    // Indicates whether the controller is currently running.
    @Volatile
    private var isActive =
        false

    // Stores the current stage of the face-analysis process.
    @Volatile
    private var phase =
        FaceAnalysisPhase.IDLE

    // Performs the actual comparison between current facial behavior
    // and the user's saved baseline.
    private var analyzer:
            FaceDistressAnalyzer? = null

    // Prevents the calibration UI from being updated for every camera frame.
    // The UI is updated only once per second.
    private var lastCalibrationStateUpdateTimestampMs =
        0L

    // Prevents multiple updated baselines from being saved at the same time.
    private var derivedSaveInProgress =
        false

    /**
     * Starts the facial-analysis flow.
     *
     * The controller first checks Firebase for a saved baseline.
     *
     * If a baseline exists:
     * - It is loaded.
     * - Face analysis begins immediately.
     *
     * If no baseline exists:
     * - A new personal calibration begins.
     */
    fun start() {

        // Do not start the controller twice.
        if (isActive) {
            return
        }

        isActive = true

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

                // Ignore Firebase results if the controller was stopped
                // while the request was still running.
                if (!isActive) {
                    return@getBaseline
                }

                if (savedBaseline == null) {

                    // No saved baseline exists.
                    // Start a new calibration for this user.
                    startCalibration(
                        message =
                            "No saved baseline found. Collecting a new baseline"
                    )

                } else {

                    // Store the baseline inside the baseline manager.
                    baselineManager.useSavedBaseline(
                        savedBaseline
                    )

                    // Create the analyzer using the saved personal baseline.
                    analyzer =
                        FaceDistressAnalyzer(
                            savedBaseline
                        )

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

                // The Firebase request failed, but the feature can still work.
                // A new local baseline will be created instead.
                startCalibration(
                    message =
                        "Firebase load failed. Collecting a local baseline"
                )
            }
        )
    }

    /**
     * Receives facial measurements extracted from one camera frame.
     *
     * This method should be called by the camera/image analyzer
     * whenever a face is successfully detected.
     */
    @Synchronized
    fun onFrameData(
        frameData: FaceFrameData
    ) {

        if (!isActive) {
            return
        }

        when (phase) {

            // During calibration, frames are used to create the baseline.
            FaceAnalysisPhase.CALIBRATING ->
                handleCalibrationFrame(
                    frameData
                )

            // During and immediately after baseline saving,
            // frames are used for distress analysis.
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
     * Called when the camera frame does not contain a detected face.
     *
     * The analyzer may clear its recent history after the face
     * has been missing for a certain period.
     */
    @Synchronized
    fun onFaceMissing(
        timestampMs: Long
    ) {

        if (!isActive) {
            return
        }

        val historyReset =
            analyzer
                ?.onFaceMissing(timestampMs)
                ?: false

        // If the previous facial-analysis history was reset,
        // return the facial score to zero.
        if (historyReset) {
            onScoreReady(0)
        }
    }

    /**
     * Stops facial analysis and releases the current analyzer state.
     */
    @Synchronized
    fun stop() {

        isActive = false

        phase =
            FaceAnalysisPhase.IDLE

        analyzer?.reset()
        analyzer = null

        derivedSaveInProgress =
            false

        // A stopped analyzer should not leave an old distress score active.
        onScoreReady(0)

        emitState(
            phase =
                FaceAnalysisPhase.IDLE,
            message =
                "Face analysis stopped"
        )
    }

    /**
     * Starts the personal facial-baseline calibration.
     */
    private fun startCalibration(
        message: String
    ) {

        baselineManager.startCalibration()

        analyzer = null

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
     * Adds a camera frame to the baseline-calibration process.
     */
    private fun handleCalibrationFrame(
        frameData: FaceFrameData
    ) {

        val completed =
            baselineManager.addFrame(
                frameData
            )

        // Update the UI only once every second instead of on every frame.
        if (
            frameData.timestampMs -
            lastCalibrationStateUpdateTimestampMs >=
            CALIBRATION_UI_UPDATE_INTERVAL_MS
        ) {

            lastCalibrationStateUpdateTimestampMs =
                frameData.timestampMs

            emitState(
                phase =
                    FaceAnalysisPhase.CALIBRATING,
                message =
                    "Collecting personal facial baseline",
                collectedFrames =
                    baselineManager
                        .getCollectedFrameCount(),
                validWindows =
                    baselineManager
                        .getValidWindowCount()
            )
        }

        // Continue collecting frames until calibration is complete.
        if (!completed) {
            return
        }

        val baseline =
            baselineManager
                .getCurrentBaseline()

        // Calibration completed but no usable baseline was produced.
        if (baseline == null) {

            phase =
                FaceAnalysisPhase.ERROR

            emitState(
                phase =
                    FaceAnalysisPhase.ERROR,
                message =
                    "Baseline creation failed"
            )

            return
        }

        val frameCount =
            baselineManager
                .getCollectedFrameCount()

        val validWindowCount =
            baselineManager
                .getValidWindowCount()

        // Begin analysis immediately using the new local baseline.
        analyzer =
            FaceDistressAnalyzer(
                baseline
            )

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

        // Save the newly created personal baseline in Firebase.
        repository.saveRawBaseline(
            baseline = baseline,
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

                // Continue using the baseline locally even if saving failed.
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
     * Sends a camera frame to the facial-distress analyzer.
     */
    private fun handleAnalysisFrame(
        frameData: FaceFrameData
    ) {

        val currentAnalyzer =
            analyzer
                ?: return

        // addFrame may return null until enough frame data
        // has been collected for a reliable result.
        val result =
            currentAnalyzer.addFrame(
                frameData
            ) ?: return

        // Send the complete result to the UI or logging layer.
        onResult(result)

        // Send only the final distress level to the global scoring system.
        onScoreReady(
            result.level.coerceIn(
                minimumValue = 0,
                maximumValue = 4
            )
        )

        // Do not save another learned baseline while a save is active.
        if (derivedSaveInProgress) {
            return
        }

        // The analyzer may gradually learn new stable values
        // and request that the baseline be updated.
        val updatedBaseline =
            currentAnalyzer
                .consumePendingBaselineUpdate()
                ?: return

        derivedSaveInProgress =
            true

        repository.saveDerivedBaseline(

            baseline =
                updatedBaseline,

            onSuccess = {

                derivedSaveInProgress =
                    false

                if (isActive) {

                    emitState(
                        phase =
                            phase,
                        message =
                            "Facial analysis is running",
                        baseline =
                            updatedBaseline,
                        collectedFrames =
                            baselineManager
                                .getCollectedFrameCount(),
                        validWindows =
                            baselineManager
                                .getValidWindowCount()
                    )
                }
            },

            onFailure = { exception ->

                derivedSaveInProgress =
                    false

                Log.e(
                    TAG,
                    "Failed to save learned derived baseline",
                    exception
                )
            }
        )
    }

    /**
     * Creates a FaceAnalysisState object and sends it
     * to the screen or component observing the controller.
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
                phase = phase,
                message = message,
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

        // Calibration-state updates are sent to the UI once per second.
        private const val CALIBRATION_UI_UPDATE_INTERVAL_MS =
            1_000L
    }
}