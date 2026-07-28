package com.example.easyfill_project.face_analysis

import kotlin.math.abs

internal object FaceStats {

    const val MIN_RAW_VALUE = 0f
    const val MAX_RAW_VALUE = 1f

    private const val MODIFIED_Z_SCALE =
        0.6745f

    private const val MIN_GEOMETRY_RATIO =
        0f

    private const val MAX_GEOMETRY_RATIO =
        2.5f

    private const val MAX_GEOMETRY_ASYMMETRY =
        1.0f

    private const val MIN_INTER_EYE_DISTANCE =
        0.03f

    val blendshapeFeatures =
        listOf(
            FaceBaselineFeature.EYE_BLINK_LEFT,
            FaceBaselineFeature.EYE_BLINK_RIGHT,

            FaceBaselineFeature.EYE_SQUINT_LEFT,
            FaceBaselineFeature.EYE_SQUINT_RIGHT,

            FaceBaselineFeature.EYE_WIDE_LEFT,
            FaceBaselineFeature.EYE_WIDE_RIGHT,

            FaceBaselineFeature.BROW_DOWN_LEFT,
            FaceBaselineFeature.BROW_DOWN_RIGHT,

            FaceBaselineFeature.BROW_INNER_UP,

            FaceBaselineFeature.BROW_OUTER_UP_LEFT,
            FaceBaselineFeature.BROW_OUTER_UP_RIGHT
        )

    val geometryFeatures =
        listOf(
            FaceBaselineFeature.BROW_EYE_DISTANCE_LEFT,
            FaceBaselineFeature.BROW_EYE_DISTANCE_RIGHT,
            FaceBaselineFeature.INNER_BROW_DISTANCE,
            FaceBaselineFeature.BROW_GEOMETRY_ASYMMETRY
        )

    val rawFeatures =
        blendshapeFeatures +
                geometryFeatures

    fun rawValue(
        frame: FaceFrameData,
        feature: FaceBaselineFeature
    ): Float {
        return when (feature) {

            FaceBaselineFeature.EYE_BLINK_LEFT ->
                frame.eyeBlinkLeft

            FaceBaselineFeature.EYE_BLINK_RIGHT ->
                frame.eyeBlinkRight

            FaceBaselineFeature.EYE_SQUINT_LEFT ->
                frame.eyeSquintLeft

            FaceBaselineFeature.EYE_SQUINT_RIGHT ->
                frame.eyeSquintRight

            FaceBaselineFeature.EYE_WIDE_LEFT ->
                frame.eyeWideLeft

            FaceBaselineFeature.EYE_WIDE_RIGHT ->
                frame.eyeWideRight

            FaceBaselineFeature.BROW_DOWN_LEFT ->
                frame.browDownLeft

            FaceBaselineFeature.BROW_DOWN_RIGHT ->
                frame.browDownRight

            FaceBaselineFeature.BROW_INNER_UP ->
                frame.browInnerUp

            FaceBaselineFeature.BROW_OUTER_UP_LEFT ->
                frame.browOuterUpLeft

            FaceBaselineFeature.BROW_OUTER_UP_RIGHT ->
                frame.browOuterUpRight

            FaceBaselineFeature.BROW_EYE_DISTANCE_LEFT ->
                frame.browGeometry
                    ?.leftBrowEyeDistanceRatio
                    ?: Float.NaN

            FaceBaselineFeature.BROW_EYE_DISTANCE_RIGHT ->
                frame.browGeometry
                    ?.rightBrowEyeDistanceRatio
                    ?: Float.NaN

            FaceBaselineFeature.INNER_BROW_DISTANCE ->
                frame.browGeometry
                    ?.innerBrowDistanceRatio
                    ?: Float.NaN

            FaceBaselineFeature.BROW_GEOMETRY_ASYMMETRY ->
                frame.browGeometry
                    ?.asymmetry
                    ?: Float.NaN

            else ->
                throw IllegalArgumentException(
                    "Feature $feature is not a raw facial feature."
                )
        }
    }

    fun isValidFrame(
        frame: FaceFrameData
    ): Boolean {
        val blendshapesAreValid =
            blendshapeFeatures.all { feature ->
                val value =
                    rawValue(
                        frame = frame,
                        feature = feature
                    )

                value.isFinite() &&
                        value in MIN_RAW_VALUE..MAX_RAW_VALUE
            }

        if (!blendshapesAreValid) {
            return false
        }

        val geometry =
            frame.browGeometry
                ?: return false

        if (!geometry.isReliable) {
            return false
        }

        val browEyeRatiosAreValid =
            geometry.leftBrowEyeDistanceRatio
                .isValidGeometryRatio() &&
                    geometry.rightBrowEyeDistanceRatio
                        .isValidGeometryRatio()

        val innerBrowRatioIsValid =
            geometry.innerBrowDistanceRatio
                .isValidGeometryRatio()

        val asymmetryIsValid =
            geometry.asymmetry.isFinite() &&
                    geometry.asymmetry in
                    MIN_GEOMETRY_RATIO..
                    MAX_GEOMETRY_ASYMMETRY

        val normalizationDistanceIsValid =
            geometry.interEyeDistance.isFinite() &&
                    geometry.interEyeDistance >=
                    MIN_INTER_EYE_DISTANCE

        return browEyeRatiosAreValid &&
                innerBrowRatioIsValid &&
                asymmetryIsValid &&
                normalizationDistanceIsValid
    }

    private fun Float.isValidGeometryRatio(): Boolean {
        return isFinite() &&
                this in MIN_GEOMETRY_RATIO..
                MAX_GEOMETRY_RATIO
    }

    fun median(
        values: List<Float>
    ): Float {
        require(values.isNotEmpty()) {
            "Cannot calculate median from an empty list."
        }

        val sorted =
            values.sorted()

        val middle =
            sorted.size / 2

        return if (sorted.size % 2 == 0) {
            (
                    sorted[middle - 1] +
                            sorted[middle]
                    ) / 2f
        } else {
            sorted[middle]
        }
    }

    fun mad(
        values: List<Float>,
        median: Float
    ): Float {
        require(values.isNotEmpty()) {
            "Cannot calculate MAD from an empty list."
        }

        val deviations =
            values.map { value ->
                abs(value - median)
            }

        return median(deviations)
    }

    private fun minimumMadFor(
        feature: FaceBaselineFeature
    ): Float {
        return when (feature) {

            FaceBaselineFeature.EYE_BLINK_LEFT,
            FaceBaselineFeature.EYE_BLINK_RIGHT ->
                0.030f

            FaceBaselineFeature.EYE_SQUINT_LEFT,
            FaceBaselineFeature.EYE_SQUINT_RIGHT ->
                0.025f

            FaceBaselineFeature.EYE_WIDE_LEFT,
            FaceBaselineFeature.EYE_WIDE_RIGHT ->
                0.010f

            FaceBaselineFeature.BROW_DOWN_LEFT,
            FaceBaselineFeature.BROW_DOWN_RIGHT ->
                0.015f

            FaceBaselineFeature.BROW_INNER_UP ->
                0.030f

            FaceBaselineFeature.BROW_OUTER_UP_LEFT,
            FaceBaselineFeature.BROW_OUTER_UP_RIGHT ->
                0.025f

            FaceBaselineFeature.BROW_EYE_DISTANCE_LEFT,
            FaceBaselineFeature.BROW_EYE_DISTANCE_RIGHT ->
                0.015f

            FaceBaselineFeature.INNER_BROW_DISTANCE ->
                0.010f

            FaceBaselineFeature.BROW_GEOMETRY_ASYMMETRY ->
                0.010f

            FaceBaselineFeature.BLINK_RATE ->
                2.0f

            FaceBaselineFeature.AVERAGE_BLINK_DURATION ->
                60f

            FaceBaselineFeature.LONG_EYE_CLOSURE_DURATION ->
                250f

            FaceBaselineFeature.LONG_EYE_CLOSURE_COUNT ->
                1f

            FaceBaselineFeature.SQUINT_INTENSITY,
            FaceBaselineFeature.EYE_WIDE_INTENSITY,
            FaceBaselineFeature.BROW_FURROW_INTENSITY,
            FaceBaselineFeature.BROW_INNER_UP_INTENSITY,
            FaceBaselineFeature.OUTER_BROW_RAISE_INTENSITY ->
                0.25f

            FaceBaselineFeature.SQUINT_DURATION,
            FaceBaselineFeature.EYE_WIDE_DURATION,
            FaceBaselineFeature.BROW_FURROW_DURATION,
            FaceBaselineFeature.BROW_INNER_UP_DURATION,
            FaceBaselineFeature.OUTER_BROW_RAISE_DURATION,
            FaceBaselineFeature.LOW_ACTIVITY_DURATION,
            FaceBaselineFeature.EXPRESSION_HOLD_DURATION ->
                500f

            FaceBaselineFeature.SQUINT_PERCENTAGE,
            FaceBaselineFeature.BROW_FURROW_PERCENTAGE,
            FaceBaselineFeature.LOW_ACTIVITY_PERCENTAGE ->
                5f

            FaceBaselineFeature.SQUINT_ASYMMETRY,
            FaceBaselineFeature.EYE_WIDE_ASYMMETRY,
            FaceBaselineFeature.BROW_FURROW_ASYMMETRY,
            FaceBaselineFeature.OUTER_BROW_RAISE_ASYMMETRY ->
                0.20f

            FaceBaselineFeature.EYE_WIDE_EVENT_COUNT,
            FaceBaselineFeature.BROW_INNER_UP_EVENT_COUNT,
            FaceBaselineFeature.FACIAL_CHANGE_COUNT ->
                1f

            FaceBaselineFeature.FACIAL_ACTIVITY_LEVEL ->
                0.005f
        }
    }

    fun effectiveMad(
        feature: FaceBaselineFeature,
        metric: BaselineMetric
    ): Float {
        return maxOf(
            metric.mad,
            minimumMadFor(feature)
        )
    }

    fun modifiedZ(
        feature: FaceBaselineFeature,
        currentValue: Float,
        metric: BaselineMetric
    ): Float {
        return MODIFIED_Z_SCALE *
                (currentValue - metric.median) /
                effectiveMad(
                    feature = feature,
                    metric = metric
                )
    }

    fun positiveZToScore(
        modifiedZ: Float
    ): Float {
        val z =
            modifiedZ.coerceAtLeast(0f)

        return when {

            z <= 1.5f ->
                0f

            z < 2.5f ->
                interpolate(
                    value = z,
                    inputStart = 1.5f,
                    inputEnd = 2.5f,
                    outputStart = 0f,
                    outputEnd = 1f
                )

            z < 3.5f ->
                interpolate(
                    value = z,
                    inputStart = 2.5f,
                    inputEnd = 3.5f,
                    outputStart = 1f,
                    outputEnd = 2f
                )

            z < 5f ->
                interpolate(
                    value = z,
                    inputStart = 3.5f,
                    inputEnd = 5f,
                    outputStart = 2f,
                    outputEnd = 3f
                )

            z < 7f ->
                interpolate(
                    value = z,
                    inputStart = 5f,
                    inputEnd = 7f,
                    outputStart = 3f,
                    outputEnd = 4f
                )

            else ->
                4f

        }.coerceIn(
            minimumValue = 0f,
            maximumValue = 4f
        )
    }

    fun interpolate(
        value: Float,
        inputStart: Float,
        inputEnd: Float,
        outputStart: Float,
        outputEnd: Float
    ): Float {
        val progress =
            (
                    (value - inputStart) /
                            (inputEnd - inputStart)
                    ).coerceIn(
                    minimumValue = 0f,
                    maximumValue = 1f
                )

        return outputStart +
                progress *
                (outputEnd - outputStart)
    }
}