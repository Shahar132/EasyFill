package com.example.easyfill_project.face_analysis

/**
 * Raw and derived facial features
 * used by the personal baseline.
 */
enum class FaceBaselineFeature {

    // Raw MediaPipe eye features.
    EYE_BLINK_LEFT,
    EYE_BLINK_RIGHT,

    EYE_SQUINT_LEFT,
    EYE_SQUINT_RIGHT,

    EYE_WIDE_LEFT,
    EYE_WIDE_RIGHT,

    // Raw MediaPipe eyebrow features.
    BROW_DOWN_LEFT,
    BROW_DOWN_RIGHT,

    BROW_INNER_UP,

    BROW_OUTER_UP_LEFT,
    BROW_OUTER_UP_RIGHT,

    // Normalized landmark geometry features.
    BROW_EYE_DISTANCE_LEFT,
    BROW_EYE_DISTANCE_RIGHT,
    INNER_BROW_DISTANCE,
    BROW_GEOMETRY_ASYMMETRY,

    // Blink metrics.
    BLINK_RATE,
    AVERAGE_BLINK_DURATION,
    LONG_EYE_CLOSURE_DURATION,
    LONG_EYE_CLOSURE_COUNT,

    // Squint metrics.
    SQUINT_INTENSITY,
    SQUINT_DURATION,
    SQUINT_PERCENTAGE,
    SQUINT_ASYMMETRY,

    // Wide-eye metrics.
    EYE_WIDE_INTENSITY,
    EYE_WIDE_DURATION,
    EYE_WIDE_EVENT_COUNT,
    EYE_WIDE_ASYMMETRY,

    // Brow furrow metrics.
    BROW_FURROW_INTENSITY,
    BROW_FURROW_DURATION,
    BROW_FURROW_PERCENTAGE,
    BROW_FURROW_ASYMMETRY,

    // Inner-brow metrics.
    BROW_INNER_UP_INTENSITY,
    BROW_INNER_UP_DURATION,
    BROW_INNER_UP_EVENT_COUNT,

    // Outer-brow metrics.
    OUTER_BROW_RAISE_INTENSITY,
    OUTER_BROW_RAISE_DURATION,
    OUTER_BROW_RAISE_ASYMMETRY,

    // Facial activity metrics.
    FACIAL_ACTIVITY_LEVEL,
    LOW_ACTIVITY_DURATION,
    LOW_ACTIVITY_PERCENTAGE,
    EXPRESSION_HOLD_DURATION,
    FACIAL_CHANGE_COUNT
}

/**
 * Normalized eyebrow geometry calculated from face landmarks.
 *
 * The distances are normalized using the distance between
 * the eyes. This reduces sensitivity to changes in the
 * user's distance from the camera.
 */
data class BrowGeometryData(

    // Normalized distance between the left eyebrow and left eye.
    val leftBrowEyeDistanceRatio: Float,

    // Normalized distance between the right eyebrow and right eye.
    val rightBrowEyeDistanceRatio: Float,

    // Normalized distance between the inner eyebrow areas.
    val innerBrowDistanceRatio: Float,

    // Difference between the left and right brow-to-eye ratios.
    val asymmetry: Float,

    // Raw distance used for normalization and reliability checks.
    val interEyeDistance: Float,

    // False when required landmarks are missing or unreliable.
    val isReliable: Boolean
) {

    /**
     * Average normalized distance between both eyebrows and eyes.
     */
    val averageBrowEyeDistanceRatio: Float
        get() = (
                leftBrowEyeDistanceRatio +
                        rightBrowEyeDistanceRatio
                ) / 2f
}

/**
 * Normal value and variation of one feature.
 */
data class BaselineMetric(
    val median: Float,
    val mad: Float,
    val sampleCount: Int
)

/**
 * The user's personal facial baseline.
 */
data class FaceBaseline(
    val rawMetrics: Map<FaceBaselineFeature, BaselineMetric>,
    val derivedMetrics: Map<FaceBaselineFeature, BaselineMetric> =
        emptyMap()
) {

    val metrics: Map<FaceBaselineFeature, BaselineMetric>
        get() = rawMetrics + derivedMetrics

    fun getMetric(
        feature: FaceBaselineFeature
    ): BaselineMetric? {
        return rawMetrics[feature]
            ?: derivedMetrics[feature]
    }

    fun isFeatureReady(
        feature: FaceBaselineFeature
    ): Boolean {
        return feature in rawMetrics ||
                feature in derivedMetrics
    }
}

/**
 * Current stage of the facial-analysis pipeline.
 */
enum class FaceAnalysisPhase {
    IDLE,
    LOADING_BASELINE,
    CALIBRATING,
    SAVING_BASELINE,
    ANALYZING,
    ERROR
}

/**
 * Current state reported by the facial-analysis controller.
 */
data class FaceAnalysisState(
    val phase: FaceAnalysisPhase =
        FaceAnalysisPhase.IDLE,
    val message: String =
        "Face analysis is idle",
    val baselineReady: Boolean = false,
    val rawMetricCount: Int = 0,
    val derivedMetricCount: Int = 0,
    val collectedFrameCount: Int = 0,
    val validWindowCount: Int = 0
)

/**
 * Main facial signal that contributed to the current score.
 */
enum class FaceDistressContributor {
    NONE,
    EYE_SQUINT,
    EYE_WIDE,
    BLINK_PATTERN,
    BROW_FURROW,
    BROW_RAISE,
    LOW_FACIAL_ACTIVITY
}

/**
 * Result of one feature inside one 500 ms window.
 */
data class FaceWindowFeatureResult(
    val feature: FaceBaselineFeature,
    val currentMedian: Float,
    val baselineMedian: Float,
    val baselineMad: Float,
    val effectiveMad: Float,
    val modifiedZ: Float,
    val score: Float
)

/**
 * Result of one completed 500 ms window.
 */
data class FaceWindowResult(
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val frameCount: Int,
    val activityLevel: Float,
    val featureResults:
    Map<FaceBaselineFeature, FaceWindowFeatureResult>
) {

    /**
     * Returns the normalized score of one feature.
     */
    fun scoreOf(
        feature: FaceBaselineFeature
    ): Float {
        return featureResults[feature]
            ?.score
            ?: 0f
    }

    /**
     * Returns the current median of one feature.
     */
    fun medianOf(
        feature: FaceBaselineFeature
    ): Float {
        return featureResults[feature]
            ?.currentMedian
            ?: 0f
    }

    /**
     * Returns true when the window contains
     * a result for the requested feature.
     */
    fun hasFeature(
        feature: FaceBaselineFeature
    ): Boolean {
        return feature in featureResults
    }
}

/**
 * Final facial-analysis result.
 *
 * score is continuous from 0 to 4.
 * level is an integer from 0 to 4.
 */
data class FaceDistressResult(
    val score: Float,
    val level: Int,
    val eyesScore: Float,
    val browsScore: Float,
    val activityScore: Float,
    val peakFeatureScore: Float,
    val topContributor: FaceDistressContributor,
    val isReliable: Boolean,
    val windowStartTimestampMs: Long,
    val windowEndTimestampMs: Long,
    val derivedMetrics: Map<FaceBaselineFeature, Float>
)

/**
 * Current face-detection status.
 */
data class FaceDetectionStatus(
    val faceDetected: Boolean,
    val landmarkCount: Int,
    val message: String
)