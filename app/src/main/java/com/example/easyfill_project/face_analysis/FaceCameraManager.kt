package com.example.easyfill_project.face_analysis

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages the front camera and sends camera frames
 * to MediaPipe at the selected maximum frame rate.
 */
class FaceCameraManager(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val faceLandmarkerHelper: FaceLandmarkerHelper,

    /*
     * Maximum number of frames sent to MediaPipe per second.
     * This value can be changed when creating the manager.
     */
    private val targetFps: Int = DEFAULT_TARGET_FPS
) {

    private val applicationContext =
        context.applicationContext

    /*
     * Camera frame processing runs on a background thread
     * to avoid blocking the user interface.
     */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null

    private var imageAnalysis: ImageAnalysis? = null

    init {
        /*
         * The FPS value must be validated before calculating
         * the interval between processed frames.
         */
        require(targetFps in MINIMUM_FPS..MAXIMUM_FPS) {
            "targetFps must be between $MINIMUM_FPS and $MAXIMUM_FPS"
        }
    }

    /*
     * Minimum time that must pass between two processed frames.
     *
     * Examples:
     * 30 FPS = approximately 33 milliseconds
     * 15 FPS = approximately 66 milliseconds
     * 10 FPS = 100 milliseconds
     */
    private val frameIntervalMs: Long =
        MILLISECONDS_PER_SECOND / targetFps

    private var lastProcessedFrameTimeMs: Long = 0L

    /**
     * Opens the front camera and starts frame analysis.
     */
    fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(applicationContext)

        cameraProviderFuture.addListener(
            {
                try {
                    val provider =
                        cameraProviderFuture.get()

                    cameraProvider = provider

                    bindCameraUseCase(provider)

                } catch (exception: Exception) {
                    Log.e(
                        TAG,
                        "Failed to start front camera",
                        exception
                    )
                }
            },
            ContextCompat.getMainExecutor(applicationContext)
        )
    }

    /**
     * Binds the ImageAnalysis use case
     * to the screen lifecycle.
     */
    private fun bindCameraUseCase(
        provider: ProcessCameraProvider
    ) {
        val cameraSelector =
            CameraSelector.DEFAULT_FRONT_CAMERA

        val analysis =
            ImageAnalysis.Builder()
                /*
                 * If MediaPipe is still processing a frame,
                 * only the most recent frame is kept.
                 */
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->

            val currentTimeMs =
                SystemClock.elapsedRealtime()

            val enoughTimePassed =
                lastProcessedFrameTimeMs == 0L ||
                        currentTimeMs - lastProcessedFrameTimeMs >=
                        frameIntervalMs

            if (enoughTimePassed) {

                lastProcessedFrameTimeMs =
                    currentTimeMs

                /*
                 * The helper is responsible for processing
                 * and closing this ImageProxy.
                 */
                faceLandmarkerHelper.detectLiveStream(
                    imageProxy
                )

            } else {

                /*
                 * Frames that exceed the selected FPS limit
                 * are skipped and must still be closed.
                 */
                imageProxy.close()
            }
        }

        imageAnalysis = analysis

        try {
            /*
             * Prevents duplicate bindings when startCamera()
             * is called more than once.
             */
            provider.unbindAll()

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                analysis
            )

            Log.d(
                TAG,
                "Front camera started | target FPS: $targetFps"
            )

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Failed to bind camera analysis",
                exception
            )
        }
    }

    /**
     * Stops the camera without closing MediaPipe.
     */
    fun stopCamera() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null

        cameraProvider?.unbindAll()
        cameraProvider = null

        lastProcessedFrameTimeMs = 0L

        Log.d(
            TAG,
            "Front camera stopped"
        )
    }

    /**
     * Releases the camera, background executor,
     * and MediaPipe resources.
     */
    fun close() {
        stopCamera()

        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }

        faceLandmarkerHelper.close()
    }

    companion object {

        private const val TAG = "FACE_CAMERA"

        // Default maximum number of camera frames
        // sent to MediaPipe every second.
        private const val DEFAULT_TARGET_FPS = 30

        // Lowest FPS value allowed when creating FaceCameraManager.
        private const val MINIMUM_FPS = 1

        // Highest FPS value allowed when creating FaceCameraManager.
        private const val MAXIMUM_FPS = 60

        // Number of milliseconds in one second.
        // Used to calculate the interval between processed frames.
        private const val MILLISECONDS_PER_SECOND = 1000L
    }
}