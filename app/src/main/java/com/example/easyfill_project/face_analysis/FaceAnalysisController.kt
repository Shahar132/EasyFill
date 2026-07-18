package com.example.easyfill_project.face_analysis

import android.util.Log

/**
 * Coordinates baseline loading, calibration,
 * runtime analysis and Firebase updates.
 */
class FaceAnalysisController(
    private val repository: FaceBaselineRepository =
        FaceBaselineRepository(),
    private val onStateChanged:
        (FaceAnalysisState) -> Unit = {},
    private val onResult:
        (FaceDistressResult) -> Unit = {},
    private val onScoreReady:
        (Int) -> Unit = {}
) {

    private val baselineManager =
        FaceBaselineManager()

    @Volatile
    private var isActive =
        false

    @Volatile
    private var phase =
        FaceAnalysisPhase.IDLE

    private var analyzer:
            FaceDistressAnalyzer? = null

    private var lastCalibrationStateUpdateTimestampMs =
        0L

    private var derivedSaveInProgress =
        false

    fun start() {

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

                if (!isActive) {
                    return@getBaseline
                }

                if (savedBaseline == null) {

                    startCalibration(
                        message =
                            "No saved baseline found. Collecting a new baseline"
                    )

                } else {

                    baselineManager.useSavedBaseline(
                        savedBaseline
                    )

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

                startCalibration(
                    message =
                        "Firebase load failed. Collecting a local baseline"
                )
            }
        )
    }

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

        if (historyReset) {
            onScoreReady(0)
        }
    }

    @Synchronized
    fun stop() {

        isActive = false

        phase =
            FaceAnalysisPhase.IDLE

        analyzer?.reset()
        analyzer = null

        derivedSaveInProgress =
            false

        onScoreReady(0)

        emitState(
            phase =
                FaceAnalysisPhase.IDLE,
            message =
                "Face analysis stopped"
        )
    }

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

    private fun handleCalibrationFrame(
        frameData: FaceFrameData
    ) {

        val completed =
            baselineManager.addFrame(
                frameData
            )

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

        if (!completed) {
            return
        }

        val baseline =
            baselineManager
                .getCurrentBaseline()

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

    private fun handleAnalysisFrame(
        frameData: FaceFrameData
    ) {

        val currentAnalyzer =
            analyzer
                ?: return

        val result =
            currentAnalyzer.addFrame(
                frameData
            ) ?: return

//        Log.d(
//            "FACE_RESULT",
//            "score=${result.score} | " +
//                    "level=${result.level} | " +
//                    "reliable=${result.isReliable} | " +
//                    "eyes=${result.eyesScore} | " +
//                    "brows=${result.browsScore} | " +
//                    "top=${result.topContributor}"
//        )

        onResult(result)

        onScoreReady(
            result.level.coerceIn(
                minimumValue = 0,
                maximumValue = 4
            )
        )

        if (derivedSaveInProgress) {
            return
        }

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

        private const val CALIBRATION_UI_UPDATE_INTERVAL_MS =
            1_000L
    }
}