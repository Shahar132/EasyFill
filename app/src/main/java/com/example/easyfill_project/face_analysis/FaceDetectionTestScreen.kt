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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.easyfill_project.distress_scoring.DistressScoringManager
import java.util.Locale

/**
 * Temporary screen for testing
 * the complete facial-analysis pipeline.
 */
@Composable
fun FaceDetectionTestScreen() {

    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var detectionStatus by remember {
        mutableStateOf(
            FaceDetectionStatus(
                faceDetected = false,
                landmarkCount = 0,
                message =
                    if (hasCameraPermission) {
                        "Preparing face detection"
                    } else {
                        "Camera permission is required"
                    }
            )
        )
    }

    var analysisState by remember {
        mutableStateOf(
            FaceAnalysisState()
        )
    }

    var distressResult by remember {
        mutableStateOf<FaceDistressResult?>(
            null
        )
    }

    var displayedValues by remember {
        mutableStateOf(
            DisplayedFaceValues()
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCameraPermission =
                granted
        }

    DisposableEffect(
        hasCameraPermission,
        lifecycleOwner
    ) {

        if (!hasCameraPermission) {

            detectionStatus =
                FaceDetectionStatus(
                    faceDetected = false,
                    landmarkCount = 0,
                    message =
                        "Camera permission is required"
                )

            onDispose {
                // No camera resources were created.
            }

        } else {

            val mainHandler =
                Handler(
                    Looper.getMainLooper()
                )

            var screenActive =
                true

            var lastDebugUiTimestampMs =
                0L

            fun postToUi(
                action: () -> Unit
            ) {

                if (!screenActive) {
                    return
                }

                mainHandler.post {

                    if (screenActive) {
                        action()
                    }
                }
            }

            val session =
                FaceMonitoringSession(
                    context = context,
                    lifecycleOwner =
                        lifecycleOwner,

                    onAnalysisStateChanged = { state ->

                        postToUi {
                            analysisState = state
                        }
                    },

                    onDistressResult = { result ->

                        Log.d(
                            FACE_RESULT_TAG,
                            "score=${formatScore(result.score)} | " +
                                    "level=${result.level} | " +
                                    "reliable=${result.isReliable} | " +
                                    "eyes=${formatScore(result.eyesScore)} | " +
                                    "brows=${formatScore(result.browsScore)} | " +
                                    "activity=${formatScore(result.activityScore)} | " +
                                    "top=${result.topContributor}"
                        )

                        postToUi {
                            distressResult = result
                        }
                    },

                    onScoreReady = { score ->

                        val normalizedScore =
                            score.coerceIn(
                                minimumValue = 0,
                                maximumValue = 4
                            )

                        Log.d(
                            FACE_SCORE_SENT_TAG,
                            "Sending face score to manager: " +
                                    normalizedScore
                        )

                        DistressScoringManager
                            .updateFaceScore(
                                normalizedScore
                            )
                    },

                    onDetectionStatusChanged = { status ->

                        postToUi {

                            detectionStatus =
                                status

                            if (!status.faceDetected) {

                                displayedValues =
                                    DisplayedFaceValues()
                            }
                        }
                    },

                    onFrameDataForDebug = { frame ->

                        if (
                            frame.timestampMs -
                            lastDebugUiTimestampMs >=
                            DEBUG_UI_UPDATE_INTERVAL_MS
                        ) {

                            lastDebugUiTimestampMs =
                                frame.timestampMs

                            postToUi {

                                displayedValues =
                                    DisplayedFaceValues.from(
                                        frame
                                    )
                            }
                        }
                    }
                )

            val started =
                session.start()

            if (!started) {

                detectionStatus =
                    FaceDetectionStatus(
                        faceDetected = false,
                        landmarkCount = 0,
                        message =
                            "Facial analysis could not start"
                    )
            }

            onDispose {

                screenActive = false

                mainHandler.removeCallbacksAndMessages(
                    null
                )

                session.close()

                /*
                 * Resets only the facial score.
                 * Other collectors are not affected.
                 */
                DistressScoringManager
                    .updateFaceScore(0)
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Top
    ) {

        Text(
            text = "Face Detection Test"
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Text(
            text =
                detectionStatus.message
        )

        Text(
            text =
                "Face detected: " +
                        detectionStatus.faceDetected
        )

        Text(
            text =
                "Landmarks: " +
                        detectionStatus.landmarkCount
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Analysis phase: " +
                        analysisState.phase
        )

        Text(
            text =
                analysisState.message
        )

        Text(
            text =
                "Baseline ready: " +
                        analysisState.baselineReady
        )

        Text(
            text =
                "Raw baseline metrics: " +
                        "${analysisState.rawMetricCount} / 11"
        )

        Text(
            text =
                "Derived baseline metrics: " +
                        analysisState.derivedMetricCount
        )

        Text(
            text =
                "Calibration frames: " +
                        analysisState.collectedFrameCount
        )

        Text(
            text =
                "Valid windows: " +
                        analysisState.validWindowCount
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        val result =
            distressResult

        Text(
            text =
                "Face score: " +
                        "${formatScore(result?.score ?: 0f)} / 4"
        )

        Text(
            text =
                "Manager level: " +
                        (result?.level ?: 0)
        )

        Text(
            text =
                "Reliable result: " +
                        (result?.isReliable ?: false)
        )

        Text(
            text =
                "Eyes score: " +
                        formatScore(
                            result?.eyesScore
                                ?: 0f
                        )
        )

        Text(
            text =
                "Brows score: " +
                        formatScore(
                            result?.browsScore
                                ?: 0f
                        )
        )

        Text(
            text =
                "Activity score: " +
                        formatScore(
                            result?.activityScore
                                ?: 0f
                        )
        )

        Text(
            text =
                "Top contributor: " +
                        (
                                result?.topContributor
                                    ?: FaceDistressContributor.NONE
                                )
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        FaceValueText(
            label = "Eye blink left",
            value =
                displayedValues.eyeBlinkLeft
        )

        FaceValueText(
            label = "Eye blink right",
            value =
                displayedValues.eyeBlinkRight
        )

        FaceValueText(
            label = "Eye squint left",
            value =
                displayedValues.eyeSquintLeft
        )

        FaceValueText(
            label = "Eye squint right",
            value =
                displayedValues.eyeSquintRight
        )

        FaceValueText(
            label = "Eye wide left",
            value =
                displayedValues.eyeWideLeft
        )

        FaceValueText(
            label = "Eye wide right",
            value =
                displayedValues.eyeWideRight
        )

        FaceValueText(
            label = "Brow down left",
            value =
                displayedValues.browDownLeft
        )

        FaceValueText(
            label = "Brow down right",
            value =
                displayedValues.browDownRight
        )

        FaceValueText(
            label = "Brow inner up",
            value =
                displayedValues.browInnerUp
        )

        FaceValueText(
            label = "Brow outer up left",
            value =
                displayedValues.browOuterUpLeft
        )

        FaceValueText(
            label = "Brow outer up right",
            value =
                displayedValues.browOuterUpRight
        )

        if (!hasCameraPermission) {

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            Button(
                onClick = {

                    permissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }
            ) {

                Text(
                    text = "Allow camera"
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(30.dp)
        )
    }
}

@Composable
private fun FaceValueText(
    label: String,
    value: Float
) {

    Text(
        text =
            "$label: ${formatScore(value)}"
    )
}

private data class DisplayedFaceValues(
    val eyeBlinkLeft: Float = 0f,
    val eyeBlinkRight: Float = 0f,

    val eyeSquintLeft: Float = 0f,
    val eyeSquintRight: Float = 0f,

    val eyeWideLeft: Float = 0f,
    val eyeWideRight: Float = 0f,

    val browDownLeft: Float = 0f,
    val browDownRight: Float = 0f,

    val browInnerUp: Float = 0f,

    val browOuterUpLeft: Float = 0f,
    val browOuterUpRight: Float = 0f
) {

    companion object {

        fun from(
            frame: FaceFrameData
        ): DisplayedFaceValues {

            return DisplayedFaceValues(
                eyeBlinkLeft =
                    frame.eyeBlinkLeft,

                eyeBlinkRight =
                    frame.eyeBlinkRight,

                eyeSquintLeft =
                    frame.eyeSquintLeft,

                eyeSquintRight =
                    frame.eyeSquintRight,

                eyeWideLeft =
                    frame.eyeWideLeft,

                eyeWideRight =
                    frame.eyeWideRight,

                browDownLeft =
                    frame.browDownLeft,

                browDownRight =
                    frame.browDownRight,

                browInnerUp =
                    frame.browInnerUp,

                browOuterUpLeft =
                    frame.browOuterUpLeft,

                browOuterUpRight =
                    frame.browOuterUpRight
            )
        }
    }
}

private fun formatScore(
    score: Float
): String {

    return String.format(
        Locale.US,
        "%.2f",
        score
    )
}

private const val DEBUG_UI_UPDATE_INTERVAL_MS =
    200L

private const val FACE_RESULT_TAG =
    "FACE_RESULT"

private const val FACE_SCORE_SENT_TAG =
    "FACE_SCORE_SENT"