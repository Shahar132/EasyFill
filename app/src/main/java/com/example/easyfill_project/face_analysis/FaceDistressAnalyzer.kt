package com.example.easyfill_project.face_analysis

import android.util.Log
import java.util.ArrayDeque
import kotlin.math.abs

class FaceDistressAnalyzer(
    baseline: FaceBaseline
) {

    private var activeBaseline = baseline

    private val currentWindowFrames =
        mutableListOf<FaceFrameData>()

    private var currentWindowStartTimestampMs: Long? =
        null

    private var lastFrameTimestampMs: Long? =
        null

    private var observationStartTimestampMs: Long? =
        null

    private val recentWindows =
        ArrayDeque<FaceWindowResult>()

    private val blinkEvents =
        ArrayDeque<BlinkEvent>()

    private var currentBlinkStartTimestampMs: Long? =
        null

    private var smoothedScore =
        0f

    private val cleanDerivedSamples =
        mutableMapOf<
                FaceBaselineFeature,
                ArrayDeque<Float>
                >()

    private var cleanSnapshotCount =
        0

    private var lastDerivedBaselineUpdateTimestampMs =
        0L

    private var pendingBaselineUpdate: FaceBaseline? =
        null

    init {
        require(
            FaceStats.rawFeatures.all { feature ->
                feature in baseline.rawMetrics
            }
        ) {
            "Face baseline is missing required raw metrics."
        }
    }

    @Synchronized
    fun addFrame(
        frameData: FaceFrameData
    ): FaceDistressResult? {
        if (!FaceStats.isValidFrame(frameData)) {
            return null
        }

        val previousTimestamp =
            lastFrameTimestampMs

        if (
            previousTimestamp != null &&
            frameData.timestampMs -
            previousTimestamp >
            MAX_FRAME_GAP_MS
        ) {
            resetTransientState()
        }

        lastFrameTimestampMs =
            frameData.timestampMs

        if (observationStartTimestampMs == null) {
            observationStartTimestampMs =
                frameData.timestampMs
        }

        updateBlinkTracking(frameData)

        val windowStart =
            currentWindowStartTimestampMs

        if (windowStart == null) {
            currentWindowStartTimestampMs =
                frameData.timestampMs

            currentWindowFrames.add(frameData)

            return null
        }

        val elapsed =
            frameData.timestampMs -
                    windowStart

        if (elapsed < WINDOW_DURATION_MS) {
            currentWindowFrames.add(frameData)

            return null
        }

        val completedWindow =
            buildWindowResult(
                startTimestampMs = windowStart
            )

        currentWindowFrames.clear()

        currentWindowStartTimestampMs =
            frameData.timestampMs

        currentWindowFrames.add(frameData)

        if (completedWindow == null) {
            return null
        }

        recentWindows.addLast(completedWindow)

        while (
            recentWindows.size >
            HISTORY_WINDOW_COUNT
        ) {
            recentWindows.removeFirst()
        }

        val result =
            buildDistressResult(
                currentWindow = completedWindow
            )

        learnDerivedBaselineIfClean(result)

        return result
    }

    @Synchronized
    fun onFaceMissing(
        timestampMs: Long
    ): Boolean {
        val lastTimestamp =
            lastFrameTimestampMs
                ?: return false

        if (
            timestampMs - lastTimestamp <
            MAX_FRAME_GAP_MS
        ) {
            return false
        }

        resetTransientState()

        lastFrameTimestampMs = null
        observationStartTimestampMs = null

        return true
    }

    @Synchronized
    fun reset() {
        resetTransientState()

        lastFrameTimestampMs = null
        observationStartTimestampMs = null

        blinkEvents.clear()
        cleanDerivedSamples.clear()

        cleanSnapshotCount = 0
        pendingBaselineUpdate = null
        smoothedScore = 0f
    }

    @Synchronized
    fun consumePendingBaselineUpdate():
            FaceBaseline? {

        val update =
            pendingBaselineUpdate

        pendingBaselineUpdate = null

        return update
    }

    private fun buildWindowResult(
        startTimestampMs: Long
    ): FaceWindowResult? {
        if (
            currentWindowFrames.size <
            MIN_VALID_FRAMES_PER_WINDOW
        ) {
            return null
        }

        val featureResults =
            FaceStats.rawFeatures
                .associateWith { feature ->
                    val currentMedian =
                        FaceStats.median(
                            currentWindowFrames.map { frame ->
                                FaceStats.rawValue(
                                    frame = frame,
                                    feature = feature
                                )
                            }
                        )

                    val baselineMetric =
                        activeBaseline
                            .rawMetrics
                            .getValue(feature)

                    val modifiedZ =
                        FaceStats.modifiedZ(
                            feature = feature,
                            currentValue = currentMedian,
                            metric = baselineMetric
                        )

                    FaceWindowFeatureResult(
                        feature = feature,
                        currentMedian = currentMedian,
                        baselineMedian =
                            baselineMetric.median,
                        baselineMad =
                            baselineMetric.mad,
                        effectiveMad =
                            FaceStats.effectiveMad(
                                feature = feature,
                                metric = baselineMetric
                            ),
                        modifiedZ = modifiedZ,
                        score =
                            FaceStats.positiveZToScore(
                                modifiedZ
                            )
                    )
                }

        return FaceWindowResult(
            startTimestampMs =
                startTimestampMs,
            endTimestampMs =
                startTimestampMs +
                        WINDOW_DURATION_MS,
            frameCount =
                currentWindowFrames.size,
            activityLevel =
                calculateWindowActivity(
                    frames = currentWindowFrames
                ),
            featureResults =
                featureResults
        )
    }

    private fun buildDistressResult(
        currentWindow: FaceWindowResult
    ): FaceDistressResult {
        val reliable =
            recentWindows.size >=
                    PERSISTENCE_WINDOW_COUNT

        val squintScore =
            persistentScore(
                FaceBaselineFeature.EYE_SQUINT_LEFT,
                FaceBaselineFeature.EYE_SQUINT_RIGHT
            )

        val wideEyeScore =
            persistentScore(
                FaceBaselineFeature.EYE_WIDE_LEFT,
                FaceBaselineFeature.EYE_WIDE_RIGHT
            )

        val rawBrowFurrowScore =
            persistentScore(
                FaceBaselineFeature.BROW_DOWN_LEFT,
                FaceBaselineFeature.BROW_DOWN_RIGHT
            )

        val rawInnerBrowRaiseScore =
            persistentScore(
                FaceBaselineFeature.BROW_INNER_UP
            )

        val rawOuterBrowRaiseScore =
            persistentScore(
                FaceBaselineFeature.BROW_OUTER_UP_LEFT,
                FaceBaselineFeature.BROW_OUTER_UP_RIGHT
            )

        val rawBrowRaiseScore =
            maxOf(
                rawInnerBrowRaiseScore,
                rawOuterBrowRaiseScore
            )

        val browRaiseGeometryScore =
            persistentScore(
                FaceBaselineFeature.BROW_EYE_DISTANCE_LEFT,
                FaceBaselineFeature.BROW_EYE_DISTANCE_RIGHT
            )

        val browLoweringGeometryScore =
            persistentNegativeScore(
                FaceBaselineFeature.BROW_EYE_DISTANCE_LEFT,
                FaceBaselineFeature.BROW_EYE_DISTANCE_RIGHT
            )

        val innerBrowCompressionScore =
            persistentNegativeScore(
                FaceBaselineFeature.INNER_BROW_DISTANCE
            )

        val browFurrowGeometryScore =
            combineStrongest(
                listOf(
                    browLoweringGeometryScore,
                    innerBrowCompressionScore
                )
            )

        val geometryAsymmetryScore =
            persistentScore(
                FaceBaselineFeature.BROW_GEOMETRY_ASYMMETRY
            )

        val confirmedBrowFurrowScore =
            confirmBrowSignal(
                blendshapeScore =
                    rawBrowFurrowScore,
                geometryScore =
                    browFurrowGeometryScore,
                asymmetryScore =
                    geometryAsymmetryScore
            )

        val confirmedBrowRaiseScore =
            confirmBrowSignal(
                blendshapeScore =
                    rawBrowRaiseScore,
                geometryScore =
                    browRaiseGeometryScore,
                asymmetryScore =
                    geometryAsymmetryScore
            )

        logBrowConfirmation(
            rawFurrowScore =
                rawBrowFurrowScore,
            furrowGeometryScore =
                browFurrowGeometryScore,
            confirmedFurrowScore =
                confirmedBrowFurrowScore,
            rawRaiseScore =
                rawBrowRaiseScore,
            raiseGeometryScore =
                browRaiseGeometryScore,
            confirmedRaiseScore =
                confirmedBrowRaiseScore,
            asymmetryScore =
                geometryAsymmetryScore
        )

        val derivedMetrics =
            buildDerivedMetrics(
                currentTimestampMs =
                    currentWindow.endTimestampMs
            )

        val blinkPatternScore =
            calculateBlinkPatternScore(
                derivedMetrics = derivedMetrics
            )

        val activityScore =
            calculateLowActivityScore(
                derivedMetrics = derivedMetrics
            )

        val eyesScore =
            combineStrongest(
                listOf(
                    squintScore,
                    wideEyeScore,
                    blinkPatternScore
                )
            )

        val browsScore =
            combineStrongest(
                listOf(
                    confirmedBrowFurrowScore,
                    confirmedBrowRaiseScore
                )
            )

        val contributorScores =
            linkedMapOf(
                FaceDistressContributor.EYE_SQUINT
                        to squintScore,

                FaceDistressContributor.EYE_WIDE
                        to wideEyeScore,

                FaceDistressContributor.BLINK_PATTERN
                        to blinkPatternScore,

                FaceDistressContributor.BROW_FURROW
                        to confirmedBrowFurrowScore,

                FaceDistressContributor.BROW_RAISE
                        to confirmedBrowRaiseScore,

                FaceDistressContributor.LOW_FACIAL_ACTIVITY
                        to activityScore
            )

        val peakFeatureScore =
            contributorScores
                .values
                .maxOrNull()
                ?: 0f

        var rawFaceScore =
            eyesScore * EYES_WEIGHT +
                    browsScore * BROWS_WEIGHT +
                    activityScore * ACTIVITY_WEIGHT

        if (
            eyesScore >= MULTI_SIGNAL_THRESHOLD &&
            browsScore >= MULTI_SIGNAL_THRESHOLD
        ) {
            rawFaceScore +=
                MULTI_SIGNAL_BONUS
        }

        rawFaceScore =
            rawFaceScore.coerceIn(
                minimumValue = 0f,
                maximumValue = 4f
            )

        smoothedScore =
            if (!reliable) {
                0f
            } else if (smoothedScore == 0f) {
                rawFaceScore
            } else {
                smoothedScore *
                        SCORE_SMOOTHING_OLD_WEIGHT +
                        rawFaceScore *
                        SCORE_SMOOTHING_NEW_WEIGHT
            }

        if (
            rawFaceScore <
            SCORE_RESET_THRESHOLD
        ) {
            smoothedScore *=
                SCORE_DECAY_FACTOR
        }

        val finalScore =
            smoothedScore.coerceIn(
                minimumValue = 0f,
                maximumValue = 4f
            )

        val level =
            scoreToLevel(finalScore)

        val topContributor =
            contributorScores
                .maxByOrNull { entry ->
                    entry.value
                }
                ?.takeIf { entry ->
                    entry.value > 0f
                }
                ?.key
                ?: FaceDistressContributor.NONE

        return FaceDistressResult(
            score = finalScore,
            level = level,
            eyesScore = eyesScore,
            browsScore = browsScore,
            activityScore = activityScore,
            peakFeatureScore = peakFeatureScore,
            topContributor = topContributor,
            isReliable = reliable,
            windowStartTimestampMs =
                currentWindow.startTimestampMs,
            windowEndTimestampMs =
                currentWindow.endTimestampMs,
            derivedMetrics =
                derivedMetrics
        )
    }

    private fun persistentScore(
        vararg features: FaceBaselineFeature
    ): Float {
        if (
            recentWindows.size <
            PERSISTENCE_WINDOW_COUNT
        ) {
            return 0f
        }

        val lastWindows =
            recentWindows
                .toList()
                .takeLast(
                    PERSISTENCE_WINDOW_COUNT
                )

        val scores =
            lastWindows.map { window ->
                val featureScores =
                    features.map { feature ->
                        window.scoreOf(feature)
                    }

                if (featureScores.size == 1) {
                    featureScores.first()
                } else {
                    featureScores
                        .average()
                        .toFloat()
                }
            }

        return FaceStats.median(scores)
    }

    private fun persistentNegativeScore(
        vararg features: FaceBaselineFeature
    ): Float {
        if (
            recentWindows.size <
            PERSISTENCE_WINDOW_COUNT
        ) {
            return 0f
        }

        val lastWindows =
            recentWindows
                .toList()
                .takeLast(
                    PERSISTENCE_WINDOW_COUNT
                )

        val scores =
            lastWindows.map { window ->
                val featureScores =
                    features.map { feature ->
                        val modifiedZ =
                            window.featureResults[feature]
                                ?.modifiedZ
                                ?: 0f

                        FaceStats.positiveZToScore(
                            -modifiedZ
                        )
                    }

                if (featureScores.size == 1) {
                    featureScores.first()
                } else {
                    featureScores
                        .average()
                        .toFloat()
                }
            }

        return FaceStats.median(scores)
    }

    private fun confirmBrowSignal(
        blendshapeScore: Float,
        geometryScore: Float,
        asymmetryScore: Float
    ): Float {
        if (blendshapeScore <= 0f) {
            return 0f
        }

        val geometrySupported =
            geometryScore >=
                    GEOMETRY_CONFIRMATION_SCORE

        val supportedScore =
            if (geometrySupported) {
                blendshapeScore *
                        CONFIRMED_BLENDSHAPE_WEIGHT +
                        geometryScore *
                        CONFIRMED_GEOMETRY_WEIGHT
            } else {
                blendshapeScore *
                        UNCONFIRMED_BROW_WEIGHT
            }

        val asymmetryReliability =
            when {
                asymmetryScore >=
                        HIGH_ASYMMETRY_SCORE ->
                    HIGH_ASYMMETRY_WEIGHT

                asymmetryScore >=
                        MEDIUM_ASYMMETRY_SCORE ->
                    MEDIUM_ASYMMETRY_WEIGHT

                asymmetryScore >=
                        LOW_ASYMMETRY_SCORE ->
                    LOW_ASYMMETRY_WEIGHT

                else ->
                    1f
            }

        return (
                supportedScore *
                        asymmetryReliability
                ).coerceIn(
                minimumValue = 0f,
                maximumValue = 4f
            )
    }

    private fun combineStrongest(
        scores: List<Float>
    ): Float {
        val sorted =
            scores.sortedDescending()

        val strongest =
            sorted.getOrElse(0) {
                0f
            }

        val second =
            sorted.getOrElse(1) {
                0f
            }

        return (
                strongest +
                        second *
                        SECOND_SIGNAL_BONUS_WEIGHT
                ).coerceIn(
                minimumValue = 0f,
                maximumValue = 4f
            )
    }

    private fun updateBlinkTracking(
        frameData: FaceFrameData
    ) {
        val leftFeature =
            FaceBaselineFeature.EYE_BLINK_LEFT

        val rightFeature =
            FaceBaselineFeature.EYE_BLINK_RIGHT

        val leftMetric =
            activeBaseline.rawMetrics.getValue(
                leftFeature
            )

        val rightMetric =
            activeBaseline.rawMetrics.getValue(
                rightFeature
            )

        val leftScore =
            FaceStats.positiveZToScore(
                FaceStats.modifiedZ(
                    feature = leftFeature,
                    currentValue =
                        frameData.eyeBlinkLeft,
                    metric = leftMetric
                )
            )

        val rightScore =
            FaceStats.positiveZToScore(
                FaceStats.modifiedZ(
                    feature = rightFeature,
                    currentValue =
                        frameData.eyeBlinkRight,
                    metric = rightMetric
                )
            )

        val bilateralBlinkScore =
            minOf(
                leftScore,
                rightScore
            )

        if (
            currentBlinkStartTimestampMs == null &&
            bilateralBlinkScore >=
            BLINK_START_SCORE
        ) {
            currentBlinkStartTimestampMs =
                frameData.timestampMs

            return
        }

        if (
            currentBlinkStartTimestampMs != null &&
            bilateralBlinkScore <=
            BLINK_END_SCORE
        ) {
            val startTimestamp =
                currentBlinkStartTimestampMs
                    ?: return

            val duration =
                frameData.timestampMs -
                        startTimestamp

            currentBlinkStartTimestampMs =
                null

            if (
                duration in
                MIN_BLINK_DURATION_MS..
                MAX_TRACKED_EYE_CLOSURE_MS
            ) {
                blinkEvents.addLast(
                    BlinkEvent(
                        endTimestampMs =
                            frameData.timestampMs,
                        durationMs =
                            duration
                    )
                )
            }
        }

        pruneBlinkEvents(
            currentTimestampMs =
                frameData.timestampMs
        )
    }

    private fun pruneBlinkEvents(
        currentTimestampMs: Long
    ) {
        while (
            blinkEvents.isNotEmpty() &&
            currentTimestampMs -
            blinkEvents.first().endTimestampMs >
            BLINK_HISTORY_MS
        ) {
            blinkEvents.removeFirst()
        }
    }

    private fun buildDerivedMetrics(
        currentTimestampMs: Long
    ): Map<FaceBaselineFeature, Float> {
        val windows =
            recentWindows.toList()

        if (windows.isEmpty()) {
            return emptyMap()
        }

        pruneBlinkEvents(
            currentTimestampMs
        )

        val observationStart =
            observationStartTimestampMs
                ?: currentTimestampMs

        val actualObservationDurationMs =
            (
                    currentTimestampMs -
                            observationStart
                    ).coerceAtLeast(0L)

        val rateObservationDurationMs =
            actualObservationDurationMs
                .coerceAtMost(
                    BLINK_HISTORY_MS
                )

        val blinkEventList =
            blinkEvents.toList()

        val blinkObservationReady =
            actualObservationDurationMs >=
                    MIN_BLINK_PATTERN_OBSERVATION_MS

        val blinkRateIsReady =
            blinkObservationReady &&
                    blinkEventList.size >=
                    MIN_BLINK_EVENTS_FOR_RATE

        val averageBlinkDurationIsReady =
            blinkObservationReady &&
                    blinkEventList.size >=
                    MIN_BLINK_EVENTS_FOR_AVERAGE_DURATION

        val blinkRate =
            if (blinkRateIsReady) {
                blinkEventList.size *
                        60_000f /
                        rateObservationDurationMs
                            .coerceAtLeast(1L)
                            .toFloat()
            } else {
                0f
            }

        val averageBlinkDuration =
            if (averageBlinkDurationIsReady) {
                blinkEventList
                    .map { event ->
                        event.durationMs.toFloat()
                    }
                    .average()
                    .toFloat()
            } else {
                0f
            }

        val currentClosureDuration =
            currentBlinkStartTimestampMs
                ?.let { start ->
                    (
                            currentTimestampMs -
                                    start
                            ).coerceAtLeast(0L)
                }
                ?: 0L

        /*
  * Only the currently active eye closure contributes
  * to the live closure-duration distress score.
  *
  * Completed closures are counted separately and do not
  * keep the live score elevated after the eyes reopen.
  */
        val activeLongClosureDuration =
            if (
                currentClosureDuration >=
                LONG_EYE_CLOSURE_MS
            ) {
                currentClosureDuration
            } else {
                0L
            }

        val recentCompletedLongClosures =
            blinkEventList.count { event ->
                event.durationMs >=
                        LONG_EYE_CLOSURE_MS &&
                        currentTimestampMs -
                        event.endTimestampMs <=
                        LONG_CLOSURE_COUNT_HISTORY_MS
            }

        val longClosureCount =
            recentCompletedLongClosures +
                    if (activeLongClosureDuration > 0L) {
                        1
                    } else {
                        0
                    }

        val squint =
            expressionStats(
                windows = windows,
                leftFeature =
                    FaceBaselineFeature.EYE_SQUINT_LEFT,
                rightFeature =
                    FaceBaselineFeature.EYE_SQUINT_RIGHT
            )

        val wideEye =
            expressionStats(
                windows = windows,
                leftFeature =
                    FaceBaselineFeature.EYE_WIDE_LEFT,
                rightFeature =
                    FaceBaselineFeature.EYE_WIDE_RIGHT
            )

        val browFurrow =
            expressionStats(
                windows = windows,
                leftFeature =
                    FaceBaselineFeature.BROW_DOWN_LEFT,
                rightFeature =
                    FaceBaselineFeature.BROW_DOWN_RIGHT
            )

        val innerBrow =
            expressionStats(
                windows = windows,
                leftFeature =
                    FaceBaselineFeature.BROW_INNER_UP,
                rightFeature = null
            )

        val outerBrow =
            expressionStats(
                windows = windows,
                leftFeature =
                    FaceBaselineFeature.BROW_OUTER_UP_LEFT,
                rightFeature =
                    FaceBaselineFeature.BROW_OUTER_UP_RIGHT
            )

        val averageActivity =
            windows
                .map { window ->
                    window.activityLevel
                }
                .average()
                .toFloat()

        val lowActivityThreshold =
            calculateLowActivityThreshold()

        val lowActivityFlags =
            windows.map { window ->
                lowActivityThreshold != null &&
                        window.activityLevel <=
                        lowActivityThreshold
            }

        val lowActivityDuration =
            currentConsecutiveDuration(
                flags = lowActivityFlags
            )

        val lowActivityPercentage =
            percentageOfTrue(
                flags = lowActivityFlags
            )

        val expressionHoldDuration =
            calculateExpressionHoldDuration(
                windows = windows
            )

        val facialChangeCount =
            calculateFacialChangeCount(
                windows = windows
            )

        val metrics =
            linkedMapOf<FaceBaselineFeature, Float>()

        if (blinkRateIsReady) {
            metrics[
                FaceBaselineFeature.BLINK_RATE
            ] = blinkRate
        }

        if (averageBlinkDurationIsReady) {
            metrics[
                FaceBaselineFeature.AVERAGE_BLINK_DURATION
            ] = averageBlinkDuration
        }

        metrics[
            FaceBaselineFeature.LONG_EYE_CLOSURE_DURATION
        ] = activeLongClosureDuration.toFloat()

        metrics[
            FaceBaselineFeature.LONG_EYE_CLOSURE_COUNT
        ] = longClosureCount.toFloat()

        metrics[
            FaceBaselineFeature.SQUINT_INTENSITY
        ] = squint.intensity

        metrics[
            FaceBaselineFeature.SQUINT_DURATION
        ] = squint.currentDurationMs

        metrics[
            FaceBaselineFeature.SQUINT_PERCENTAGE
        ] = squint.percentage

        metrics[
            FaceBaselineFeature.SQUINT_ASYMMETRY
        ] = squint.asymmetry

        metrics[
            FaceBaselineFeature.EYE_WIDE_INTENSITY
        ] = wideEye.intensity

        metrics[
            FaceBaselineFeature.EYE_WIDE_DURATION
        ] = wideEye.currentDurationMs

        metrics[
            FaceBaselineFeature.EYE_WIDE_EVENT_COUNT
        ] = wideEye.eventCount

        metrics[
            FaceBaselineFeature.EYE_WIDE_ASYMMETRY
        ] = wideEye.asymmetry

        metrics[
            FaceBaselineFeature.BROW_FURROW_INTENSITY
        ] = browFurrow.intensity

        metrics[
            FaceBaselineFeature.BROW_FURROW_DURATION
        ] = browFurrow.currentDurationMs

        metrics[
            FaceBaselineFeature.BROW_FURROW_PERCENTAGE
        ] = browFurrow.percentage

        metrics[
            FaceBaselineFeature.BROW_FURROW_ASYMMETRY
        ] = browFurrow.asymmetry

        metrics[
            FaceBaselineFeature.BROW_INNER_UP_INTENSITY
        ] = innerBrow.intensity

        metrics[
            FaceBaselineFeature.BROW_INNER_UP_DURATION
        ] = innerBrow.currentDurationMs

        metrics[
            FaceBaselineFeature.BROW_INNER_UP_EVENT_COUNT
        ] = innerBrow.eventCount

        metrics[
            FaceBaselineFeature.OUTER_BROW_RAISE_INTENSITY
        ] = outerBrow.intensity

        metrics[
            FaceBaselineFeature.OUTER_BROW_RAISE_DURATION
        ] = outerBrow.currentDurationMs

        metrics[
            FaceBaselineFeature.OUTER_BROW_RAISE_ASYMMETRY
        ] = outerBrow.asymmetry

        metrics[
            FaceBaselineFeature.FACIAL_ACTIVITY_LEVEL
        ] = averageActivity

        metrics[
            FaceBaselineFeature.LOW_ACTIVITY_DURATION
        ] = lowActivityDuration

        metrics[
            FaceBaselineFeature.LOW_ACTIVITY_PERCENTAGE
        ] = lowActivityPercentage

        metrics[
            FaceBaselineFeature.EXPRESSION_HOLD_DURATION
        ] = expressionHoldDuration

        metrics[
            FaceBaselineFeature.FACIAL_CHANGE_COUNT
        ] = facialChangeCount

        return metrics
    }

    private fun expressionStats(
        windows: List<FaceWindowResult>,
        leftFeature: FaceBaselineFeature,
        rightFeature: FaceBaselineFeature?
    ): ExpressionStats {
        val scores =
            windows.map { window ->
                if (rightFeature == null) {
                    window.scoreOf(
                        leftFeature
                    )
                } else {
                    (
                            window.scoreOf(
                                leftFeature
                            ) +
                                    window.scoreOf(
                                        rightFeature
                                    )
                            ) / 2f
                }
            }

        val activeFlags =
            scores.map { score ->
                score >=
                        EXPRESSION_ACTIVE_SCORE
            }

        val activeScores =
            scores.filter { score ->
                score >=
                        EXPRESSION_ACTIVE_SCORE
            }

        val asymmetry =
            if (rightFeature == null) {
                0f
            } else {
                windows
                    .map { window ->
                        abs(
                            window.scoreOf(
                                leftFeature
                            ) -
                                    window.scoreOf(
                                        rightFeature
                                    )
                        )
                    }
                    .average()
                    .toFloat()
            }

        return ExpressionStats(
            intensity =
                activeScores
                    .takeIf { values ->
                        values.isNotEmpty()
                    }
                    ?.average()
                    ?.toFloat()
                    ?: 0f,

            currentDurationMs =
                currentConsecutiveDuration(
                    flags = activeFlags
                ),

            percentage =
                percentageOfTrue(
                    flags = activeFlags
                ),

            asymmetry =
                asymmetry,

            eventCount =
                countEvents(
                    flags = activeFlags
                ).toFloat()
        )
    }

    private fun calculateBlinkPatternScore(
        derivedMetrics:
        Map<FaceBaselineFeature, Float>
    ): Float {
        val currentLongClosure =
            derivedMetrics[
                FaceBaselineFeature
                    .LONG_EYE_CLOSURE_DURATION
            ] ?: 0f

        val longClosureScore =
            when {
                currentLongClosure <
                        LONG_EYE_CLOSURE_MS ->
                    0f

                currentLongClosure < 1_200f ->
                    FaceStats.interpolate(
                        value =
                            currentLongClosure,
                        inputStart =
                            LONG_EYE_CLOSURE_MS
                                .toFloat(),
                        inputEnd =
                            1_200f,
                        outputStart =
                            1f,
                        outputEnd =
                            2f
                    )

                currentLongClosure < 2_000f ->
                    FaceStats.interpolate(
                        value =
                            currentLongClosure,
                        inputStart =
                            1_200f,
                        inputEnd =
                            2_000f,
                        outputStart =
                            2f,
                        outputEnd =
                            4f
                    )

                else ->
                    4f
            }

        val learnedScores =
            listOf(
                FaceBaselineFeature.BLINK_RATE,
                FaceBaselineFeature
                    .AVERAGE_BLINK_DURATION
            ).mapNotNull { feature ->
                val currentValue =
                    derivedMetrics[feature]
                        ?: return@mapNotNull null

                compareDerivedPositive(
                    feature = feature,
                    currentValue = currentValue
                )
            }

        return maxOf(
            longClosureScore,
            learnedScores.maxOrNull()
                ?: 0f
        ).coerceIn(
            minimumValue = 0f,
            maximumValue = 4f
        )
    }

    private fun calculateLowActivityScore(
        derivedMetrics:
        Map<FaceBaselineFeature, Float>
    ): Float {
        val activityMetric =
            activeBaseline.derivedMetrics[
                FaceBaselineFeature
                    .FACIAL_ACTIVITY_LEVEL
            ] ?: return 0f

        val currentActivity =
            derivedMetrics[
                FaceBaselineFeature
                    .FACIAL_ACTIVITY_LEVEL
            ] ?: return 0f

        val negativeZ =
            0.6745f *
                    (
                            activityMetric.median -
                                    currentActivity
                            ) /
                    FaceStats.effectiveMad(
                        feature =
                            FaceBaselineFeature
                                .FACIAL_ACTIVITY_LEVEL,
                        metric = activityMetric
                    )

        val deviationScore =
            FaceStats.positiveZToScore(
                negativeZ
            )

        val lowDuration =
            derivedMetrics[
                FaceBaselineFeature
                    .LOW_ACTIVITY_DURATION
            ] ?: 0f

        val durationScore =
            when {
                lowDuration < 3_000f ->
                    0f

                lowDuration < 5_000f ->
                    FaceStats.interpolate(
                        value =
                            lowDuration,
                        inputStart =
                            3_000f,
                        inputEnd =
                            5_000f,
                        outputStart =
                            0f,
                        outputEnd =
                            2f
                    )

                lowDuration < 8_000f ->
                    FaceStats.interpolate(
                        value =
                            lowDuration,
                        inputStart =
                            5_000f,
                        inputEnd =
                            8_000f,
                        outputStart =
                            2f,
                        outputEnd =
                            4f
                    )

                else ->
                    4f
            }

        return minOf(
            deviationScore,
            durationScore
        ).coerceIn(
            minimumValue = 0f,
            maximumValue = 4f
        )
    }

    private fun compareDerivedPositive(
        feature: FaceBaselineFeature,
        currentValue: Float
    ): Float? {
        val metric =
            activeBaseline
                .derivedMetrics[feature]
                ?: return null

        return FaceStats.positiveZToScore(
            FaceStats.modifiedZ(
                feature = feature,
                currentValue = currentValue,
                metric = metric
            )
        )
    }

    private fun calculateLowActivityThreshold():
            Float? {

        val metric =
            activeBaseline.derivedMetrics[
                FaceBaselineFeature
                    .FACIAL_ACTIVITY_LEVEL
            ] ?: return null

        return (
                metric.median -
                        LOW_ACTIVITY_BASELINE_DEVIATIONS *
                        FaceStats.effectiveMad(
                            feature =
                                FaceBaselineFeature
                                    .FACIAL_ACTIVITY_LEVEL,
                            metric = metric
                        )
                ).coerceAtLeast(0f)
    }

    private fun calculateExpressionHoldDuration(
        windows: List<FaceWindowResult>
    ): Float {
        if (windows.size < 2) {
            return 0f
        }

        var consecutive =
            0

        for (
        index in
        windows.lastIndex downTo 1
        ) {
            val current =
                windows[index]

            val previous =
                windows[index - 1]

            val change =
                windowVectorChange(
                    first = previous,
                    second = current
                )

            val expressionActive =
                FaceStats.blendshapeFeatures
                    .any { feature ->
                        current.scoreOf(feature) >=
                                EXPRESSION_ACTIVE_SCORE
                    }

            if (
                expressionActive &&
                change <=
                EXPRESSION_HOLD_CHANGE_THRESHOLD
            ) {
                consecutive += 1
            } else {
                break
            }
        }

        return consecutive *
                WINDOW_DURATION_MS.toFloat()
    }

    private fun calculateFacialChangeCount(
        windows: List<FaceWindowResult>
    ): Float {
        if (windows.size < 2) {
            return 0f
        }

        return windows
            .zipWithNext()
            .count { pair ->
                val previous =
                    pair.first

                val current =
                    pair.second

                windowVectorChange(
                    first = previous,
                    second = current
                ) >=
                        FACIAL_CHANGE_THRESHOLD
            }
            .toFloat()
    }

    private fun windowVectorChange(
        first: FaceWindowResult,
        second: FaceWindowResult
    ): Float {
        return FaceStats.blendshapeFeatures
            .map { feature ->
                abs(
                    first.medianOf(feature) -
                            second.medianOf(feature)
                )
            }
            .average()
            .toFloat()
    }

    private fun calculateWindowActivity(
        frames: List<FaceFrameData>
    ): Float {
        if (frames.size < 2) {
            return 0f
        }

        return frames
            .zipWithNext()
            .map { pair ->
                val previous =
                    pair.first

                val current =
                    pair.second

                FaceStats.blendshapeFeatures
                    .map { feature ->
                        abs(
                            FaceStats.rawValue(
                                frame = current,
                                feature = feature
                            ) -
                                    FaceStats.rawValue(
                                        frame = previous,
                                        feature = feature
                                    )
                        )
                    }
                    .average()
                    .toFloat()
            }
            .average()
            .toFloat()
    }

    private fun currentConsecutiveDuration(
        flags: List<Boolean>
    ): Float {
        var count =
            0

        for (flag in flags.asReversed()) {
            if (!flag) {
                break
            }

            count += 1
        }

        return count *
                WINDOW_DURATION_MS.toFloat()
    }

    private fun percentageOfTrue(
        flags: List<Boolean>
    ): Float {
        if (flags.isEmpty()) {
            return 0f
        }

        return flags.count { flag ->
            flag
        } * 100f /
                flags.size.toFloat()
    }

    private fun countEvents(
        flags: List<Boolean>
    ): Int {
        var eventCount =
            0

        var previouslyActive =
            false

        flags.forEach { active ->
            if (
                active &&
                !previouslyActive
            ) {
                eventCount += 1
            }

            previouslyActive =
                active
        }

        return eventCount
    }

    private fun learnDerivedBaselineIfClean(
        result: FaceDistressResult
    ) {
        if (!result.isReliable) {
            return
        }

        if (
            recentWindows.size <
            MIN_HISTORY_WINDOWS_FOR_LEARNING
        ) {
            return
        }

        if (
            result.score >=
            CLEAN_SCORE_LIMIT
        ) {
            return
        }

        if (
            result.peakFeatureScore >=
            CLEAN_FEATURE_LIMIT
        ) {
            return
        }

        if (result.derivedMetrics.isEmpty()) {
            return
        }

        result.derivedMetrics.forEach { (feature, value) ->
            if (!value.isFinite()) {
                return@forEach
            }

            val samples =
                cleanDerivedSamples.getOrPut(
                    feature
                ) {
                    ArrayDeque()
                }

            samples.addLast(value)

            while (
                samples.size >
                MAX_CLEAN_SAMPLES_PER_FEATURE
            ) {
                samples.removeFirst()
            }
        }

        cleanSnapshotCount += 1

        val enoughSamples =
            cleanSnapshotCount >=
                    CLEAN_SNAPSHOTS_PER_UPDATE

        val updateIntervalPassed =
            lastDerivedBaselineUpdateTimestampMs == 0L ||
                    result.windowEndTimestampMs -
                    lastDerivedBaselineUpdateTimestampMs >=
                    DERIVED_UPDATE_INTERVAL_MS

        if (
            !enoughSamples ||
            !updateIntervalPassed
        ) {
            return
        }

        val observedMetrics =
            cleanDerivedSamples
                .mapNotNull { entry ->
                    val feature =
                        entry.key

                    val values =
                        entry.value.toList()

                    if (
                        values.size <
                        MIN_DERIVED_SAMPLES_PER_FEATURE
                    ) {
                        return@mapNotNull null
                    }

                    val median =
                        FaceStats.median(values)

                    feature to
                            BaselineMetric(
                                median = median,
                                mad =
                                    FaceStats.mad(
                                        values = values,
                                        median = median
                                    ),
                                sampleCount =
                                    values.size
                            )
                }
                .toMap()

        if (observedMetrics.isEmpty()) {
            return
        }

        val mergedMetrics =
            activeBaseline
                .derivedMetrics
                .toMutableMap()

        observedMetrics.forEach { (feature, observed) ->
            val existing =
                mergedMetrics[feature]

            mergedMetrics[feature] =
                if (existing == null) {
                    observed
                } else {
                    BaselineMetric(
                        median =
                            existing.median *
                                    EXISTING_BASELINE_WEIGHT +
                                    observed.median *
                                    NEW_BASELINE_WEIGHT,

                        mad =
                            existing.mad *
                                    EXISTING_BASELINE_WEIGHT +
                                    observed.mad *
                                    NEW_BASELINE_WEIGHT,

                        sampleCount =
                            (
                                    existing.sampleCount +
                                            observed.sampleCount
                                    ).coerceAtMost(
                                    MAX_BASELINE_SAMPLE_COUNT
                                )
                    )
                }
        }

        activeBaseline =
            activeBaseline.copy(
                derivedMetrics =
                    mergedMetrics
            )

        pendingBaselineUpdate =
            activeBaseline

        lastDerivedBaselineUpdateTimestampMs =
            result.windowEndTimestampMs

        cleanSnapshotCount = 0
        cleanDerivedSamples.clear()
    }

    private fun scoreToLevel(
        score: Float
    ): Int {
        return when {
            score < 1.25f ->
                0

            score < 2.0f ->
                1

            score < 2.75f ->
                2

            score < 3.5f ->
                3

            else ->
                4
        }
    }

    private fun logBrowConfirmation(
        rawFurrowScore: Float,
        furrowGeometryScore: Float,
        confirmedFurrowScore: Float,
        rawRaiseScore: Float,
        raiseGeometryScore: Float,
        confirmedRaiseScore: Float,
        asymmetryScore: Float
    ) {
        Log.d(
            BROW_CONFIRMATION_TAG,
            "furrowRaw=$rawFurrowScore | " +
                    "furrowGeometry=$furrowGeometryScore | " +
                    "furrowFinal=$confirmedFurrowScore | " +
                    "raiseRaw=$rawRaiseScore | " +
                    "raiseGeometry=$raiseGeometryScore | " +
                    "raiseFinal=$confirmedRaiseScore | " +
                    "asymmetry=$asymmetryScore"
        )
    }

    private fun resetTransientState() {
        currentWindowFrames.clear()
        currentWindowStartTimestampMs = null

        recentWindows.clear()

        currentBlinkStartTimestampMs = null
        blinkEvents.clear()

        observationStartTimestampMs = null

        smoothedScore = 0f
    }

    private data class BlinkEvent(
        val endTimestampMs: Long,
        val durationMs: Long
    )

    private data class ExpressionStats(
        val intensity: Float,
        val currentDurationMs: Float,
        val percentage: Float,
        val asymmetry: Float,
        val eventCount: Float
    )

    companion object {

        private const val BROW_CONFIRMATION_TAG =
            "BROW_CONFIRMATION"

        private const val WINDOW_DURATION_MS =
            500L

        private const val MIN_VALID_FRAMES_PER_WINDOW =
            5

        private const val MAX_FRAME_GAP_MS =
            1_200L

        private const val HISTORY_WINDOW_COUNT =
            20

        private const val PERSISTENCE_WINDOW_COUNT =
            3

        private const val EYES_WEIGHT =
            0.50f

        private const val BROWS_WEIGHT =
            0.35f

        private const val ACTIVITY_WEIGHT =
            0.15f

        private const val SECOND_SIGNAL_BONUS_WEIGHT =
            0.25f

        private const val MULTI_SIGNAL_THRESHOLD =
            2f

        private const val MULTI_SIGNAL_BONUS =
            0.40f

        private const val SCORE_SMOOTHING_OLD_WEIGHT =
            0.55f

        private const val SCORE_SMOOTHING_NEW_WEIGHT =
            0.45f

        private const val SCORE_RESET_THRESHOLD =
            0.25f

        private const val SCORE_DECAY_FACTOR =
            0.70f

        private const val BLINK_START_SCORE =
            2.5f

        private const val BLINK_END_SCORE =
            0.8f

        private const val MIN_BLINK_DURATION_MS =
            50L

        private const val LONG_EYE_CLOSURE_MS =
            800L

        private const val MAX_TRACKED_EYE_CLOSURE_MS =
            5_000L

        private const val BLINK_HISTORY_MS =
            60_000L

        private const val MIN_BLINK_PATTERN_OBSERVATION_MS =
            10_000L

        private const val MIN_BLINK_EVENTS_FOR_RATE =
            2

        private const val MIN_BLINK_EVENTS_FOR_AVERAGE_DURATION =
            3

        private const val EXPRESSION_ACTIVE_SCORE =
            1f

        private const val EXPRESSION_HOLD_CHANGE_THRESHOLD =
            0.01f

        private const val FACIAL_CHANGE_THRESHOLD =
            0.03f

        private const val LOW_ACTIVITY_BASELINE_DEVIATIONS =
            2f

        private const val GEOMETRY_CONFIRMATION_SCORE =
            0.35f

        private const val CONFIRMED_BLENDSHAPE_WEIGHT =
            0.70f

        private const val CONFIRMED_GEOMETRY_WEIGHT =
            0.30f

        private const val UNCONFIRMED_BROW_WEIGHT =
            0.20f

        private const val LOW_ASYMMETRY_SCORE =
            1f

        private const val MEDIUM_ASYMMETRY_SCORE =
            2f

        private const val HIGH_ASYMMETRY_SCORE =
            3f

        private const val LOW_ASYMMETRY_WEIGHT =
            0.80f

        private const val MEDIUM_ASYMMETRY_WEIGHT =
            0.55f

        private const val HIGH_ASYMMETRY_WEIGHT =
            0.30f

        private const val MIN_HISTORY_WINDOWS_FOR_LEARNING =
            10

        private const val CLEAN_SCORE_LIMIT =
            0.75f

        private const val CLEAN_FEATURE_LIMIT =
            1f

        private const val CLEAN_SNAPSHOTS_PER_UPDATE =
            60

        private const val MIN_DERIVED_SAMPLES_PER_FEATURE =
            30

        private const val MAX_CLEAN_SAMPLES_PER_FEATURE =
            240

        private const val DERIVED_UPDATE_INTERVAL_MS =
            300_000L

        private const val EXISTING_BASELINE_WEIGHT =
            0.80f

        private const val NEW_BASELINE_WEIGHT =
            0.20f

        private const val MAX_BASELINE_SAMPLE_COUNT =
            10_000

        /*
        * Completed long closures are counted only for a short
        * recent period. They must not influence the live duration
        * score for the entire 60-second blink-rate history.
        */
        private const val LONG_CLOSURE_COUNT_HISTORY_MS =
            10_000L
    }


}