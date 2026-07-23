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
 * Manages the phone's front camera for facial analysis.
 *
 * Main steps performed by this class:
 *
 * 1. Opens the front camera using CameraX.
 * 2. Connects the camera to the lifecycle of the current screen.
 * 3. Receives camera frames through ImageAnalysis.
 * 4. Limits how many frames are processed every second.
 * 5. Sends selected frames to FaceLandmarkerHelper.
 * 6. Skips and closes unnecessary frames.
 * 7. Stops and releases camera resources when analysis ends.
 *
 * This class does not calculate distress and does not create a baseline.
 *
 * Its responsibility is only:
 *
 * Front camera
 *      ↓
 * Camera frame
 *      ↓
 * FaceCameraManager
 *      ↓
 * FaceLandmarkerHelper / MediaPipe
 */
class FaceCameraManager(

    /*
     * Android context used to access CameraX.
     *
     * The application context is stored later to avoid
     * accidentally keeping a reference to an Activity.
     */
    context: Context,

    /*
     * The lifecycle owner of the screen using the camera.
     *
     * Usually this is an Activity, Fragment, or Compose
     * LifecycleOwner.
     *
     * CameraX automatically stops the camera when this
     * lifecycle is stopped or destroyed.
     */
    private val lifecycleOwner: LifecycleOwner,

    /*
     * Helper responsible for sending the camera image
     * to MediaPipe and detecting facial landmarks.
     */
    private val faceLandmarkerHelper: FaceLandmarkerHelper,

    /*
     * Maximum number of frames sent to MediaPipe per second.
     *
     * The default is 15 FPS, but another value can be supplied
     * when FaceCameraManager is created.
     */
    private val targetFps: Int = DEFAULT_TARGET_FPS
) {

    /*
     * Store the application-level context rather than the
     * Activity context.
     *
     * This reduces the risk of memory leaks.
     */
    private val applicationContext =
        context.applicationContext

    /*
     * Camera and image processing run on a background thread.
     *
     * This prevents facial processing from blocking:
     *
     * - buttons
     * - scrolling
     * - animations
     * - the rest of the user interface
     *
     * A single-thread executor also keeps frames processed
     * in a predictable order.
     */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    /*
     * Reference to the CameraX provider currently managing
     * the camera.
     *
     * It is null before the camera starts and after it stops.
     */
    private var cameraProvider: ProcessCameraProvider? = null

    /*
     * Reference to the ImageAnalysis use case.
     *
     * ImageAnalysis receives frames from CameraX so they can
     * be processed by MediaPipe.
     */
    private var imageAnalysis: ImageAnalysis? = null

    /*
     * This block runs immediately when FaceCameraManager
     * is created.
     */
    init {

        /*
         * Ensure that the selected FPS value is valid.
         *
         * This also prevents division by zero when calculating
         * the time interval between frames.
         */
        require(targetFps in MINIMUM_FPS..MAXIMUM_FPS) {
            "targetFps must be between $MINIMUM_FPS and $MAXIMUM_FPS"
        }
    }

    /*
     * Minimum time that must pass before another frame
     * can be sent to MediaPipe.
     *
     * Formula:
     *
     * milliseconds in one second / desired FPS
     *
     * Examples:
     *
     * 30 FPS:
     * 1000 / 30 = approximately 33 ms
     *
     * 15 FPS:
     * 1000 / 15 = approximately 66 ms
     *
     * 10 FPS:
     * 1000 / 10 = 100 ms
     */
    private val frameIntervalMs: Long =
        MILLISECONDS_PER_SECOND / targetFps

    /*
     * Stores the elapsed-realtime timestamp of the most
     * recently processed frame.
     *
     * It is used to determine whether enough time has passed
     * before sending another frame to MediaPipe.
     */
    private var lastProcessedFrameTimeMs: Long = 0L

    /**
     * Opens the front camera and starts receiving frames.
     *
     * This method:
     *
     * 1. Requests a CameraX ProcessCameraProvider.
     * 2. Waits until the provider is ready.
     * 3. Stores the provider.
     * 4. Binds the camera-analysis use case.
     */
    fun startCamera() {

        /*
         * CameraX provides its camera controller asynchronously.
         *
         * The returned future completes when CameraX is ready.
         */
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(applicationContext)

        /*
         * Add a listener that runs when the camera provider
         * becomes available.
         */
        cameraProviderFuture.addListener(
            {
                try {

                    /*
                     * Retrieve the ready CameraX provider.
                     */
                    val provider =
                        cameraProviderFuture.get()

                    /*
                     * Keep the provider so it can later be
                     * stopped or unbound.
                     */
                    cameraProvider = provider

                    /*
                     * Connect the front camera and ImageAnalysis
                     * to the screen lifecycle.
                     */
                    bindCameraUseCase(provider)

                } catch (exception: Exception) {

                    /*
                     * Possible failures include:
                     *
                     * - CameraX initialization failure
                     * - camera unavailable
                     * - permission or hardware problems
                     */
                    Log.e(
                        TAG,
                        "Failed to start front camera",
                        exception
                    )
                }
            },

            /*
             * Camera binding must be initiated using
             * the application's main executor.
             */
            ContextCompat.getMainExecutor(applicationContext)
        )
    }

    /**
     * Creates and binds the ImageAnalysis use case.
     *
     * ImageAnalysis continuously receives frames from
     * the front camera.
     */
    private fun bindCameraUseCase(
        provider: ProcessCameraProvider
    ) {

        /*
         * Select the front-facing camera.
         *
         * This is appropriate because the application
         * analyzes the user's own face.
         */
        val cameraSelector =
            CameraSelector.DEFAULT_FRONT_CAMERA

        /*
         * Build the ImageAnalysis use case.
         */
        val analysis =
            ImageAnalysis.Builder()

                /*
                 * If processing is slower than the camera,
                 * CameraX discards older waiting frames and
                 * keeps only the newest one.
                 *
                 * Example:
                 *
                 * MediaPipe is processing frame 1.
                 * Frames 2, 3, 4 and 5 arrive.
                 *
                 * CameraX discards 2, 3 and 4 and keeps frame 5.
                 *
                 * This prevents a growing queue of outdated
                 * camera images.
                 */
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

        /*
         * Define what should happen whenever CameraX
         * provides a new camera frame.
         *
         * This code runs on cameraExecutor, not on the UI thread.
         */
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->

            /*
             * Read a monotonic clock value.
             *
             * elapsedRealtime() is appropriate for measuring
             * elapsed time because it is not affected if the user
             * changes the phone's date or clock.
             */
            val currentTimeMs =
                SystemClock.elapsedRealtime()

            /*
             * Decide whether this frame may be processed.
             *
             * A frame is accepted when:
             *
             * - it is the first frame, or
             * - enough milliseconds passed since the last
             *   processed frame.
             */
            val enoughTimePassed =
                lastProcessedFrameTimeMs == 0L ||
                        currentTimeMs - lastProcessedFrameTimeMs >=
                        frameIntervalMs

            if (enoughTimePassed) {

                /*
                 * Save the processing time so later frames can
                 * be limited according to targetFps.
                 */
                lastProcessedFrameTimeMs =
                    currentTimeMs

                /*
                 * Send this frame to FaceLandmarkerHelper.
                 *
                 * FaceLandmarkerHelper will:
                 *
                 * - rotate or prepare the image
                 * - send it to MediaPipe
                 * - detect facial landmarks and blendshapes
                 * - convert the result into face-analysis data
                 * - close this ImageProxy after processing
                 *
                 * Because the helper receives ownership of the
                 * frame, this class must not close it here.
                 */
                faceLandmarkerHelper.detectLiveStream(
                    imageProxy
                )

            } else {

                /*
                 * This frame arrived too soon and would exceed
                 * the selected FPS limit.
                 *
                 * It is skipped, but it must still be closed.
                 *
                 * Failing to close ImageProxy objects can block
                 * CameraX from providing additional frames.
                 */
                imageProxy.close()
            }
        }

        /*
         * Store the ImageAnalysis instance so its analyzer
         * can later be stopped.
         */
        imageAnalysis = analysis

        try {

            /*
             * Remove previous camera bindings.
             *
             * This prevents duplicate analysis use cases when
             * startCamera() is called more than once.
             */
            provider.unbindAll()

            /*
             * Bind the front camera and ImageAnalysis use case
             * to the provided lifecycle.
             *
             * CameraX will automatically react to lifecycle
             * changes such as screen stop or destruction.
             */
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

            /*
             * This may fail if:
             *
             * - the camera cannot be opened
             * - another use case conflicts with this binding
             * - the device has no usable front camera
             */
            Log.e(
                TAG,
                "Failed to bind camera analysis",
                exception
            )
        }
    }

    /**
     * Stops the front camera without destroying MediaPipe.
     *
     * This is useful when analysis should temporarily stop
     * but may start again later.
     */
    fun stopCamera() {

        /*
         * Stop receiving frames through ImageAnalysis.
         */
        imageAnalysis?.clearAnalyzer()

        imageAnalysis = null

        /*
         * Unbind all CameraX use cases from this provider.
         */
        cameraProvider?.unbindAll()

        cameraProvider = null

        /*
         * Reset the FPS timer.
         *
         * This ensures the first frame is immediately accepted
         * the next time the camera starts.
         */
        lastProcessedFrameTimeMs = 0L

        Log.d(
            TAG,
            "Front camera stopped"
        )
    }

    /**
     * Permanently releases all resources used by this manager.
     *
     * This should usually be called when the screen or the
     * face-analysis component is being destroyed completely.
     *
     * It:
     *
     * 1. Stops CameraX.
     * 2. Stops the background camera thread.
     * 3. Closes MediaPipe resources.
     */
    fun close() {

        /*
         * Stop receiving camera frames.
         */
        stopCamera()

        /*
         * Shut down the background executor only once.
         */
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }

        /*
         * Release MediaPipe and face-landmarker resources.
         */
        faceLandmarkerHelper.close()
    }

    companion object {

        /*
         * Logcat tag used by this class.
         */
        private const val TAG = "FACE_CAMERA"

        /*
         * Default maximum number of frames sent to
         * MediaPipe every second.
         *
         * Fifteen FPS usually provides frequent facial updates
         * without processing every camera frame.
         */
        private const val DEFAULT_TARGET_FPS = 15

        /*
         * Lowest accepted target FPS.
         */
        private const val MINIMUM_FPS = 1

        /*
         * Highest accepted target FPS.
         */
        private const val MAXIMUM_FPS = 60

        /*
         * Number of milliseconds in one second.
         *
         * Used to convert FPS into a time interval.
         */
        private const val MILLISECONDS_PER_SECOND = 1000L
    }
}