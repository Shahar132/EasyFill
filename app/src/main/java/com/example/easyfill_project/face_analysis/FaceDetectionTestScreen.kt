package com.example.easyfill_project.face_analysis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.Locale

@Composable
fun FaceDetectionTestScreen() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var statusMessage by remember {
        mutableStateOf("Waiting for camera permission")
    }

    var faceDetected by remember {
        mutableStateOf(false)
    }

    var landmarkCount by remember {
        mutableIntStateOf(0)
    }

    // Eye measurements
    var eyeBlinkLeft by remember {
        mutableFloatStateOf(0f)
    }

    var eyeBlinkRight by remember {
        mutableFloatStateOf(0f)
    }

    // Eyebrow measurements
    var browDownLeft by remember {
        mutableFloatStateOf(0f)
    }

    var browDownRight by remember {
        mutableFloatStateOf(0f)
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCameraPermission = granted

            statusMessage =
                if (granted) {
                    "Camera permission granted"
                } else {
                    "Camera permission denied"
                }

            Log.d(
                TAG,
                "Camera permission granted: $granted"
            )
        }

    /*
     * Creates CameraX and MediaPipe only while
     * the test screen is active.
     */
    DisposableEffect(
        hasCameraPermission,
        lifecycleOwner
    ) {

        if (!hasCameraPermission) {

            statusMessage = "Camera permission is required"

            onDispose {
                // No camera resources were created.
            }

        } else {

            val mainHandler =
                Handler(Looper.getMainLooper())

            val faceLandmarkerHelper =
                FaceLandmarkerHelper(
                    context = context,
                    listener = object :
                        FaceLandmarkerHelper.FaceLandmarkerListener {

                        override fun onFrameData(
                            frameData: FaceFrameData
                        ) {
                            Log.d(
                                TAG,
                                                        """
                                FaceFrameData received
                                timestamp: ${frameData.timestampMs}
                        
                                eyeBlinkLeft: ${frameData.eyeBlinkLeft}
                                eyeBlinkRight: ${frameData.eyeBlinkRight}
                        
                                eyeSquintLeft: ${frameData.eyeSquintLeft}
                                eyeSquintRight: ${frameData.eyeSquintRight}
                        
                                eyeWideLeft: ${frameData.eyeWideLeft}
                                eyeWideRight: ${frameData.eyeWideRight}
                        
                                browDownLeft: ${frameData.browDownLeft}
                                browDownRight: ${frameData.browDownRight}
                        
                                browInnerUp: ${frameData.browInnerUp}
                        
                                browOuterUpLeft: ${frameData.browOuterUpLeft}
                                browOuterUpRight: ${frameData.browOuterUpRight}
                                """.trimIndent()
                            )
                        }



                        override fun onResult(
                            result: FaceLandmarkerResult,
                            imageWidth: Int,
                            imageHeight: Int
                        ) {

                            val currentLandmarkCount =
                                result.faceLandmarks()
                                    .firstOrNull()
                                    ?.size
                                    ?: 0

                            /*
                             * faceBlendshapes contains one list
                             * of blendshape categories for each face.
                             */
                            val blendshapes =
                                result.faceBlendshapes()
                                    .orElse(emptyList())
                                    .firstOrNull()
                                    .orEmpty()

                            val currentEyeBlinkLeft =
                                findBlendshapeScore(
                                    blendshapes = blendshapes,
                                    categoryName = "eyeBlinkLeft"
                                )

                            val currentEyeBlinkRight =
                                findBlendshapeScore(
                                    blendshapes = blendshapes,
                                    categoryName = "eyeBlinkRight"
                                )

                            val currentBrowDownLeft =
                                findBlendshapeScore(
                                    blendshapes = blendshapes,
                                    categoryName = "browDownLeft"
                                )

                            val currentBrowDownRight =
                                findBlendshapeScore(
                                    blendshapes = blendshapes,
                                    categoryName = "browDownRight"
                                )


                            /*
                             * MediaPipe callbacks are asynchronous,
                             * so Compose state is updated on the main thread.
                             */
                            mainHandler.post {

                                faceDetected = true
                                landmarkCount = currentLandmarkCount

                                eyeBlinkLeft =
                                    currentEyeBlinkLeft

                                eyeBlinkRight =
                                    currentEyeBlinkRight

                                browDownLeft =
                                    currentBrowDownLeft

                                browDownRight =
                                    currentBrowDownRight

                                statusMessage =
                                    "Face detection is working"
                            }

                            Log.d(
                                TAG,
                                """
                                Face detected
                                Landmarks: $currentLandmarkCount
                                eyeBlinkLeft: $currentEyeBlinkLeft
                                eyeBlinkRight: $currentEyeBlinkRight
                                browDownLeft: $currentBrowDownLeft
                                browDownRight: $currentBrowDownRight
                                """.trimIndent()
                            )
                        }

                        override fun onNoFaceDetected(
                            timestampMs: Long
                        ) {

                            /*
                             * Clears the previous pose so that
                             * re-entering the camera does not create
                             * a false movement spike.
                             */

                            mainHandler.post {

                                faceDetected = false
                                landmarkCount = 0

                                eyeBlinkLeft = 0f
                                eyeBlinkRight = 0f

                                browDownLeft = 0f
                                browDownRight = 0f

                                statusMessage =
                                    "Camera is running, but no face was detected"
                            }

                            Log.d(
                                TAG,
                                "No face detected | timestamp=$timestampMs"
                            )
                        }

                        override fun onError(
                            message: String,
                            exception: Throwable?
                        ) {


                            mainHandler.post {

                                faceDetected = false
                                landmarkCount = 0

                                eyeBlinkLeft = 0f
                                eyeBlinkRight = 0f

                                browDownLeft = 0f
                                browDownRight = 0f


                                statusMessage = message
                            }

                            Log.e(
                                TAG,
                                message,
                                exception
                            )
                        }
                    }
                )

            val faceCameraManager =
                FaceCameraManager(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    faceLandmarkerHelper = faceLandmarkerHelper
                )

            if (faceLandmarkerHelper.isReady()) {

                statusMessage =
                    "MediaPipe loaded. Starting camera..."

                faceCameraManager.startCamera()

            } else {

                statusMessage =
                    "MediaPipe could not load"
            }

            onDispose {

                faceCameraManager.close()

                Log.d(
                    TAG,
                    "Face detection test screen closed"
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Face Detection Test"
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = statusMessage
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Face detected: $faceDetected"
        )

        Text(
            text = "Landmarks: $landmarkCount"
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Eye blink left: ${formatScore(eyeBlinkLeft)}"
        )

        Text(
            text = "Eye blink right: ${formatScore(eyeBlinkRight)}"
        )

        Text(
            text = "Brow down left: ${formatScore(browDownLeft)}"
        )

        Text(
            text = "Brow down right: ${formatScore(browDownRight)}"
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        if (!hasCameraPermission) {

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Button(
                onClick = {
                    cameraPermissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }
            ) {
                Text(
                    text = "Allow camera"
                )
            }
        }
    }
}

/**
 * Finds a specific blendshape category and returns its score.
 * Returns zero when the requested category is unavailable.
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
 * Formats a blendshape score to three decimal places.
 */
private fun formatScore(
    score: Float
): String {

    return String.format(
        Locale.US,
        "%.3f",
        score
    )
}

/**
 * Formats an angle or movement speed
 * to one decimal place.
 */
private fun formatAngle(
    angle: Float
): String {

    return String.format(
        Locale.US,
        "%.1f",
        angle
    )
}

private const val TAG = "FACE_TEST"