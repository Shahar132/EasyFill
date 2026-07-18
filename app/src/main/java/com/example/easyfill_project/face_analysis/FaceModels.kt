package com.example.easyfill_project.face_analysis

/**
 * Raw and derived facial features
 * used by the personal baseline.
 */
enum class FaceBaselineFeature {

    // Raw MediaPipe features.
    EYE_BLINK_LEFT,
    EYE_BLINK_RIGHT,

    EYE_SQUINT_LEFT,
    EYE_SQUINT_RIGHT,

    EYE_WIDE_LEFT,
    EYE_WIDE_RIGHT,

    BROW_DOWN_LEFT,
    BROW_DOWN_RIGHT,

    BROW_INNER_UP,

    BROW_OUTER_UP_LEFT,
    BROW_OUTER_UP_RIGHT,

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

enum class FaceAnalysisPhase {
    IDLE,
    LOADING_BASELINE,
    CALIBRATING,
    SAVING_BASELINE,
    ANALYZING,
    ERROR
}

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
 * Result of one raw feature
 * inside one 500 ms window.
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

    fun scoreOf(
        feature: FaceBaselineFeature
    ): Float {

        return featureResults[feature]
            ?.score
            ?: 0f
    }

    fun medianOf(
        feature: FaceBaselineFeature
    ): Float {

        return featureResults[feature]
            ?.currentMedian
            ?: 0f
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

data class FaceDetectionStatus(
    val faceDetected: Boolean,
    val landmarkCount: Int,
    val message: String
)