package com.example.easyfill_project.face_analysis

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt

class FaceLandmarkerHelper(
    context: Context,
    private val listener: FaceLandmarkerListener
) {

    // Uses the application context to avoid holding an Activity in memory.
    private val applicationContext = context.applicationContext

    private var faceLandmarker: FaceLandmarker? = null

    // Prevents geometry logs from being written for every camera frame.
    private var lastGeometryLogTimestampMs = 0L

    // Prevents no-face logs from being written for every camera frame.
    private var lastNoFaceLogTimestampMs = 0L

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

                // Only one user is expected in front of the camera.
                .setNumFaces(NUM_FACES)

                .setMinFaceDetectionConfidence(
                    MIN_FACE_DETECTION_CONFIDENCE
                )
                .setMinFacePresenceConfidence(
                    MIN_FACE_PRESENCE_CONFIDENCE
                )
                .setMinTrackingConfidence(
                    MIN_TRACKING_CONFIDENCE
                )

                // Required for values such as eyeBlinkLeft
                // and browDownLeft.
                .setOutputFaceBlendshapes(true)

                // Running mode intended for live camera frames.
                .setRunningMode(RunningMode.LIVE_STREAM)

                .setResultListener(::handleResult)
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
     * Sends an existing MPImage to MediaPipe.
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
     * Converts a CameraX frame to MPImage
     * and sends it to MediaPipe.
     */
    fun detectLiveStream(
        imageProxy: ImageProxy
    ) {
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

            val mpImage =
                BitmapImageBuilder(argbBitmap).build()

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
            // Every ImageProxy must always be closed.
            imageProxy.close()
        }
    }

    /**
     * Handles one asynchronous MediaPipe result.
     */
    private fun handleResult(
        result: FaceLandmarkerResult,
        inputImage: MPImage
    ) {
        val faceLandmarks =
            result.faceLandmarks().firstOrNull()

        if (faceLandmarks == null) {
            logNoFaceIfNeeded(
                timestampMs = result.timestampMs()
            )

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

        val browGeometry =
            calculateBrowGeometry(
                landmarks = faceLandmarks
            )

        val frameData =
            FaceFrameData(
                timestampMs = result.timestampMs(),

                eyeBlinkLeft = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "eyeBlinkLeft"
                ),

                eyeBlinkRight = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "eyeBlinkRight"
                ),

                eyeSquintLeft = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "eyeSquintLeft"
                ),

                eyeSquintRight = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "eyeSquintRight"
                ),

                eyeWideLeft = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "eyeWideLeft"
                ),

                eyeWideRight = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "eyeWideRight"
                ),

                browDownLeft = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "browDownLeft"
                ),

                browDownRight = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "browDownRight"
                ),

                browInnerUp = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "browInnerUp"
                ),

                browOuterUpLeft = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "browOuterUpLeft"
                ),

                browOuterUpRight = findBlendshapeScore(
                    blendshapes = blendshapes,
                    categoryName = "browOuterUpRight"
                ),

                browGeometry = browGeometry
            )

        listener.onFrameData(frameData)

        logGeometryIfNeeded(
            timestampMs = result.timestampMs(),
            geometry = browGeometry
        )

        /*
         * Keeps the complete MediaPipe result available
         * for the existing test screen.
         */
        listener.onResult(
            result = result,
            imageWidth = inputImage.width,
            imageHeight = inputImage.height
        )
    }

    /**
     * Calculates normalized eyebrow geometry.
     *
     * Every distance is divided by the distance between
     * the two eye centers. This reduces the effect of
     * the user moving closer to or farther from the camera.
     */
    private fun calculateBrowGeometry(
        landmarks: List<NormalizedLandmark>
    ): BrowGeometryData? {
        if (landmarks.size <= MAX_REQUIRED_LANDMARK_INDEX) {
            return null
        }

        val leftEyeCenter = averagePoint(
            landmarks = landmarks,
            indices = LEFT_EYE_LANDMARKS
        ) ?: return null

        val rightEyeCenter = averagePoint(
            landmarks = landmarks,
            indices = RIGHT_EYE_LANDMARKS
        ) ?: return null

        val leftBrowCenter = averagePoint(
            landmarks = landmarks,
            indices = LEFT_BROW_LANDMARKS
        ) ?: return null

        val rightBrowCenter = averagePoint(
            landmarks = landmarks,
            indices = RIGHT_BROW_LANDMARKS
        ) ?: return null

        val leftInnerBrow =
            landmarks.getOrNull(
                LEFT_INNER_BROW_LANDMARK
            )?.toPoint3D()
                ?: return null

        val rightInnerBrow =
            landmarks.getOrNull(
                RIGHT_INNER_BROW_LANDMARK
            )?.toPoint3D()
                ?: return null

        val interEyeDistance =
            distance3D(
                first = leftEyeCenter,
                second = rightEyeCenter
            )

        if (
            !interEyeDistance.isFinite() ||
            interEyeDistance < MIN_INTER_EYE_DISTANCE
        ) {
            return null
        }

        val leftBrowEyeDistanceRatio =
            distance3D(
                first = leftBrowCenter,
                second = leftEyeCenter
            ) / interEyeDistance

        val rightBrowEyeDistanceRatio =
            distance3D(
                first = rightBrowCenter,
                second = rightEyeCenter
            ) / interEyeDistance

        val innerBrowDistanceRatio =
            distance3D(
                first = leftInnerBrow,
                second = rightInnerBrow
            ) / interEyeDistance

        val asymmetry =
            abs(
                leftBrowEyeDistanceRatio -
                        rightBrowEyeDistanceRatio
            )

        val valuesAreReliable =
            isGeometryRatioValid(
                leftBrowEyeDistanceRatio
            ) &&
                    isGeometryRatioValid(
                        rightBrowEyeDistanceRatio
                    ) &&
                    isGeometryRatioValid(
                        innerBrowDistanceRatio
                    ) &&
                    asymmetry.isFinite() &&
                    asymmetry <= MAX_GEOMETRY_ASYMMETRY

        if (!valuesAreReliable) {
            return null
        }

        return BrowGeometryData(
            leftBrowEyeDistanceRatio =
                leftBrowEyeDistanceRatio,

            rightBrowEyeDistanceRatio =
                rightBrowEyeDistanceRatio,

            innerBrowDistanceRatio =
                innerBrowDistanceRatio,

            asymmetry = asymmetry,

            interEyeDistance =
                interEyeDistance,

            isReliable = true
        )
    }

    /**
     * Calculates the average 3D position
     * of a group of face landmarks.
     */
    private fun averagePoint(
        landmarks: List<NormalizedLandmark>,
        indices: IntArray
    ): Point3D? {
        if (indices.isEmpty()) {
            return null
        }

        var xSum = 0f
        var ySum = 0f
        var zSum = 0f

        indices.forEach { index ->
            val landmark =
                landmarks.getOrNull(index)
                    ?: return null

            val x = landmark.x()
            val y = landmark.y()
            val z = landmark.z()

            if (
                !x.isFinite() ||
                !y.isFinite() ||
                !z.isFinite()
            ) {
                return null
            }

            xSum += x
            ySum += y
            zSum += z
        }

        val count = indices.size.toFloat()

        return Point3D(
            x = xSum / count,
            y = ySum / count,
            z = zSum / count
        )
    }

    /**
     * Converts one MediaPipe landmark
     * to the internal 3D point representation.
     */
    private fun NormalizedLandmark.toPoint3D(): Point3D? {
        val point = Point3D(
            x = x(),
            y = y(),
            z = z()
        )

        return if (
            point.x.isFinite() &&
            point.y.isFinite() &&
            point.z.isFinite()
        ) {
            point
        } else {
            null
        }
    }

    /**
     * Calculates Euclidean distance in normalized 3D space.
     */
    private fun distance3D(
        first: Point3D,
        second: Point3D
    ): Float {
        val deltaX = first.x - second.x
        val deltaY = first.y - second.y
        val deltaZ = first.z - second.z

        val squaredDistance =
            deltaX * deltaX +
                    deltaY * deltaY +
                    deltaZ * deltaZ

        return sqrt(
            squaredDistance.toDouble()
        ).toFloat()
    }

    /**
     * Rejects geometrically impossible or unstable ratios.
     */
    private fun isGeometryRatioValid(
        value: Float
    ): Boolean {
        return value.isFinite() &&
                value in MIN_GEOMETRY_RATIO..MAX_GEOMETRY_RATIO
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
     * Prints the eyebrow geometry only once per second.
     */
    private fun logGeometryIfNeeded(
        timestampMs: Long,
        geometry: BrowGeometryData?
    ) {
        if (
            lastGeometryLogTimestampMs != 0L &&
            timestampMs - lastGeometryLogTimestampMs <
            GEOMETRY_LOG_INTERVAL_MS
        ) {
            return
        }

        lastGeometryLogTimestampMs = timestampMs

        if (geometry == null) {
            Log.d(
                GEOMETRY_TAG,
                "geometry unavailable"
            )
            return
        }

        Log.d(
            GEOMETRY_TAG,
            "left=${geometry.leftBrowEyeDistanceRatio} | " +
                    "right=${geometry.rightBrowEyeDistanceRatio} | " +
                    "average=${geometry.averageBrowEyeDistanceRatio} | " +
                    "inner=${geometry.innerBrowDistanceRatio} | " +
                    "asymmetry=${geometry.asymmetry} | " +
                    "interEye=${geometry.interEyeDistance} | " +
                    "reliable=${geometry.isReliable}"
        )
    }

    /**
     * Prevents no-face messages from flooding Logcat.
     */
    private fun logNoFaceIfNeeded(
        timestampMs: Long
    ) {
        if (
            lastNoFaceLogTimestampMs != 0L &&
            timestampMs - lastNoFaceLogTimestampMs <
            NO_FACE_LOG_INTERVAL_MS
        ) {
            return
        }

        lastNoFaceLogTimestampMs = timestampMs

        Log.d(
            TAG,
            "No face detected"
        )
    }

    /**
     * Receives runtime errors reported by MediaPipe.
     */
    private fun handleMediaPipeError(
        exception: RuntimeException
    ) {
        val message =
            exception.message
                ?: "Unknown MediaPipe error"

        Log.e(TAG, message, exception)

        listener.onError(
            message = message,
            exception = exception
        )
    }

    /**
     * Returns true when the model is ready.
     */
    fun isReady(): Boolean {
        return faceLandmarker != null
    }

    /**
     * Releases MediaPipe resources.
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

    interface FaceLandmarkerListener {

        /**
         * Returns structured measurements
         * extracted from one face frame.
         */
        fun onFrameData(
            frameData: FaceFrameData
        ) {
            // Optional callback.
        }

        /**
         * Returns the complete MediaPipe result.
         */
        fun onResult(
            result: FaceLandmarkerResult,
            imageWidth: Int,
            imageHeight: Int
        )

        /**
         * Called when no face is available.
         */
        fun onNoFaceDetected(
            timestampMs: Long
        )

        /**
         * Called after an initialization
         * or processing failure.
         */
        fun onError(
            message: String,
            exception: Throwable? = null
        )
    }

    /**
     * Small internal representation of one 3D point.
     */
    private data class Point3D(
        val x: Float,
        val y: Float,
        val z: Float
    )

    companion object {

        private const val TAG =
            "FACE_LANDMARKER"

        private const val GEOMETRY_TAG =
            "BROW_GEOMETRY"

        private const val MODEL_NAME =
            "face_landmarker.task"

        private const val NUM_FACES = 1

        private const val MIN_FACE_DETECTION_CONFIDENCE =
            0.5f

        private const val MIN_FACE_PRESENCE_CONFIDENCE =
            0.5f

        private const val MIN_TRACKING_CONFIDENCE =
            0.5f

        /*
         * Anatomical left side of the detected face.
         */
        private val LEFT_EYE_LANDMARKS =
            intArrayOf(
                362,
                263,
                386,
                374
            )

        private val LEFT_BROW_LANDMARKS =
            intArrayOf(
                336,
                296,
                334,
                293,
                300
            )

        /*
         * Anatomical right side of the detected face.
         */
        private val RIGHT_EYE_LANDMARKS =
            intArrayOf(
                33,
                133,
                159,
                145
            )

        private val RIGHT_BROW_LANDMARKS =
            intArrayOf(
                107,
                66,
                105,
                63,
                70
            )

        private const val LEFT_INNER_BROW_LANDMARK =
            336

        private const val RIGHT_INNER_BROW_LANDMARK =
            107

        private const val MAX_REQUIRED_LANDMARK_INDEX =
            386

        /*
         * Rejects frames where the detected face
         * is too small for stable geometry.
         */
        private const val MIN_INTER_EYE_DISTANCE =
            0.03f

        /*
         * Broad technical limits used only to reject
         * invalid geometry, not to detect distress.
         */
        private const val MIN_GEOMETRY_RATIO =
            0f

        private const val MAX_GEOMETRY_RATIO =
            2.5f

        private const val MAX_GEOMETRY_ASYMMETRY =
            1.0f

        private const val GEOMETRY_LOG_INTERVAL_MS =
            1_000L

        private const val NO_FACE_LOG_INTERVAL_MS =
            2_000L
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

    // How strongly each eye is narrowed.
    val eyeSquintLeft: Float,
    val eyeSquintRight: Float,

    // How widely each eye is opened.
    val eyeWideLeft: Float,
    val eyeWideRight: Float,

    // How strongly each eyebrow is pulled downward.
    val browDownLeft: Float,
    val browDownRight: Float,

    // How strongly the inner eyebrows are raised.
    val browInnerUp: Float,

    // How strongly the outer eyebrows are raised.
    val browOuterUpLeft: Float,
    val browOuterUpRight: Float,

    // Normalized eyebrow geometry calculated from landmarks.
    val browGeometry: BrowGeometryData? = null
)