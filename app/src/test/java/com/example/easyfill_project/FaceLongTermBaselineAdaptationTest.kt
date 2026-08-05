package com.example.easyfill_project.face_analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Long-term synthetic stress test for facial baseline adaptation.
 *
 * The test performs 500 accepted repeated uses for each of four
 * virtual participants. Every use runs the real FaceDistressAnalyzer
 * on stable normal facial input and consumes its learned baseline update.
 *
 * The CSV stores every update, so Jupyter can inspect all uses or only
 * selected checkpoints such as 1, 2, 5, 10, 25, 50, 100, 250 and 500.
 *
 * After 500 updates, the test verifies that:
 *
 * - normal facial behavior still remains at level 0,
 * - a strong eye-squint signal is still detected,
 * - strong distress is blocked from baseline learning,
 * - invalid facial geometry is blocked from baseline learning,
 * - all 15 raw baseline metrics remain unchanged,
 * - all learned values remain finite and valid.
 *
 * Output:
 *
 * app/build/synthetic-face-long-term-adaptation/
 * ├── synthetic_face_long_term_adaptation.csv
 * └── synthetic_face_long_term_adaptation_summary.txt
 */
class FaceLongTermBaselineAdaptationTest {

    @Test
    fun generateLongTermFaceAdaptationDataset() {

        val outputDirectory =
            resolveOutputDirectory()

        val rows =
            mutableListOf<AdaptationRow>()

        val safetyRows =
            mutableListOf<SafetyRow>()

        virtualParticipants().forEach { participant ->

            val originalBaseline =
                createBaseline(
                    participant =
                        participant,
                    activityMedian =
                        0.012f,
                    facialChangeMedian =
                        8f
                )

            var currentBaseline =
                originalBaseline

            var estimatedActivityTarget: Float? =
                null

            var previousDistance: Float? =
                null

            for (
            usageIndex in
            1..TOTAL_ACCEPTED_USES
            ) {

                val activityBefore =
                    currentBaseline
                        .derivedMetrics
                        .getValue(
                            FaceBaselineFeature
                                .FACIAL_ACTIVITY_LEVEL
                        )

                val analyzer =
                    FaceDistressAnalyzer(
                        currentBaseline
                    )

                val normalResults =
                    feedStableNormalLearningSequence(
                        analyzer =
                            analyzer,
                        participant =
                            participant,
                        usageIndex =
                            usageIndex
                    )

                val updatedBaseline =
                    analyzer
                        .consumePendingBaselineUpdate()

                assertNotNull(
                    "No face baseline update was produced at use $usageIndex for ${participant.id}.",
                    updatedBaseline
                )

                val baselineAfter =
                    updatedBaseline!!

                val activityAfter =
                    baselineAfter
                        .derivedMetrics
                        .getValue(
                            FaceBaselineFeature
                                .FACIAL_ACTIVITY_LEVEL
                        )

                if (
                    estimatedActivityTarget ==
                    null
                ) {
                    estimatedActivityTarget =
                        (
                                activityAfter.median -
                                        activityBefore.median *
                                        EXISTING_BASELINE_WEIGHT
                                ) /
                                NEW_DATA_WEIGHT
                }

                val stableTarget =
                    estimatedActivityTarget!!

                val distanceAfter =
                    abs(
                        activityAfter.median -
                                stableTarget
                    )

                val distanceDidNotIncrease =
                    previousDistance == null ||
                            distanceAfter <=
                            previousDistance!! +
                            DISTANCE_TOLERANCE

                val reliableResults =
                    normalResults.filter {
                        it.isReliable
                    }

                val averageNormalScore =
                    reliableResults
                        .map {
                            it.score
                        }
                        .averageOrZero()

                val normalScoreStandardDeviation =
                    standardDeviation(
                        reliableResults.map {
                            it.score
                        }
                    )

                val positiveNormalWindowPercent =
                    reliableResults.percentage {
                        it.level > 0
                    }

                val maximumNormalLevel =
                    reliableResults
                        .maxOfOrNull {
                            it.level
                        }
                        ?: 0

                val rawBaselineUnchanged =
                    baselineAfter.rawMetrics ==
                            originalBaseline.rawMetrics

                val allMetricsFinite =
                    baselineAfter.metrics
                        .values
                        .all { metric ->
                            metric.median.isFinite() &&
                                    metric.mad.isFinite() &&
                                    metric.mad >= 0f &&
                                    metric.sampleCount > 0
                        }

                rows.add(
                    AdaptationRow(
                        participantId =
                            participant.id,
                        usageIndex =
                            usageIndex,
                        isCheckpoint =
                            usageIndex in
                                    REPORT_CHECKPOINTS,
                        updateProduced =
                            true,
                        rawBaselineUnchanged =
                            rawBaselineUnchanged,
                        allMetricsFinite =
                            allMetricsFinite,
                        rawMetricCount =
                            baselineAfter
                                .rawMetrics
                                .size,
                        derivedMetricCount =
                            baselineAfter
                                .derivedMetrics
                                .size,
                        activityMedianBefore =
                            activityBefore.median,
                        activityMedianAfter =
                            activityAfter.median,
                        activityMadAfter =
                            activityAfter.mad,
                        activitySampleCountAfter =
                            activityAfter.sampleCount,
                        estimatedActivityTarget =
                            stableTarget,
                        distanceToActivityTarget =
                            distanceAfter,
                        distanceDidNotIncrease =
                            distanceDidNotIncrease,
                        averageNormalScore =
                            averageNormalScore,
                        normalScoreStandardDeviation =
                            normalScoreStandardDeviation,
                        positiveNormalWindowPercent =
                            positiveNormalWindowPercent,
                        maximumNormalLevel =
                            maximumNormalLevel,
                        reliableNormalWindowCount =
                            reliableResults.size
                    )
                )

                assertEquals(
                    "The 15 raw facial metrics changed during long-term adaptation.",
                    originalBaseline.rawMetrics,
                    baselineAfter.rawMetrics
                )

                assertTrue(
                    "A learned facial metric became invalid.",
                    allMetricsFinite
                )

                assertTrue(
                    "The facial baseline moved away from its stable target.",
                    distanceDidNotIncrease
                )

                assertEquals(
                    "Stable normal facial behavior produced a positive level.",
                    0,
                    maximumNormalLevel
                )

                currentBaseline =
                    baselineAfter

                previousDistance =
                    distanceAfter
            }

            val finalNormalAnalyzer =
                FaceDistressAnalyzer(
                    currentBaseline
                )

            val finalNormalResults =
                feedFrames(
                    analyzer =
                        finalNormalAnalyzer,
                    participant =
                        participant,
                    mode =
                        FrameMode.NORMAL,
                    durationMs =
                        FINAL_NORMAL_CHECK_DURATION_MS,
                    amplitude =
                        0f,
                    startTimestampMs =
                        20_000_000L +
                                participant.seed,
                    geometryReliable =
                        true
                )

            val finalNormalMaximumLevel =
                finalNormalResults
                    .filter {
                        it.isReliable
                    }
                    .maxOfOrNull {
                        it.level
                    }
                    ?: 0

            val distressAnalyzer =
                FaceDistressAnalyzer(
                    currentBaseline
                )

            val distressResults =
                feedFrames(
                    analyzer =
                        distressAnalyzer,
                    participant =
                        participant,
                    mode =
                        FrameMode.STRONG_EYE_SQUINT,
                    durationMs =
                        DISTRESS_CHECK_DURATION_MS,
                    amplitude =
                        0.35f,
                    startTimestampMs =
                        30_000_000L +
                                participant.seed,
                    geometryReliable =
                        true
                )

            val distressMaximumLevel =
                distressResults
                    .filter {
                        it.isReliable
                    }
                    .maxOfOrNull {
                        it.level
                    }
                    ?: 0

            val distressDetected =
                distressMaximumLevel > 0

            val distressUpdateBlocked =
                distressAnalyzer
                    .consumePendingBaselineUpdate() ==
                        null

            val invalidAnalyzer =
                FaceDistressAnalyzer(
                    currentBaseline
                )

            feedFrames(
                analyzer =
                    invalidAnalyzer,
                participant =
                    participant,
                mode =
                    FrameMode.NORMAL,
                durationMs =
                    INVALID_CHECK_DURATION_MS,
                amplitude =
                    0f,
                startTimestampMs =
                    40_000_000L +
                            participant.seed,
                geometryReliable =
                    false
            )

            val invalidUpdateBlocked =
                invalidAnalyzer
                    .consumePendingBaselineUpdate() ==
                        null

            safetyRows.add(
                SafetyRow(
                    participantId =
                        participant.id,
                    checkedAfterUse =
                        TOTAL_ACCEPTED_USES,
                    finalNormalMaximumLevel =
                        finalNormalMaximumLevel,
                    distressMaximumLevel =
                        distressMaximumLevel,
                    distressStillDetected =
                        distressDetected,
                    distressUpdateBlocked =
                        distressUpdateBlocked,
                    invalidUpdateBlocked =
                        invalidUpdateBlocked,
                    rawBaselineStillUnchanged =
                        currentBaseline.rawMetrics ==
                                originalBaseline.rawMetrics,
                    allFinalMetricsFinite =
                        currentBaseline.metrics
                            .values
                            .all { metric ->
                                metric.median.isFinite() &&
                                        metric.mad.isFinite() &&
                                        metric.mad >= 0f &&
                                        metric.sampleCount > 0
                            }
                )
            )

            assertEquals(
                "Normal facial behavior became positive after 500 uses.",
                0,
                finalNormalMaximumLevel
            )

            assertTrue(
                "Strong facial deviation was no longer detected after 500 uses.",
                distressDetected
            )

            assertTrue(
                "Strong facial distress was learned into the baseline.",
                distressUpdateBlocked
            )

            assertTrue(
                "Invalid facial geometry was learned into the baseline.",
                invalidUpdateBlocked
            )
        }

        val adaptationFile =
            File(
                outputDirectory,
                "synthetic_face_long_term_adaptation.csv"
            )

        val safetyFile =
            File(
                outputDirectory,
                "synthetic_face_long_term_safety.csv"
            )

        val summaryFile =
            File(
                outputDirectory,
                "synthetic_face_long_term_adaptation_summary.txt"
            )

        writeAdaptationCsv(
            file =
                adaptationFile,
            rows =
                rows
        )

        writeSafetyCsv(
            file =
                safetyFile,
            rows =
                safetyRows
        )

        val summary =
            buildSummary(
                rows =
                    rows,
                safetyRows =
                    safetyRows
            )

        summaryFile.writeText(
            summary
        )

        println(summary)
        println()
        println(
            "Adaptation CSV: ${adaptationFile.absolutePath}"
        )
        println(
            "Safety CSV: ${safetyFile.absolutePath}"
        )
        println(
            "Summary: ${summaryFile.absolutePath}"
        )

        assertTrue(
            adaptationFile.exists()
        )

        assertTrue(
            safetyFile.exists()
        )

        assertTrue(
            summaryFile.exists()
        )
    }

    // ---------------------------------------------------------------------
    // Real analyzer input
    // ---------------------------------------------------------------------

    private fun feedStableNormalLearningSequence(
        analyzer: FaceDistressAnalyzer,
        participant: VirtualParticipant,
        usageIndex: Int
    ): List<FaceDistressResult> {

        /*
         * The same deterministic normal sequence is used in
         * every repeated use. This isolates convergence from
         * random changes in the generated input.
         */
        return feedFrames(
            analyzer =
                analyzer,
            participant =
                participant,
            mode =
                FrameMode.NORMAL,
            durationMs =
                LEARNING_SEQUENCE_DURATION_MS,
            amplitude =
                0f,
            startTimestampMs =
                usageIndex *
                        1_000_000L +
                        participant.seed,
            geometryReliable =
                true
        )
    }

    private fun feedFrames(
        analyzer: FaceDistressAnalyzer,
        participant: VirtualParticipant,
        mode: FrameMode,
        durationMs: Long,
        amplitude: Float,
        startTimestampMs: Long,
        geometryReliable: Boolean
    ): List<FaceDistressResult> {

        val results =
            mutableListOf<FaceDistressResult>()

        var timestampMs =
            startTimestampMs

        var frameIndex =
            0

        val frameCount =
            (
                    durationMs /
                            FRAME_INTERVAL_MS
                    ).toInt()
                .coerceAtLeast(
                    1
                )

        repeat(
            frameCount
        ) {

            val frame =
                createFrame(
                    participant =
                        participant,
                    timestampMs =
                        timestampMs,
                    frameIndex =
                        frameIndex,
                    mode =
                        mode,
                    amplitude =
                        amplitude,
                    geometryReliable =
                        geometryReliable
                )

            analyzer
                .addFrame(
                    frame
                )
                ?.let {
                    results.add(
                        it
                    )
                }

            timestampMs +=
                FRAME_INTERVAL_MS

            frameIndex +=
                1
        }

        return results
    }

    private fun createFrame(
        participant: VirtualParticipant,
        timestampMs: Long,
        frameIndex: Int,
        mode: FrameMode,
        amplitude: Float,
        geometryReliable: Boolean
    ): FaceFrameData {

        val jitter =
            deterministicJitter(
                frameIndex =
                    frameIndex,
                phaseShift =
                    participant.seed
            )

        var eyeBlinkLeft =
            participant.eyeBlinkLeft +
                    jitter * 0.50f

        var eyeBlinkRight =
            participant.eyeBlinkRight -
                    jitter * 0.45f

        var eyeSquintLeft =
            participant.eyeSquintLeft +
                    jitter * 0.80f

        var eyeSquintRight =
            participant.eyeSquintRight -
                    jitter * 0.70f

        val eyeWideLeft =
            participant.eyeWideLeft +
                    jitter * 0.30f

        val eyeWideRight =
            participant.eyeWideRight -
                    jitter * 0.25f

        val browDownLeft =
            participant.browDownLeft +
                    jitter * 0.40f

        val browDownRight =
            participant.browDownRight -
                    jitter * 0.35f

        val browInnerUp =
            participant.browInnerUp +
                    jitter * 0.30f

        val browOuterUpLeft =
            participant.browOuterUpLeft +
                    jitter * 0.35f

        val browOuterUpRight =
            participant.browOuterUpRight -
                    jitter * 0.30f

        val leftBrowEyeDistance =
            participant
                .leftBrowEyeDistance +
                    jitter * 0.10f

        val rightBrowEyeDistance =
            participant
                .rightBrowEyeDistance -
                    jitter * 0.10f

        val innerBrowDistance =
            participant
                .innerBrowDistance +
                    jitter * 0.08f

        if (
            mode ==
            FrameMode.STRONG_EYE_SQUINT
        ) {
            eyeSquintLeft +=
                amplitude

            eyeSquintRight +=
                amplitude
        }

        eyeBlinkLeft =
            eyeBlinkLeft.unitValue()

        eyeBlinkRight =
            eyeBlinkRight.unitValue()

        eyeSquintLeft =
            eyeSquintLeft.unitValue()

        eyeSquintRight =
            eyeSquintRight.unitValue()

        val safeEyeWideLeft =
            eyeWideLeft.unitValue()

        val safeEyeWideRight =
            eyeWideRight.unitValue()

        val safeBrowDownLeft =
            browDownLeft.unitValue()

        val safeBrowDownRight =
            browDownRight.unitValue()

        val safeBrowInnerUp =
            browInnerUp.unitValue()

        val safeBrowOuterUpLeft =
            browOuterUpLeft.unitValue()

        val safeBrowOuterUpRight =
            browOuterUpRight.unitValue()

        val safeLeftBrowEyeDistance =
            leftBrowEyeDistance
                .coerceIn(
                    0.02f,
                    2.40f
                )

        val safeRightBrowEyeDistance =
            rightBrowEyeDistance
                .coerceIn(
                    0.02f,
                    2.40f
                )

        val safeInnerBrowDistance =
            innerBrowDistance
                .coerceIn(
                    0.02f,
                    2.40f
                )

        val asymmetry =
            abs(
                safeLeftBrowEyeDistance -
                        safeRightBrowEyeDistance
            ).coerceIn(
                0f,
                1f
            )

        return FaceFrameData(
            timestampMs =
                timestampMs,

            eyeBlinkLeft =
                eyeBlinkLeft,

            eyeBlinkRight =
                eyeBlinkRight,

            eyeSquintLeft =
                eyeSquintLeft,

            eyeSquintRight =
                eyeSquintRight,

            eyeWideLeft =
                safeEyeWideLeft,

            eyeWideRight =
                safeEyeWideRight,

            browDownLeft =
                safeBrowDownLeft,

            browDownRight =
                safeBrowDownRight,

            browInnerUp =
                safeBrowInnerUp,

            browOuterUpLeft =
                safeBrowOuterUpLeft,

            browOuterUpRight =
                safeBrowOuterUpRight,

            browGeometry =
                BrowGeometryData(
                    leftBrowEyeDistanceRatio =
                        safeLeftBrowEyeDistance,

                    rightBrowEyeDistanceRatio =
                        safeRightBrowEyeDistance,

                    innerBrowDistanceRatio =
                        safeInnerBrowDistance,

                    asymmetry =
                        asymmetry,

                    interEyeDistance =
                        participant
                            .interEyeDistance,

                    isReliable =
                        geometryReliable
                )
        )
    }

    private fun deterministicJitter(
        frameIndex: Int,
        phaseShift: Int
    ): Float {

        val firstWave =
            sin(
                (
                        frameIndex +
                                phaseShift
                        ) *
                        0.57
            ).toFloat() *
                    0.0040f

        val secondWave =
            cos(
                (
                        frameIndex +
                                phaseShift
                        ) *
                        0.21
            ).toFloat() *
                    0.0020f

        return firstWave +
                secondWave
    }

    // ---------------------------------------------------------------------
    // Baseline
    // ---------------------------------------------------------------------

    private fun createBaseline(
        participant: VirtualParticipant,
        activityMedian: Float,
        facialChangeMedian: Float
    ): FaceBaseline {

        val rawMetrics =
            linkedMapOf(
                FaceBaselineFeature
                    .EYE_BLINK_LEFT to
                        rawMetric(
                            participant.eyeBlinkLeft,
                            0.015f
                        ),

                FaceBaselineFeature
                    .EYE_BLINK_RIGHT to
                        rawMetric(
                            participant.eyeBlinkRight,
                            0.015f
                        ),

                FaceBaselineFeature
                    .EYE_SQUINT_LEFT to
                        rawMetric(
                            participant.eyeSquintLeft,
                            0.012f
                        ),

                FaceBaselineFeature
                    .EYE_SQUINT_RIGHT to
                        rawMetric(
                            participant.eyeSquintRight,
                            0.012f
                        ),

                FaceBaselineFeature
                    .EYE_WIDE_LEFT to
                        rawMetric(
                            participant.eyeWideLeft,
                            0.008f
                        ),

                FaceBaselineFeature
                    .EYE_WIDE_RIGHT to
                        rawMetric(
                            participant.eyeWideRight,
                            0.008f
                        ),

                FaceBaselineFeature
                    .BROW_DOWN_LEFT to
                        rawMetric(
                            participant.browDownLeft,
                            0.010f
                        ),

                FaceBaselineFeature
                    .BROW_DOWN_RIGHT to
                        rawMetric(
                            participant.browDownRight,
                            0.010f
                        ),

                FaceBaselineFeature
                    .BROW_INNER_UP to
                        rawMetric(
                            participant.browInnerUp,
                            0.015f
                        ),

                FaceBaselineFeature
                    .BROW_OUTER_UP_LEFT to
                        rawMetric(
                            participant.browOuterUpLeft,
                            0.012f
                        ),

                FaceBaselineFeature
                    .BROW_OUTER_UP_RIGHT to
                        rawMetric(
                            participant.browOuterUpRight,
                            0.012f
                        ),

                FaceBaselineFeature
                    .BROW_EYE_DISTANCE_LEFT to
                        rawMetric(
                            participant
                                .leftBrowEyeDistance,
                            0.008f
                        ),

                FaceBaselineFeature
                    .BROW_EYE_DISTANCE_RIGHT to
                        rawMetric(
                            participant
                                .rightBrowEyeDistance,
                            0.008f
                        ),

                FaceBaselineFeature
                    .INNER_BROW_DISTANCE to
                        rawMetric(
                            participant
                                .innerBrowDistance,
                            0.006f
                        ),

                FaceBaselineFeature
                    .BROW_GEOMETRY_ASYMMETRY to
                        rawMetric(
                            abs(
                                participant
                                    .leftBrowEyeDistance -
                                        participant
                                            .rightBrowEyeDistance
                            ),
                            0.004f
                        )
            )

        val derivedMetrics =
            linkedMapOf(
                FaceBaselineFeature
                    .BLINK_RATE to
                        BaselineMetric(
                            median =
                                participant
                                    .normalBlinkRate,
                            mad =
                                2f,
                            sampleCount =
                                60
                        ),

                FaceBaselineFeature
                    .AVERAGE_BLINK_DURATION to
                        BaselineMetric(
                            median =
                                participant
                                    .normalBlinkDurationMs,
                            mad =
                                35f,
                            sampleCount =
                                60
                        ),

                FaceBaselineFeature
                    .FACIAL_ACTIVITY_LEVEL to
                        BaselineMetric(
                            median =
                                activityMedian,
                            mad =
                                0.005f,
                            sampleCount =
                                60
                        ),

                FaceBaselineFeature
                    .FACIAL_CHANGE_COUNT to
                        BaselineMetric(
                            median =
                                facialChangeMedian,
                            mad =
                                1f,
                            sampleCount =
                                60
                        )
            )

        return FaceBaseline(
            rawMetrics =
                rawMetrics,
            derivedMetrics =
                derivedMetrics
        )
    }

    private fun rawMetric(
        median: Float,
        mad: Float
    ): BaselineMetric {

        return BaselineMetric(
            median =
                median,
            mad =
                mad,
            sampleCount =
                20
        )
    }

    // ---------------------------------------------------------------------
    // CSV
    // ---------------------------------------------------------------------

    private fun writeAdaptationCsv(
        file: File,
        rows: List<AdaptationRow>
    ) {

        val header =
            listOf(
                "participantId",
                "usageIndex",
                "isCheckpoint",
                "updateProduced",
                "rawBaselineUnchanged",
                "allMetricsFinite",
                "rawMetricCount",
                "derivedMetricCount",
                "activityMedianBefore",
                "activityMedianAfter",
                "activityMadAfter",
                "activitySampleCountAfter",
                "estimatedActivityTarget",
                "distanceToActivityTarget",
                "distanceDidNotIncrease",
                "averageNormalScore",
                "normalScoreStandardDeviation",
                "positiveNormalWindowPercent",
                "maximumNormalLevel",
                "reliableNormalWindowCount"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(",")
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.participantId,
                        row.usageIndex,
                        row.isCheckpoint,
                        row.updateProduced,
                        row.rawBaselineUnchanged,
                        row.allMetricsFinite,
                        row.rawMetricCount,
                        row.derivedMetricCount,
                        row.activityMedianBefore,
                        row.activityMedianAfter,
                        row.activityMadAfter,
                        row.activitySampleCountAfter,
                        row.estimatedActivityTarget,
                        row.distanceToActivityTarget,
                        row.distanceDidNotIncrease,
                        row.averageNormalScore,
                        row.normalScoreStandardDeviation,
                        row.positiveNormalWindowPercent,
                        row.maximumNormalLevel,
                        row.reliableNormalWindowCount
                    ).toCsvLine()
                )
            }
        }
    }

    private fun writeSafetyCsv(
        file: File,
        rows: List<SafetyRow>
    ) {

        val header =
            listOf(
                "participantId",
                "checkedAfterUse",
                "finalNormalMaximumLevel",
                "distressMaximumLevel",
                "distressStillDetected",
                "distressUpdateBlocked",
                "invalidUpdateBlocked",
                "rawBaselineStillUnchanged",
                "allFinalMetricsFinite"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(",")
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.participantId,
                        row.checkedAfterUse,
                        row.finalNormalMaximumLevel,
                        row.distressMaximumLevel,
                        row.distressStillDetected,
                        row.distressUpdateBlocked,
                        row.invalidUpdateBlocked,
                        row.rawBaselineStillUnchanged,
                        row.allFinalMetricsFinite
                    ).toCsvLine()
                )
            }
        }
    }

    private fun buildSummary(
        rows: List<AdaptationRow>,
        safetyRows: List<SafetyRow>
    ): String {

        val totalUpdates =
            rows.size

        val updatesProduced =
            rows.count {
                it.updateProduced
            }

        val rawPreserved =
            rows.count {
                it.rawBaselineUnchanged
            }

        val finiteMetrics =
            rows.count {
                it.allMetricsFinite
            }

        val monotonicUpdates =
            rows.count {
                it.distanceDidNotIncrease
            }

        val normalLevelZero =
            rows.count {
                it.maximumNormalLevel ==
                        0
            }

        val finalSafetyPassed =
            safetyRows.count { row ->
                row.finalNormalMaximumLevel ==
                        0 &&
                        row.distressStillDetected &&
                        row.distressUpdateBlocked &&
                        row.invalidUpdateBlocked &&
                        row.rawBaselineStillUnchanged &&
                        row.allFinalMetricsFinite
            }

        val checkpointLines =
            REPORT_CHECKPOINTS
                .joinToString(
                    separator =
                        "\n"
                ) { checkpoint ->

                    val checkpointRows =
                        rows.filter {
                            it.usageIndex ==
                                    checkpoint
                        }

                    val averageDistance =
                        checkpointRows
                            .map {
                                it.distanceToActivityTarget
                            }
                            .average()

                    "Use $checkpoint average distance: " +
                            String.format(
                                Locale.US,
                                "%.10f",
                                averageDistance
                            )
                }

        return """
        Long-term synthetic facial baseline-adaptation test completed.

        Virtual participants: ${virtualParticipants().size}
        Accepted repeated uses per participant: $TOTAL_ACCEPTED_USES
        Total accepted updates: $totalUpdates

        Updates produced:
        $updatesProduced / $totalUpdates (${percentage(updatesProduced, totalUpdates)})

        Updates preserving all 15 raw metrics:
        $rawPreserved / $totalUpdates (${percentage(rawPreserved, totalUpdates)})

        Updates with finite valid metrics:
        $finiteMetrics / $totalUpdates (${percentage(finiteMetrics, totalUpdates)})

        Updates whose distance did not increase:
        $monotonicUpdates / $totalUpdates (${percentage(monotonicUpdates, totalUpdates)})

        Uses whose normal maximum level remained 0:
        $normalLevelZero / $totalUpdates (${percentage(normalLevelZero, totalUpdates)})

        Participants passing all final safety checks after 500 uses:
        $finalSafetyPassed / ${safetyRows.size} (${percentage(finalSafetyPassed, safetyRows.size)})

        Checkpoint convergence:
        $checkpointLines

        Interpretation note:
        This is a controlled engineering stress test of long-term
        adaptation. It does not establish clinical facial-distress
        accuracy or predict how many real user entries are required.
        """.trimIndent()
    }

    // ---------------------------------------------------------------------
    // Utilities and models
    // ---------------------------------------------------------------------

    private fun resolveOutputDirectory():
            File {

        val workingDirectory =
            File(
                System.getProperty(
                    "user.dir"
                )
            )

        val appDirectory =
            if (
                workingDirectory.name ==
                "app"
            ) {
                workingDirectory
            } else {
                File(
                    workingDirectory,
                    "app"
                )
            }

        return File(
            appDirectory,
            "build/synthetic-face-long-term-adaptation"
        ).apply {
            check(
                exists() ||
                        mkdirs()
            )
        }
    }

    private fun Float.unitValue():
            Float {

        return coerceIn(
            0f,
            1f
        )
    }

    private fun List<Float>.averageOrZero():
            Float {

        return if (
            isEmpty()
        ) {
            0f
        } else {
            average()
                .toFloat()
        }
    }

    private fun <T> List<T>.percentage(
        predicate: (T) -> Boolean
    ): Float {

        return if (
            isEmpty()
        ) {
            0f
        } else {
            count(
                predicate
            ) *
                    100f /
                    size.toFloat()
        }
    }

    private fun standardDeviation(
        values: List<Float>
    ): Float {

        if (
            values.isEmpty()
        ) {
            return 0f
        }

        val average =
            values.average()

        val variance =
            values
                .map { value ->
                    val difference =
                        value -
                                average

                    difference *
                            difference
                }
                .average()

        return sqrt(
            variance
        ).toFloat()
    }

    private fun List<Any?>.toCsvLine():
            String {

        return joinToString(
            separator =
                ","
        ) { value ->
            csvValue(
                value
            )
        }
    }

    private fun csvValue(
        value: Any?
    ): String {

        if (
            value ==
            null
        ) {
            return ""
        }

        val text =
            value.toString()

        return if (
            text.contains(",") ||
            text.contains("\"") ||
            text.contains("\n")
        ) {
            "\"" +
                    text.replace(
                        "\"",
                        "\"\""
                    ) +
                    "\""
        } else {
            text
        }
    }

    private fun percentage(
        numerator: Int,
        denominator: Int
    ): String {

        if (
            denominator ==
            0
        ) {
            return "0.00%"
        }

        return String.format(
            Locale.US,
            "%.2f%%",
            numerator *
                    100.0 /
                    denominator
        )
    }

    private enum class FrameMode {
        NORMAL,
        STRONG_EYE_SQUINT
    }

    private data class AdaptationRow(
        val participantId: String,
        val usageIndex: Int,
        val isCheckpoint: Boolean,
        val updateProduced: Boolean,
        val rawBaselineUnchanged: Boolean,
        val allMetricsFinite: Boolean,
        val rawMetricCount: Int,
        val derivedMetricCount: Int,
        val activityMedianBefore: Float,
        val activityMedianAfter: Float,
        val activityMadAfter: Float,
        val activitySampleCountAfter: Int,
        val estimatedActivityTarget: Float,
        val distanceToActivityTarget: Float,
        val distanceDidNotIncrease: Boolean,
        val averageNormalScore: Float,
        val normalScoreStandardDeviation: Float,
        val positiveNormalWindowPercent: Float,
        val maximumNormalLevel: Int,
        val reliableNormalWindowCount: Int
    )

    private data class SafetyRow(
        val participantId: String,
        val checkedAfterUse: Int,
        val finalNormalMaximumLevel: Int,
        val distressMaximumLevel: Int,
        val distressStillDetected: Boolean,
        val distressUpdateBlocked: Boolean,
        val invalidUpdateBlocked: Boolean,
        val rawBaselineStillUnchanged: Boolean,
        val allFinalMetricsFinite: Boolean
    )

    private data class VirtualParticipant(
        val id: String,
        val seed: Int,
        val eyeBlinkLeft: Float,
        val eyeBlinkRight: Float,
        val eyeSquintLeft: Float,
        val eyeSquintRight: Float,
        val eyeWideLeft: Float,
        val eyeWideRight: Float,
        val browDownLeft: Float,
        val browDownRight: Float,
        val browInnerUp: Float,
        val browOuterUpLeft: Float,
        val browOuterUpRight: Float,
        val leftBrowEyeDistance: Float,
        val rightBrowEyeDistance: Float,
        val innerBrowDistance: Float,
        val interEyeDistance: Float,
        val normalBlinkRate: Float,
        val normalBlinkDurationMs: Float
    )

    private fun virtualParticipants():
            List<VirtualParticipant> {

        return listOf(
            VirtualParticipant(
                id = "VIRTUAL_01",
                seed = 101,
                eyeBlinkLeft = 0.045f,
                eyeBlinkRight = 0.050f,
                eyeSquintLeft = 0.090f,
                eyeSquintRight = 0.100f,
                eyeWideLeft = 0.025f,
                eyeWideRight = 0.030f,
                browDownLeft = 0.040f,
                browDownRight = 0.045f,
                browInnerUp = 0.050f,
                browOuterUpLeft = 0.040f,
                browOuterUpRight = 0.045f,
                leftBrowEyeDistance = 0.350f,
                rightBrowEyeDistance = 0.345f,
                innerBrowDistance = 0.520f,
                interEyeDistance = 0.200f,
                normalBlinkRate = 12f,
                normalBlinkDurationMs = 180f
            ),

            VirtualParticipant(
                id = "VIRTUAL_02",
                seed = 202,
                eyeBlinkLeft = 0.090f,
                eyeBlinkRight = 0.085f,
                eyeSquintLeft = 0.170f,
                eyeSquintRight = 0.150f,
                eyeWideLeft = 0.015f,
                eyeWideRight = 0.020f,
                browDownLeft = 0.070f,
                browDownRight = 0.065f,
                browInnerUp = 0.080f,
                browOuterUpLeft = 0.070f,
                browOuterUpRight = 0.075f,
                leftBrowEyeDistance = 0.390f,
                rightBrowEyeDistance = 0.380f,
                innerBrowDistance = 0.560f,
                interEyeDistance = 0.180f,
                normalBlinkRate = 16f,
                normalBlinkDurationMs = 210f
            ),

            VirtualParticipant(
                id = "VIRTUAL_03",
                seed = 303,
                eyeBlinkLeft = 0.030f,
                eyeBlinkRight = 0.035f,
                eyeSquintLeft = 0.060f,
                eyeSquintRight = 0.065f,
                eyeWideLeft = 0.060f,
                eyeWideRight = 0.055f,
                browDownLeft = 0.025f,
                browDownRight = 0.030f,
                browInnerUp = 0.035f,
                browOuterUpLeft = 0.025f,
                browOuterUpRight = 0.030f,
                leftBrowEyeDistance = 0.320f,
                rightBrowEyeDistance = 0.335f,
                innerBrowDistance = 0.480f,
                interEyeDistance = 0.220f,
                normalBlinkRate = 10f,
                normalBlinkDurationMs = 165f
            ),

            VirtualParticipant(
                id = "VIRTUAL_04",
                seed = 404,
                eyeBlinkLeft = 0.120f,
                eyeBlinkRight = 0.110f,
                eyeSquintLeft = 0.220f,
                eyeSquintRight = 0.200f,
                eyeWideLeft = 0.010f,
                eyeWideRight = 0.012f,
                browDownLeft = 0.100f,
                browDownRight = 0.095f,
                browInnerUp = 0.110f,
                browOuterUpLeft = 0.100f,
                browOuterUpRight = 0.095f,
                leftBrowEyeDistance = 0.430f,
                rightBrowEyeDistance = 0.420f,
                innerBrowDistance = 0.610f,
                interEyeDistance = 0.160f,
                normalBlinkRate = 20f,
                normalBlinkDurationMs = 230f
            )
        )
    }

    companion object {

        private const val TOTAL_ACCEPTED_USES =
            500

        private val REPORT_CHECKPOINTS =
            setOf(
                1,
                2,
                5,
                10,
                25,
                50,
                100,
                250,
                500
            )

        private const val FRAME_INTERVAL_MS =
            67L

        /*
         * More than 60 clean completed windows are needed for
         * one learned derived-baseline update.
         */
        private const val LEARNING_WINDOW_TARGET =
            78

        private const val LEARNING_SEQUENCE_DURATION_MS =
            LEARNING_WINDOW_TARGET *
                    600L

        private const val FINAL_NORMAL_CHECK_DURATION_MS =
            12_000L

        private const val DISTRESS_CHECK_DURATION_MS =
            45_000L

        private const val INVALID_CHECK_DURATION_MS =
            45_000L

        private const val EXISTING_BASELINE_WEIGHT =
            0.80f

        private const val NEW_DATA_WEIGHT =
            0.20f

        private const val DISTANCE_TOLERANCE =
            0.00001f
    }
}