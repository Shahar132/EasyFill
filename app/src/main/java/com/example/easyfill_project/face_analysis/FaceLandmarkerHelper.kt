package com.example.easyfill_project.face_analysis

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.components.containers.Category

class FaceLandmarkerHelper(
    context: Context,
    private val listener: FaceLandmarkerListener
) {

    // Uses the application context to avoid holding a screen or activity in memory.
    private val applicationContext = context.applicationContext

    private var faceLandmarker: FaceLandmarker? = null



    init {
        setupFaceLandmarker()
    }

    /**
     * Loads the model and configures MediaPipe
     * to process live camera frames.
     */
    private fun setupFaceLandmarker() {
        if (faceLandmarker != null) {
            return
        }

        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_NAME)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)

                // Only one user is expected in front of the front camera.
                .setNumFaces(NUM_FACES)

                // Initial MediaPipe confidence thresholds.
                .setMinFaceDetectionConfidence(
                    MIN_FACE_DETECTION_CONFIDENCE
                )
                .setMinFacePresenceConfidence(
                    MIN_FACE_PRESENCE_CONFIDENCE
                )
                .setMinTrackingConfidence(
                    MIN_TRACKING_CONFIDENCE
                )

                // Required for blendshape values such as
                // eyeBlinkLeft and browDownLeft.
                .setOutputFaceBlendshapes(true)

                // Running mode intended for live camera frames.
                .setRunningMode(RunningMode.LIVE_STREAM)

                // MediaPipe returns asynchronous results to this callback.
                .setResultListener(::handleResult)

                // MediaPipe runtime errors are returned to this callback.
                .setErrorListener(::handleMediaPipeError)

                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(
                applicationContext,
                options
            )

            Log.d(
                TAG,
                "Face Landmarker loaded successfully"
            )

        } catch (exception: Exception) {
            faceLandmarker = null

            val message =
                "Failed to initialize Face Landmarker: " +
                        (exception.message ?: "Unknown error")

            Log.e(TAG, message, exception)

            listener.onError(
                message = message,
                exception = exception
            )
        }
    }

    /**
     * Receives an image that was already converted to MPImage
     * and sends it to MediaPipe for detection.
     */
    fun detectAsync(
        mpImage: MPImage,
        timestampMs: Long = SystemClock.uptimeMillis()
    ) {
        val currentFaceLandmarker = faceLandmarker

        if (currentFaceLandmarker == null) {
            listener.onError(
                message = "Face Landmarker is not ready"
            )
            return
        }

        try {
            currentFaceLandmarker.detectAsync(
                mpImage,
                timestampMs
            )
        } catch (exception: Exception) {
            val message =
                "Failed to process camera frame: " +
                        (exception.message ?: "Unknown error")

            Log.e(TAG, message, exception)

            listener.onError(
                message = message,
                exception = exception
            )
        }
    }

    /**
     * Receives a frame directly from CameraX,
     * converts it to a MediaPipe image, and starts detection.
     */
    fun detectLiveStream(imageProxy: ImageProxy) {
        val currentFaceLandmarker = faceLandmarker

        if (currentFaceLandmarker == null) {
            imageProxy.close()

            listener.onError(
                message = "Face Landmarker is not ready"
            )
            return
        }

        try {
            val timestampMs = SystemClock.uptimeMillis()

            /*
             * CameraX provides frames as ImageProxy objects.
             * MediaPipe requires an ARGB_8888 Bitmap.
             */
            val originalBitmap = imageProxy.toBitmap()

            val argbBitmap =
                if (originalBitmap.config == Bitmap.Config.ARGB_8888) {
                    originalBitmap
                } else {
                    originalBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                    )
                }

            val mpImage = BitmapImageBuilder(argbBitmap).build()

            /*
             * Camera frames may be rotated depending on
             * the orientation of the device.
             */
            val imageProcessingOptions =
                ImageProcessingOptions.builder()
                    .setRotationDegrees(
                        imageProxy.imageInfo.rotationDegrees
                    )
                    .build()

            currentFaceLandmarker.detectAsync(
                mpImage,
                imageProcessingOptions,
                timestampMs
            )

        } catch (exception: Exception) {
            val message =
                "Failed to process CameraX frame: " +
                        (exception.message ?: "Unknown error")

            Log.e(TAG, message, exception)

            listener.onError(
                message = message,
                exception = exception
            )

        } finally {
            /*
             * Every ImageProxy must be closed after processing.
             * Otherwise, CameraX may stop providing new frames.
             */
            imageProxy.close()
        }
    }

    /**
     * Called asynchronously after MediaPipe
     * finishes processing a camera frame.
     */
    private fun handleResult(
        result: FaceLandmarkerResult,
        inputImage: MPImage
    ) {
        val faceDetected =
            result.faceLandmarks().isNotEmpty()

        if (!faceDetected) {

            /*
             * Clears the previous head pose so that
             * the next detected face does not create
             * a false movement spike.
             */
            Log.d(TAG, "No face detected")

            listener.onNoFaceDetected(
                timestampMs = result.timestampMs()
            )

            return
        }

        val blendshapes =
            result.faceBlendshapes()
                .orElse(emptyList())
                .firstOrNull()
                .orEmpty()

        val frameData =
            FaceFrameData(
                timestampMs = result.timestampMs(),

                eyeBlinkLeft =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "eyeBlinkLeft"
                    ),

                eyeBlinkRight =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "eyeBlinkRight"
                    ),

                eyeSquintLeft =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "eyeSquintLeft"
                    ),

                eyeSquintRight =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "eyeSquintRight"
                    ),

                eyeWideLeft =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "eyeWideLeft"
                    ),

                eyeWideRight =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "eyeWideRight"
                    ),

                browDownLeft =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "browDownLeft"
                    ),

                browDownRight =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "browDownRight"
                    ),

                browInnerUp =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "browInnerUp"
                    ),

                browOuterUpLeft =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "browOuterUpLeft"
                    ),

                browOuterUpRight =
                    findBlendshapeScore(
                        blendshapes = blendshapes,
                        categoryName = "browOuterUpRight"
                    )
            )



        /*
         * Returns the structured facial measurements.
         */
        listener.onFrameData(frameData)

        Log.d(
            TAG,
            "Face detected | " +
                    "landmarks=${result.faceLandmarks().first().size} | " +
                    "timestamp=${result.timestampMs()}"
        )

        /*
         * Keeps the original callback temporarily
         * for the existing test screen.
         */
        listener.onResult(
            result = result,
            imageWidth = inputImage.width,
            imageHeight = inputImage.height
        )
    }


    /**
     * Finds a blendshape by name and returns its score.
     */
    private fun findBlendshapeScore(
        blendshapes: List<Category>,
        categoryName: String
    ): Float {
        return blendshapes
            .firstOrNull {
                it.categoryName() == categoryName
            }
            ?.score()
            ?: 0f
    }

    /**
     * Receives runtime errors reported by MediaPipe.
     */
    private fun handleMediaPipeError(
        exception: RuntimeException
    ) {
        val message =
            exception.message ?: "Unknown MediaPipe error"

        Log.e(TAG, message, exception)

        listener.onError(
            message = message,
            exception = exception
        )
    }

    /**
     * Returns true when the model was loaded successfully
     * and is ready to process frames.
     */
    fun isReady(): Boolean {
        return faceLandmarker != null
    }

    /**
     * Closes the Face Landmarker and releases its resources.
     *
     * This should be called when the user leaves
     * the form-filling area.
     */
    fun close() {
        try {
            faceLandmarker?.close()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Failed to close Face Landmarker",
                exception
            )
        } finally {
            faceLandmarker = null
        }
    }

    /**
     * Classes that use this helper must implement this listener.
     */
    interface FaceLandmarkerListener {


        /**
         * Returns facial measurements in a structured object.
         *
         * The default implementation keeps existing listeners
         * compatible until they are updated.
         */
        fun onFrameData(
            frameData: FaceFrameData
        ) {
            // Optional callback.
        }


        /**
         * Returns the complete face result, including
         * landmarks, blendshapes, and transformation matrices.
         */
        fun onResult(
            result: FaceLandmarkerResult,
            imageWidth: Int,
            imageHeight: Int
        )

        /**
         * Called when no face was detected in the current frame.
         */
        fun onNoFaceDetected(
            timestampMs: Long
        )

        /**
         * Called when an initialization or processing error occurs.
         */
        fun onError(
            message: String,
            exception: Throwable? = null
        )
    }

    companion object {

        private const val TAG = "FACE_LANDMARKER"

        private const val MODEL_NAME =
            "face_landmarker.task"

        private const val NUM_FACES = 1

        private const val MIN_FACE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_FACE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
    }

}

/**
 * Contains facial measurements extracted
 * from one MediaPipe camera frame.
 */
data class FaceFrameData(
    val timestampMs: Long,

    // How closed each eye is.
    val eyeBlinkLeft: Float,
    val eyeBlinkRight: Float,

    // How strongly each eye is narrowed or tightened.
    val eyeSquintLeft: Float,
    val eyeSquintRight: Float,

    // How widely each eye is opened.
    val eyeWideLeft: Float,
    val eyeWideRight: Float,

    // How strongly each eyebrow is pulled downward.
    val browDownLeft: Float,
    val browDownRight: Float,

    // How strongly the inner parts of the eyebrows are raised.
    val browInnerUp: Float,

    // How strongly the outer part of each eyebrow is raised.
    val browOuterUpLeft: Float,
    val browOuterUpRight: Float
)