package com.example.easyfill_project.face_analysis

import kotlin.math.abs

/**
 * Shared facial statistics helpers.
 */
internal object FaceStats {

    const val MIN_RAW_VALUE = 0f
    const val MAX_RAW_VALUE = 1f

    private const val MODIFIED_Z_SCALE =
        0.6745f

    /*
     * Broad technical limits for normalized eyebrow geometry.
     *
     * These limits reject invalid values only.
     * They are not distress-detection thresholds.
     */
    private const val MIN_GEOMETRY_RATIO =
        0f

    private const val MAX_GEOMETRY_RATIO =
        2.5f

    private const val MAX_GEOMETRY_ASYMMETRY =
        1.0f

    private const val MIN_INTER_EYE_DISTANCE =
        0.03f

    /**
     * Raw MediaPipe blendshape features.
     */
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

    /**
     * Normalized geometry features calculated
     * from MediaPipe face landmarks.
     */
    val geometryFeatures =
        listOf(
            FaceBaselineFeature.BROW_EYE_DISTANCE_LEFT,
            FaceBaselineFeature.BROW_EYE_DISTANCE_RIGHT,
            FaceBaselineFeature.INNER_BROW_DISTANCE,
            FaceBaselineFeature.BROW_GEOMETRY_ASYMMETRY
        )

    /**
     * Every raw feature required by the personal baseline.
     */
    val rawFeatures =
        blendshapeFeatures +
                geometryFeatures

    /**
     * Returns one raw value from a facial frame.
     *
     * Float.NaN is returned when a geometry value
     * is unavailable. The frame will then fail validation.
     */
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

    /**
     * Returns true when one frame contains valid
     * blendshape values and reliable eyebrow geometry.
     */
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

    /**
     * Returns true when a normalized geometry
     * ratio is finite and technically plausible.
     */
    private fun Float.isValidGeometryRatio(): Boolean {
        return isFinite() &&
                this in MIN_GEOMETRY_RATIO..
                MAX_GEOMETRY_RATIO
    }

    /**
     * Calculates the median of a non-empty list.
     */
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

    /**
     * Calculates Median Absolute Deviation.
     */
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

    /**
     * Returns a minimum variation value for each feature.
     *
     * This prevents very small baseline MAD values from
     * producing unrealistically large Modified Z-scores.
     */
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

            /*
             * Geometry values are normalized ratios.
             *
             * These are initial engineering values and
             * should be tuned using live test logs.
             */
            FaceBaselineFeature.BROW_EYE_DISTANCE_LEFT,
            FaceBaselineFeature.BROW_EYE_DISTANCE_RIGHT ->
                0.015f

            FaceBaselineFeature.INNER_BROW_DISTANCE ->
                0.010f

            FaceBaselineFeature.BROW_GEOMETRY_ASYMMETRY ->
                0.010f

            else ->
                0.010f
        }
    }

    /**
     * Returns the effective MAD used for comparison.
     */
    fun effectiveMad(
        feature: FaceBaselineFeature,
        metric: BaselineMetric
    ): Float {
        return maxOf(
            metric.mad,
            minimumMadFor(feature)
        )
    }

    /**
     * Calculates the Modified Z-score relative
     * to the user's personal baseline.
     */
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

    /**
     * Converts a positive Modified Z deviation
     * into a continuous score from 0 to 4.
     */
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

    /**
     * Performs linear interpolation between two ranges.
     */
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