package com.example.easyfill_project.face_analysis

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Owns CameraX, MediaPipe and the facial-analysis pipeline.
 */
class FaceMonitoringSession(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onAnalysisStateChanged: (FaceAnalysisState) -> Unit = {},
    onDistressResult: (FaceDistressResult) -> Unit = {},
    onScoreReady: (Int) -> Unit = {},
    private val onDetectionStatusChanged:
        (FaceDetectionStatus) -> Unit = {},
    private val onFrameDataForDebug:
        (FaceFrameData) -> Unit = {}
) {

    private val analysisController =
        FaceAnalysisController(
            onStateChanged = onAnalysisStateChanged,
            onResult = onDistressResult,
            onScoreReady = onScoreReady
        )

    private val faceLandmarkerHelper =
        FaceLandmarkerHelper(
            context = context,
            listener =
                object :
                    FaceLandmarkerHelper.FaceLandmarkerListener {

                    override fun onFrameData(
                        frameData: FaceFrameData
                    ) {
                        Log.d(
                            "FACE_PIPELINE",
                            "Frame received: ${frameData.timestampMs}"
                        )

                        onFrameDataForDebug(frameData)

                        analysisController.onFrameData(
                            frameData
                        )
                    }


                    override fun onResult(
                        result: FaceLandmarkerResult,
                        imageWidth: Int,
                        imageHeight: Int
                    ) {
                        val landmarkCount =
                            result.faceLandmarks()
                                .firstOrNull()
                                ?.size
                                ?: 0

                        onDetectionStatusChanged(
                            FaceDetectionStatus(
                                faceDetected = true,
                                landmarkCount = landmarkCount,
                                message = "Face detection is working"
                            )
                        )
                    }

                    override fun onNoFaceDetected(
                        timestampMs: Long
                    ) {
                        analysisController.onFaceMissing(
                            timestampMs
                        )

                        onDetectionStatusChanged(
                            FaceDetectionStatus(
                                faceDetected = false,
                                landmarkCount = 0,
                                message = "No face detected"
                            )
                        )
                    }

                    override fun onError(
                        message: String,
                        exception: Throwable?
                    ) {
                        onDetectionStatusChanged(
                            FaceDetectionStatus(
                                faceDetected = false,
                                landmarkCount = 0,
                                message = message
                            )
                        )
                    }
                }
        )

    private val cameraManager =
        FaceCameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            faceLandmarkerHelper = faceLandmarkerHelper
        )

    private var started = false

    fun start(): Boolean {

        if (started) {
            return true
        }

        if (!faceLandmarkerHelper.isReady()) {

            onDetectionStatusChanged(
                FaceDetectionStatus(
                    faceDetected = false,
                    landmarkCount = 0,
                    message = "MediaPipe could not load"
                )
            )

            return false
        }

        started = true

        analysisController.start()
        cameraManager.startCamera()

        return true
    }

    fun close() {

        if (!started) {
            return
        }

        started = false

        analysisController.stop()
        cameraManager.close()
    }
}