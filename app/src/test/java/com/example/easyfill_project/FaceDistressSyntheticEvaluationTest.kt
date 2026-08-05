package com.example.easyfill_project.face_analysis

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Complete synthetic evaluation of the facial-distress algorithm.
 *
 * This single test file covers:
 *
 * 1. Basic controlled facial scenarios.
 * 2. Robustness and misleading-input scenarios.
 * 3. Severity monotonicity.
 * 4. Recovery after an expression ends.
 * 5. Personal-baseline differences.
 * 6. Derived-baseline learning across repeated uses.
 * 7. Protection of the 15 raw baseline metrics.
 * 8. Rejection of distress and invalid frames from baseline learning.
 *
 * The test uses the real FaceDistressAnalyzer. It does not duplicate
 * the scoring rules and does not access Firebase or the camera.
 *
 * Generated files:
 *
 * app/build/synthetic-face-evaluation/
 * ├── synthetic_face_windows.csv
 * ├── synthetic_face_scenarios.csv
 * ├── synthetic_face_severity_sequences.csv
 * ├── synthetic_face_adaptation.csv
 * └── synthetic_face_summary.txt
 */
class FaceDistressSyntheticEvaluationTest {

    @Test
    fun generateCompleteSyntheticFaceEvaluationDataset() {

        val outputDirectory =
            File(
                "build/synthetic-face-evaluation"
            ).apply {
                mkdirs()
            }

        val windowRows =
            mutableListOf<WindowCsvRow>()

        val scenarioRows =
            mutableListOf<ScenarioCsvRow>()

        val severityRows =
            mutableListOf<SeveritySequenceCsvRow>()

        val adaptationRows =
            mutableListOf<AdaptationCsvRow>()

        val participants =
            createVirtualParticipants()

        participants.forEach { participant ->

            runBasicEvaluation(
                participant = participant,
                windowRows = windowRows,
                scenarioRows = scenarioRows
            )

            runRobustnessEvaluation(
                participant = participant,
                windowRows = windowRows,
                scenarioRows = scenarioRows
            )

            runSeverityEvaluation(
                participant = participant,
                windowRows = windowRows,
                scenarioRows = scenarioRows,
                severityRows = severityRows
            )

            runAdaptationEvaluation(
                participant = participant,
                windowRows = windowRows,
                adaptationRows = adaptationRows
            )
        }

        writeWindowCsv(
            file =
                File(
                    outputDirectory,
                    "synthetic_face_windows.csv"
                ),
            rows =
                windowRows
        )

        writeScenarioCsv(
            file =
                File(
                    outputDirectory,
                    "synthetic_face_scenarios.csv"
                ),
            rows =
                scenarioRows
        )

        writeSeverityCsv(
            file =
                File(
                    outputDirectory,
                    "synthetic_face_severity_sequences.csv"
                ),
            rows =
                severityRows
        )

        writeAdaptationCsv(
            file =
                File(
                    outputDirectory,
                    "synthetic_face_adaptation.csv"
                ),
            rows =
                adaptationRows
        )

        val summary =
            buildSummary(
                windowRows = windowRows,
                scenarioRows = scenarioRows,
                severityRows = severityRows,
                adaptationRows = adaptationRows
            )

        File(
            outputDirectory,
            "synthetic_face_summary.txt"
        ).writeText(summary)

        println(summary)
        println()
        println(
            "Output directory: " +
                    outputDirectory.absolutePath
        )

        /*
         * Structural assertions only.
         *
         * The generated measurements are intended for evaluation
         * in Jupyter. The test should not hide algorithm weaknesses
         * by failing before the CSV files can be inspected.
         */
        assertTrue(
            "No facial windows were generated.",
            windowRows.isNotEmpty()
        )

        assertTrue(
            "No scenario summaries were generated.",
            scenarioRows.isNotEmpty()
        )

        assertTrue(
            "The raw facial baseline changed during adaptation.",
            adaptationRows
                .filter {
                    it.recordType ==
                            ADAPTATION_UPDATE_RECORD
                }
                .all {
                    it.rawBaselineUnchanged == true
                }
        )

        assertTrue(
            "One or more output files were not created.",
            listOf(
                "synthetic_face_windows.csv",
                "synthetic_face_scenarios.csv",
                "synthetic_face_severity_sequences.csv",
                "synthetic_face_adaptation.csv",
                "synthetic_face_summary.txt"
            ).all { fileName ->
                File(
                    outputDirectory,
                    fileName
                ).exists()
            }
        )
    }

    // ---------------------------------------------------------------------
    // Basic evaluation
    // ---------------------------------------------------------------------

    private fun runBasicEvaluation(
        participant: VirtualParticipant,
        windowRows: MutableList<WindowCsvRow>,
        scenarioRows: MutableList<ScenarioCsvRow>
    ) {

        val scenarios =
            listOf(
                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.NORMAL_STABLE,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        10_000L,
                    amplitude =
                        0f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.BLINK_PATTERN,
                    expectedContributor =
                        FaceDistressContributor.BLINK_PATTERN,
                    expectedFinalPositive =
                        true,
                    expectedComponentActivation =
                        true,
                    activeDurationMs =
                        14_000L,
                    amplitude =
                        1f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.LONG_EYE_CLOSURE,
                    expectedContributor =
                        FaceDistressContributor.BLINK_PATTERN,
                    expectedFinalPositive =
                        true,
                    expectedComponentActivation =
                        true,
                    activeDurationMs =
                        4_000L,
                    amplitude =
                        1f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.EYE_SQUINT,
                    expectedContributor =
                        FaceDistressContributor.EYE_SQUINT,
                    expectedFinalPositive =
                        true,
                    expectedComponentActivation =
                        true,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.24f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.EYE_WIDE,
                    expectedContributor =
                        FaceDistressContributor.EYE_WIDE,
                    expectedFinalPositive =
                        true,
                    expectedComponentActivation =
                        true,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.11f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.BROW_FURROW_CONFIRMED,
                    expectedContributor =
                        FaceDistressContributor.BROW_FURROW,
                    expectedFinalPositive =
                        true,
                    expectedComponentActivation =
                        true,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.22f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.BROW_RAISE_CONFIRMED,
                    expectedContributor =
                        FaceDistressContributor.BROW_RAISE,
                    expectedFinalPositive =
                        true,
                    expectedComponentActivation =
                        true,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.22f
                ),

                /*
                 * Low activity is a weak supporting signal in the
                 * current weighted score. Therefore, the expected
                 * result is component activation rather than a
                 * guaranteed positive final level by itself.
                 */
                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.LOW_FACIAL_ACTIVITY,
                    expectedContributor =
                        FaceDistressContributor.LOW_FACIAL_ACTIVITY,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        true,
                    activeDurationMs =
                        10_000L,
                    amplitude =
                        1f,
                    useLowActivityBaseline =
                        true
                )
            )

        scenarios.forEach { configuration ->

            repeat(BASIC_REPETITIONS) { repetitionIndex ->

                val baseline =
                    if (
                        configuration
                            .useLowActivityBaseline
                    ) {
                        createBaseline(
                            participant =
                                participant,
                            activityMedian =
                                0.025f
                        )
                    } else {
                        createBaseline(
                            participant =
                                participant
                        )
                    }

                val run =
                    runScenario(
                        testGroup =
                            BASIC_GROUP,
                        participant =
                            participant,
                        baseline =
                            baseline,
                        configuration =
                            configuration,
                        repetition =
                            repetitionIndex + 1,
                        severityOrder =
                            null,
                        usageIndex =
                            null,
                        windowRows =
                            windowRows,
                        randomSeed =
                            participant.seed +
                                    repetitionIndex * 101 +
                                    configuration.scenario.ordinal
                    )

                scenarioRows.add(
                    run.toScenarioCsvRow()
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Robustness evaluation
    // ---------------------------------------------------------------------

    private fun runRobustnessEvaluation(
        participant: VirtualParticipant,
        windowRows: MutableList<WindowCsvRow>,
        scenarioRows: MutableList<ScenarioCsvRow>
    ) {

        val scenarios =
            listOf(
                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.SINGLE_EYE_SQUINT,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.28f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.BROW_BLENDSHAPE_ONLY,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.25f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.BROW_GEOMETRY_ONLY,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.16f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.SHORT_SQUINT,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        6_000L,
                    amplitude =
                        0.30f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.RANDOM_NON_PERSISTENT_MOVEMENT,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        10_000L,
                    amplitude =
                        0.05f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.GEOMETRY_ASYMMETRY_ONLY,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0.12f
                ),

                ScenarioConfiguration(
                    scenario =
                        SyntheticScenario.INVALID_GEOMETRY,
                    expectedContributor =
                        FaceDistressContributor.NONE,
                    expectedFinalPositive =
                        false,
                    expectedComponentActivation =
                        false,
                    activeDurationMs =
                        8_000L,
                    amplitude =
                        0f
                )
            )

        scenarios.forEach { configuration ->

            repeat(ROBUSTNESS_REPETITIONS) { repetitionIndex ->

                val run =
                    runScenario(
                        testGroup =
                            ROBUSTNESS_GROUP,
                        participant =
                            participant,
                        baseline =
                            createBaseline(
                                participant
                            ),
                        configuration =
                            configuration,
                        repetition =
                            repetitionIndex + 1,
                        severityOrder =
                            null,
                        usageIndex =
                            null,
                        windowRows =
                            windowRows,
                        randomSeed =
                            participant.seed +
                                    10_000 +
                                    repetitionIndex * 211 +
                                    configuration.scenario.ordinal
                    )

                scenarioRows.add(
                    run.toScenarioCsvRow()
                )
            }
        }

        repeat(ROBUSTNESS_REPETITIONS) { repetitionIndex ->

            val run =
                runMissingFaceRecoveryScenario(
                    participant =
                        participant,
                    repetition =
                        repetitionIndex + 1,
                    windowRows =
                        windowRows
                )

            scenarioRows.add(
                run.toScenarioCsvRow()
            )
        }
    }

    // ---------------------------------------------------------------------
    // Severity evaluation
    // ---------------------------------------------------------------------

    private fun runSeverityEvaluation(
        participant: VirtualParticipant,
        windowRows: MutableList<WindowCsvRow>,
        scenarioRows: MutableList<ScenarioCsvRow>,
        severityRows: MutableList<SeveritySequenceCsvRow>
    ) {

        val families =
            listOf(
                SeverityFamily(
                    familyName =
                        "EYE_SQUINT",
                    scenario =
                        SyntheticScenario.EYE_SQUINT,
                    expectedContributor =
                        FaceDistressContributor.EYE_SQUINT,
                    amplitudes =
                        listOf(
                            0.08f,
                            0.13f,
                            0.20f,
                            0.30f
                        ),
                    activeDurationsMs =
                        listOf(
                            8_000L,
                            8_000L,
                            8_000L,
                            8_000L
                        )
                ),

                SeverityFamily(
                    familyName =
                        "EYE_WIDE",
                    scenario =
                        SyntheticScenario.EYE_WIDE,
                    expectedContributor =
                        FaceDistressContributor.EYE_WIDE,
                    amplitudes =
                        listOf(
                            0.025f,
                            0.045f,
                            0.070f,
                            0.110f
                        ),
                    activeDurationsMs =
                        listOf(
                            8_000L,
                            8_000L,
                            8_000L,
                            8_000L
                        )
                ),

                SeverityFamily(
                    familyName =
                        "BROW_FURROW",
                    scenario =
                        SyntheticScenario.BROW_FURROW_CONFIRMED,
                    expectedContributor =
                        FaceDistressContributor.BROW_FURROW,
                    amplitudes =
                        listOf(
                            0.06f,
                            0.10f,
                            0.16f,
                            0.24f
                        ),
                    activeDurationsMs =
                        listOf(
                            8_000L,
                            8_000L,
                            8_000L,
                            8_000L
                        )
                ),

                SeverityFamily(
                    familyName =
                        "BROW_RAISE",
                    scenario =
                        SyntheticScenario.BROW_RAISE_CONFIRMED,
                    expectedContributor =
                        FaceDistressContributor.BROW_RAISE,
                    amplitudes =
                        listOf(
                            0.06f,
                            0.10f,
                            0.16f,
                            0.24f
                        ),
                    activeDurationsMs =
                        listOf(
                            8_000L,
                            8_000L,
                            8_000L,
                            8_000L
                        )
                ),

                SeverityFamily(
                    familyName =
                        "LONG_EYE_CLOSURE",
                    scenario =
                        SyntheticScenario.TIMED_EYE_CLOSURE,
                    expectedContributor =
                        FaceDistressContributor.BLINK_PATTERN,
                    amplitudes =
                        listOf(
                            1f,
                            1f,
                            1f,
                            1f
                        ),
                    activeDurationsMs =
                        listOf(
                            900L,
                            1_300L,
                            1_700L,
                            2_300L
                        )
                ),

                SeverityFamily(
                    familyName =
                        "LOW_FACIAL_ACTIVITY",
                    scenario =
                        SyntheticScenario.LOW_FACIAL_ACTIVITY,
                    expectedContributor =
                        FaceDistressContributor.LOW_FACIAL_ACTIVITY,
                    amplitudes =
                        listOf(
                            1f,
                            1f,
                            1f,
                            1f
                        ),
                    activeDurationsMs =
                        listOf(
                            3_500L,
                            5_500L,
                            7_000L,
                            9_000L
                        ),
                    useLowActivityBaseline =
                        true
                )
            )

        families.forEach { family ->

            repeat(SEVERITY_REPETITIONS) { repetitionIndex ->

                val runs =
                    mutableListOf<ScenarioRunResult>()

                family.amplitudes.indices.forEach { index ->

                    val configuration =
                        ScenarioConfiguration(
                            scenario =
                                family.scenario,
                            expectedContributor =
                                family.expectedContributor,
                            expectedFinalPositive =
                                family.expectedContributor !=
                                        FaceDistressContributor
                                            .LOW_FACIAL_ACTIVITY,
                            expectedComponentActivation =
                                true,
                            activeDurationMs =
                                family.activeDurationsMs[index],
                            amplitude =
                                family.amplitudes[index],
                            useLowActivityBaseline =
                                family.useLowActivityBaseline
                        )

                    val baseline =
                        if (
                            family
                                .useLowActivityBaseline
                        ) {
                            createBaseline(
                                participant =
                                    participant,
                                activityMedian =
                                    0.025f
                            )
                        } else {
                            createBaseline(
                                participant
                            )
                        }

                    val run =
                        runScenario(
                            testGroup =
                                SEVERITY_GROUP,
                            participant =
                                participant,
                            baseline =
                                baseline,
                            configuration =
                                configuration,
                            repetition =
                                repetitionIndex + 1,
                            severityOrder =
                                index + 1,
                            usageIndex =
                                null,
                            windowRows =
                                windowRows,
                            randomSeed =
                                participant.seed +
                                        20_000 +
                                        repetitionIndex * 307 +
                                        family.familyName.hashCode() +
                                        index
                        )

                    runs.add(run)
                    scenarioRows.add(
                        run.toScenarioCsvRow(
                            severityFamily =
                                family.familyName
                        )
                    )
                }

                val componentValues =
                    runs.map {
                        it.maximumExpectedComponentScore
                    }

                val finalScoreValues =
                    runs.map {
                        it.maximumScore
                    }

                val levelValues =
                    runs.map {
                        it.maximumLevel.toFloat()
                    }

                severityRows.add(
                    SeveritySequenceCsvRow(
                        participantId =
                            participant.id,
                        family =
                            family.familyName,
                        repetition =
                            repetitionIndex + 1,

                        weakComponentScore =
                            componentValues[0],
                        moderateComponentScore =
                            componentValues[1],
                        strongComponentScore =
                            componentValues[2],
                        veryStrongComponentScore =
                            componentValues[3],

                        weakFinalScore =
                            finalScoreValues[0],
                        moderateFinalScore =
                            finalScoreValues[1],
                        strongFinalScore =
                            finalScoreValues[2],
                        veryStrongFinalScore =
                            finalScoreValues[3],

                        weakLevel =
                            runs[0].maximumLevel,
                        moderateLevel =
                            runs[1].maximumLevel,
                        strongLevel =
                            runs[2].maximumLevel,
                        veryStrongLevel =
                            runs[3].maximumLevel,

                        componentMonotonic =
                            isNonDecreasing(
                                componentValues
                            ),

                        finalScoreMonotonic =
                            isNonDecreasing(
                                finalScoreValues
                            ),

                        levelMonotonic =
                            isNonDecreasing(
                                levelValues
                            ),

                        componentIncreasedAtLeastOnce =
                            increasedAtLeastOnce(
                                componentValues
                            ),

                        finalScoreIncreasedAtLeastOnce =
                            increasedAtLeastOnce(
                                finalScoreValues
                            )
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Derived-baseline adaptation evaluation
    // ---------------------------------------------------------------------

    private fun runAdaptationEvaluation(
        participant: VirtualParticipant,
        windowRows: MutableList<WindowCsvRow>,
        adaptationRows: MutableList<AdaptationCsvRow>
    ) {

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

        var previousActivityDistance: Float? =
            null

        repeat(ADAPTATION_USE_COUNT) { useIndex ->

            val beforeMetric =
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

            val results =
                feedLearningSequence(
                    analyzer =
                        analyzer,
                    participant =
                        participant,
                    usageIndex =
                        useIndex + 1,
                    windowRows =
                        windowRows
                )

            val updatedBaseline =
                analyzer
                    .consumePendingBaselineUpdate()

            val updateProduced =
                updatedBaseline != null

            val baselineAfter =
                updatedBaseline
                    ?: currentBaseline

            val afterMetric =
                baselineAfter
                    .derivedMetrics[
                    FaceBaselineFeature
                        .FACIAL_ACTIVITY_LEVEL
                ]

            if (
                estimatedActivityTarget == null &&
                updatedBaseline != null &&
                afterMetric != null
            ) {
                estimatedActivityTarget =
                    (
                            afterMetric.median -
                                    beforeMetric.median *
                                    EXISTING_WEIGHT_FOR_REPORT
                            ) /
                            NEW_WEIGHT_FOR_REPORT
            }

            val activityTarget =
                estimatedActivityTarget

            val activityDistance =
                if (
                    activityTarget != null &&
                    afterMetric != null
                ) {
                    abs(
                        afterMetric.median -
                                activityTarget
                    )
                } else {
                    null
                }

            val distanceDidNotIncrease =
                if (
                    previousActivityDistance != null &&
                    activityDistance != null
                ) {
                    activityDistance <=
                            previousActivityDistance!! +
                            MONOTONIC_TOLERANCE
                } else {
                    null
                }

            val reliableResults =
                results.filter {
                    it.isReliable
                }

            val averageScore =
                reliableResults
                    .map {
                        it.score
                    }
                    .averageOrZero()

            val scoreStandardDeviation =
                standardDeviation(
                    reliableResults.map {
                        it.score
                    }
                )

            val positivePercent =
                reliableResults
                    .percentage {
                        it.level > 0
                    }

            val rawBaselineUnchanged =
                baselineAfter.rawMetrics ==
                        originalBaseline.rawMetrics

            adaptationRows.add(
                AdaptationCsvRow(
                    participantId =
                        participant.id,
                    recordType =
                        ADAPTATION_UPDATE_RECORD,
                    usageIndex =
                        useIndex + 1,
                    updateProduced =
                        updateProduced,
                    rawBaselineUnchanged =
                        rawBaselineUnchanged,
                    derivedMetricCount =
                        baselineAfter
                            .derivedMetrics
                            .size,
                    activityMedianBefore =
                        beforeMetric.median,
                    activityMedianAfter =
                        afterMetric?.median,
                    estimatedActivityTarget =
                        activityTarget,
                    distanceToActivityTarget =
                        activityDistance,
                    distanceDidNotIncrease =
                        distanceDidNotIncrease,
                    averageNormalScore =
                        averageScore,
                    normalScoreStandardDeviation =
                        scoreStandardDeviation,
                    positiveNormalWindowPercent =
                        positivePercent,
                    distressUpdateBlocked =
                        null,
                    invalidFrameUpdateBlocked =
                        null
                )
            )

            if (activityDistance != null) {
                previousActivityDistance =
                    activityDistance
            }

            currentBaseline =
                baselineAfter
        }

        /*
         * A strong distress sequence must not be learned.
         */
        val distressAnalyzer =
            FaceDistressAnalyzer(
                currentBaseline
            )

        feedFramesWithoutPrelude(
            analyzer =
                distressAnalyzer,
            participant =
                participant,
            scenario =
                SyntheticScenario.EYE_SQUINT,
            durationMs =
                45_000L,
            amplitude =
                0.35f,
            startTimestampMs =
                9_000_000L +
                        participant.seed,
            randomSeed =
                participant.seed +
                        30_000
        )

        val distressUpdate =
            distressAnalyzer
                .consumePendingBaselineUpdate()

        adaptationRows.add(
            AdaptationCsvRow(
                participantId =
                    participant.id,
                recordType =
                    DISTRESS_BLOCK_RECORD,
                usageIndex =
                    null,
                updateProduced =
                    distressUpdate != null,
                rawBaselineUnchanged =
                    distressUpdate
                        ?.rawMetrics
                        ?.equals(
                            originalBaseline.rawMetrics
                        )
                        ?: true,
                derivedMetricCount =
                    distressUpdate
                        ?.derivedMetrics
                        ?.size
                        ?: currentBaseline
                            .derivedMetrics
                            .size,
                activityMedianBefore =
                    currentBaseline
                        .derivedMetrics[
                        FaceBaselineFeature
                            .FACIAL_ACTIVITY_LEVEL
                    ]
                        ?.median,
                activityMedianAfter =
                    distressUpdate
                        ?.derivedMetrics
                        ?.get(
                            FaceBaselineFeature
                                .FACIAL_ACTIVITY_LEVEL
                        )
                        ?.median,
                estimatedActivityTarget =
                    estimatedActivityTarget,
                distanceToActivityTarget =
                    null,
                distanceDidNotIncrease =
                    null,
                averageNormalScore =
                    null,
                normalScoreStandardDeviation =
                    null,
                positiveNormalWindowPercent =
                    null,
                distressUpdateBlocked =
                    distressUpdate == null,
                invalidFrameUpdateBlocked =
                    null
            )
        )

        /*
         * Invalid geometry must be rejected before learning.
         */
        val invalidAnalyzer =
            FaceDistressAnalyzer(
                currentBaseline
            )

        var timestamp =
            12_000_000L +
                    participant.seed

        repeat(INVALID_FRAME_COUNT) { frameIndex ->

            invalidAnalyzer.addFrame(
                createFrame(
                    participant =
                        participant,
                    timestampMs =
                        timestamp,
                    frameIndex =
                        frameIndex,
                    scenario =
                        SyntheticScenario.INVALID_GEOMETRY,
                    phase =
                        SequencePhase.ACTIVE,
                    activeElapsedMs =
                        frameIndex *
                                FRAME_INTERVAL_MS,
                    activeDurationMs =
                        INVALID_FRAME_COUNT *
                                FRAME_INTERVAL_MS,
                    amplitude =
                        0f,
                    random =
                        Random(
                            participant.seed +
                                    frameIndex
                        )
                )
            )

            timestamp +=
                FRAME_INTERVAL_MS
        }

        val invalidUpdate =
            invalidAnalyzer
                .consumePendingBaselineUpdate()

        adaptationRows.add(
            AdaptationCsvRow(
                participantId =
                    participant.id,
                recordType =
                    INVALID_BLOCK_RECORD,
                usageIndex =
                    null,
                updateProduced =
                    invalidUpdate != null,
                rawBaselineUnchanged =
                    invalidUpdate
                        ?.rawMetrics
                        ?.equals(
                            originalBaseline.rawMetrics
                        )
                        ?: true,
                derivedMetricCount =
                    invalidUpdate
                        ?.derivedMetrics
                        ?.size
                        ?: currentBaseline
                            .derivedMetrics
                            .size,
                activityMedianBefore =
                    currentBaseline
                        .derivedMetrics[
                        FaceBaselineFeature
                            .FACIAL_ACTIVITY_LEVEL
                    ]
                        ?.median,
                activityMedianAfter =
                    invalidUpdate
                        ?.derivedMetrics
                        ?.get(
                            FaceBaselineFeature
                                .FACIAL_ACTIVITY_LEVEL
                        )
                        ?.median,
                estimatedActivityTarget =
                    estimatedActivityTarget,
                distanceToActivityTarget =
                    null,
                distanceDidNotIncrease =
                    null,
                averageNormalScore =
                    null,
                normalScoreStandardDeviation =
                    null,
                positiveNormalWindowPercent =
                    null,
                distressUpdateBlocked =
                    null,
                invalidFrameUpdateBlocked =
                    invalidUpdate == null
            )
        )
    }

    // ---------------------------------------------------------------------
    // Scenario execution
    // ---------------------------------------------------------------------

    private fun runScenario(
        testGroup: String,
        participant: VirtualParticipant,
        baseline: FaceBaseline,
        configuration: ScenarioConfiguration,
        repetition: Int,
        severityOrder: Int?,
        usageIndex: Int?,
        windowRows: MutableList<WindowCsvRow>,
        randomSeed: Int
    ): ScenarioRunResult {

        val analyzer =
            FaceDistressAnalyzer(
                baseline
            )

        val random =
            Random(
                randomSeed
            )

        val results =
            mutableListOf<
                    ResultWithPhase
                    >()

        val totalDurationMs =
            PRELUDE_DURATION_MS +
                    configuration
                        .activeDurationMs +
                    RECOVERY_DURATION_MS

        var elapsedMs =
            0L

        var frameIndex =
            0

        while (
            elapsedMs <=
            totalDurationMs
        ) {

            val phase =
                when {
                    elapsedMs <
                            PRELUDE_DURATION_MS ->
                        SequencePhase.PRELUDE

                    elapsedMs <
                            PRELUDE_DURATION_MS +
                            configuration
                                .activeDurationMs ->
                        SequencePhase.ACTIVE

                    else ->
                        SequencePhase.RECOVERY
                }

            val activeElapsedMs =
                (
                        elapsedMs -
                                PRELUDE_DURATION_MS
                        ).coerceAtLeast(0L)

            val frame =
                createFrame(
                    participant =
                        participant,
                    timestampMs =
                        elapsedMs,
                    frameIndex =
                        frameIndex,
                    scenario =
                        configuration.scenario,
                    phase =
                        phase,
                    activeElapsedMs =
                        activeElapsedMs,
                    activeDurationMs =
                        configuration
                            .activeDurationMs,
                    amplitude =
                        configuration.amplitude,
                    random =
                        random
                )

            val result =
                analyzer.addFrame(
                    frame
                )

            if (result != null) {

                val resultPhase =
                    phaseForTimestamp(
                        windowEndTimestampMs =
                            result
                                .windowEndTimestampMs,
                        activeDurationMs =
                            configuration
                                .activeDurationMs
                    )

                results.add(
                    ResultWithPhase(
                        phase =
                            resultPhase,
                        result =
                            result
                    )
                )

                windowRows.add(
                    result.toWindowCsvRow(
                        testGroup =
                            testGroup,
                        participantId =
                            participant.id,
                        scenario =
                            configuration
                                .scenario
                                .name,
                        repetition =
                            repetition,
                        severityOrder =
                            severityOrder,
                        usageIndex =
                            usageIndex,
                        phase =
                            resultPhase,
                        expectedContributor =
                            configuration
                                .expectedContributor,
                        expectedFinalPositive =
                            configuration
                                .expectedFinalPositive
                    )
                )
            }

            elapsedMs +=
                FRAME_INTERVAL_MS

            frameIndex +=
                1
        }

        return summarizeScenarioRun(
            testGroup =
                testGroup,
            participant =
                participant,
            configuration =
                configuration,
            repetition =
                repetition,
            severityOrder =
                severityOrder,
            results =
                results
        )
    }

    private fun runMissingFaceRecoveryScenario(
        participant: VirtualParticipant,
        repetition: Int,
        windowRows: MutableList<WindowCsvRow>
    ): ScenarioRunResult {

        val baseline =
            createBaseline(
                participant
            )

        val analyzer =
            FaceDistressAnalyzer(
                baseline
            )

        val results =
            mutableListOf<
                    ResultWithPhase
                    >()

        var timestamp =
            0L

        var frameIndex =
            0

        /*
         * Build a reliable strong squint state first.
         */
        repeat(
            framesForDuration(
                PRELUDE_DURATION_MS
            )
        ) {

            val result =
                analyzer.addFrame(
                    createFrame(
                        participant =
                            participant,
                        timestampMs =
                            timestamp,
                        frameIndex =
                            frameIndex,
                        scenario =
                            SyntheticScenario
                                .NORMAL_STABLE,
                        phase =
                            SequencePhase.PRELUDE,
                        activeElapsedMs =
                            0L,
                        activeDurationMs =
                            PRELUDE_DURATION_MS,
                        amplitude =
                            0f,
                        random =
                            Random(
                                participant.seed +
                                        frameIndex
                            )
                    )
                )

            if (result != null) {
                results.add(
                    ResultWithPhase(
                        SequencePhase.PRELUDE,
                        result
                    )
                )

                windowRows.add(
                    result.toWindowCsvRow(
                        testGroup =
                            ROBUSTNESS_GROUP,
                        participantId =
                            participant.id,
                        scenario =
                            SyntheticScenario
                                .MISSING_FACE_RECOVERY
                                .name,
                        repetition =
                            repetition,
                        severityOrder =
                            null,
                        usageIndex =
                            null,
                        phase =
                            SequencePhase.PRELUDE,
                        expectedContributor =
                            FaceDistressContributor.NONE,
                        expectedFinalPositive =
                            false
                    )
                )
            }

            timestamp +=
                FRAME_INTERVAL_MS

            frameIndex +=
                1
        }

        val activeStart =
            timestamp

        repeat(
            framesForDuration(
                6_000L
            )
        ) {

            val result =
                analyzer.addFrame(
                    createFrame(
                        participant =
                            participant,
                        timestampMs =
                            timestamp,
                        frameIndex =
                            frameIndex,
                        scenario =
                            SyntheticScenario
                                .EYE_SQUINT,
                        phase =
                            SequencePhase.ACTIVE,
                        activeElapsedMs =
                            timestamp -
                                    activeStart,
                        activeDurationMs =
                            6_000L,
                        amplitude =
                            0.32f,
                        random =
                            Random(
                                participant.seed +
                                        40_000 +
                                        frameIndex
                            )
                    )
                )

            if (result != null) {

                results.add(
                    ResultWithPhase(
                        SequencePhase.ACTIVE,
                        result
                    )
                )

                windowRows.add(
                    result.toWindowCsvRow(
                        testGroup =
                            ROBUSTNESS_GROUP,
                        participantId =
                            participant.id,
                        scenario =
                            SyntheticScenario
                                .MISSING_FACE_RECOVERY
                                .name,
                        repetition =
                            repetition,
                        severityOrder =
                            null,
                        usageIndex =
                            null,
                        phase =
                            SequencePhase.ACTIVE,
                        expectedContributor =
                            FaceDistressContributor.EYE_SQUINT,
                        expectedFinalPositive =
                            true
                    )
                )
            }

            timestamp +=
                FRAME_INTERVAL_MS

            frameIndex +=
                1
        }

        /*
         * Simulate a missing face for longer than the analyzer's
         * maximum accepted frame gap.
         */
        timestamp +=
            2_000L

        analyzer.onFaceMissing(
            timestamp
        )

        val recoveryStart =
            timestamp

        repeat(
            framesForDuration(
                RECOVERY_DURATION_MS
            )
        ) {

            val result =
                analyzer.addFrame(
                    createFrame(
                        participant =
                            participant,
                        timestampMs =
                            timestamp,
                        frameIndex =
                            frameIndex,
                        scenario =
                            SyntheticScenario
                                .NORMAL_STABLE,
                        phase =
                            SequencePhase.RECOVERY,
                        activeElapsedMs =
                            timestamp -
                                    recoveryStart,
                        activeDurationMs =
                            RECOVERY_DURATION_MS,
                        amplitude =
                            0f,
                        random =
                            Random(
                                participant.seed +
                                        50_000 +
                                        frameIndex
                            )
                    )
                )

            if (result != null) {

                results.add(
                    ResultWithPhase(
                        SequencePhase.RECOVERY,
                        result
                    )
                )

                windowRows.add(
                    result.toWindowCsvRow(
                        testGroup =
                            ROBUSTNESS_GROUP,
                        participantId =
                            participant.id,
                        scenario =
                            SyntheticScenario
                                .MISSING_FACE_RECOVERY
                                .name,
                        repetition =
                            repetition,
                        severityOrder =
                            null,
                        usageIndex =
                            null,
                        phase =
                            SequencePhase.RECOVERY,
                        expectedContributor =
                            FaceDistressContributor.NONE,
                        expectedFinalPositive =
                            false
                    )
                )
            }

            timestamp +=
                FRAME_INTERVAL_MS

            frameIndex +=
                1
        }

        val configuration =
            ScenarioConfiguration(
                scenario =
                    SyntheticScenario
                        .MISSING_FACE_RECOVERY,
                expectedContributor =
                    FaceDistressContributor.NONE,
                expectedFinalPositive =
                    false,
                expectedComponentActivation =
                    false,
                activeDurationMs =
                    6_000L,
                amplitude =
                    0.32f
            )

        return summarizeScenarioRun(
            testGroup =
                ROBUSTNESS_GROUP,
            participant =
                participant,
            configuration =
                configuration,
            repetition =
                repetition,
            severityOrder =
                null,
            results =
                results
        )
    }

    private fun summarizeScenarioRun(
        testGroup: String,
        participant: VirtualParticipant,
        configuration: ScenarioConfiguration,
        repetition: Int,
        severityOrder: Int?,
        results: List<ResultWithPhase>
    ): ScenarioRunResult {

        val activeResults =
            results
                .filter {
                    it.phase ==
                            SequencePhase.ACTIVE
                }
                .map {
                    it.result
                }
                .filter {
                    it.isReliable
                }

        val recoveryResults =
            results
                .filter {
                    it.phase ==
                            SequencePhase.RECOVERY
                }
                .map {
                    it.result
                }
                .filter {
                    it.isReliable
                }

        val positiveWindowPercent =
            activeResults
                .percentage {
                    it.level > 0
                }

        val contributorMatchPercent =
            if (
                configuration
                    .expectedContributor ==
                FaceDistressContributor.NONE
            ) {
                activeResults
                    .percentage {
                        it.topContributor ==
                                FaceDistressContributor.NONE
                    }
            } else {
                activeResults
                    .percentage {
                        it.topContributor ==
                                configuration
                                    .expectedContributor
                    }
            }

        val componentActivationPercent =
            if (
                configuration
                    .expectedContributor ==
                FaceDistressContributor.NONE
            ) {
                activeResults
                    .percentage {
                        it.peakFeatureScore <=
                                COMPONENT_ACTIVE_THRESHOLD
                    }
            } else {
                activeResults
                    .percentage {
                        componentScore(
                            result =
                                it,
                            contributor =
                                configuration
                                    .expectedContributor
                        ) >
                                COMPONENT_ACTIVE_THRESHOLD
                    }
            }

        val maximumExpectedComponentScore =
            activeResults
                .maxOfOrNull {
                    componentScore(
                        result =
                            it,
                        contributor =
                            configuration
                                .expectedContributor
                    )
                }
                ?: 0f

        val maximumScore =
            activeResults
                .maxOfOrNull {
                    it.score
                }
                ?: 0f

        val maximumLevel =
            activeResults
                .maxOfOrNull {
                    it.level
                }
                ?: 0

        val recoveryTail =
            recoveryResults
                .takeLast(
                    RECOVERY_TAIL_WINDOW_COUNT
                )

        val recoveredToZero =
            recoveryTail.isNotEmpty() &&
                    recoveryTail.all {
                        it.level == 0
                    }

        return ScenarioRunResult(
            testGroup =
                testGroup,
            participantId =
                participant.id,
            scenario =
                configuration
                    .scenario
                    .name,
            repetition =
                repetition,
            severityOrder =
                severityOrder,
            expectedContributor =
                configuration
                    .expectedContributor,
            expectedFinalPositive =
                configuration
                    .expectedFinalPositive,
            expectedComponentActivation =
                configuration
                    .expectedComponentActivation,
            activeReliableWindowCount =
                activeResults.size,
            positiveWindowPercent =
                positiveWindowPercent,
            contributorMatchPercent =
                contributorMatchPercent,
            componentActivationPercent =
                componentActivationPercent,
            averageScore =
                activeResults
                    .map {
                        it.score
                    }
                    .averageOrZero(),
            maximumScore =
                maximumScore,
            maximumLevel =
                maximumLevel,
            averageEyesScore =
                activeResults
                    .map {
                        it.eyesScore
                    }
                    .averageOrZero(),
            averageBrowsScore =
                activeResults
                    .map {
                        it.browsScore
                    }
                    .averageOrZero(),
            averageActivityScore =
                activeResults
                    .map {
                        it.activityScore
                    }
                    .averageOrZero(),
            maximumExpectedComponentScore =
                maximumExpectedComponentScore,
            recoveredToZero =
                recoveredToZero
        )
    }

    // ---------------------------------------------------------------------
    // Learning sequence
    // ---------------------------------------------------------------------

    private fun feedLearningSequence(
        analyzer: FaceDistressAnalyzer,
        participant: VirtualParticipant,
        usageIndex: Int,
        windowRows: MutableList<WindowCsvRow>
    ): List<FaceDistressResult> {

        val results =
            mutableListOf<
                    FaceDistressResult
                    >()

        val durationMs =
            LEARNING_WINDOW_TARGET *
                    600L

        var timestamp =
            usageIndex *
                    1_000_000L +
                    participant.seed

        var frameIndex =
            0

        val random =
            Random(
                participant.seed +
                        usageIndex * 701
            )

        repeat(
            framesForDuration(
                durationMs
            )
        ) {

            val result =
                analyzer.addFrame(
                    createFrame(
                        participant =
                            participant,
                        timestampMs =
                            timestamp,
                        frameIndex =
                            frameIndex,
                        scenario =
                            SyntheticScenario
                                .NORMAL_STABLE,
                        phase =
                            SequencePhase.ACTIVE,
                        activeElapsedMs =
                            frameIndex *
                                    FRAME_INTERVAL_MS,
                        activeDurationMs =
                            durationMs,
                        amplitude =
                            0f,
                        random =
                            random
                    )
                )

            if (result != null) {

                results.add(result)

                windowRows.add(
                    result.toWindowCsvRow(
                        testGroup =
                            ADAPTATION_GROUP,
                        participantId =
                            participant.id,
                        scenario =
                            "NORMAL_LEARNING",
                        repetition =
                            1,
                        severityOrder =
                            null,
                        usageIndex =
                            usageIndex,
                        phase =
                            SequencePhase.ACTIVE,
                        expectedContributor =
                            FaceDistressContributor.NONE,
                        expectedFinalPositive =
                            false
                    )
                )
            }

            timestamp +=
                FRAME_INTERVAL_MS

            frameIndex +=
                1
        }

        return results
    }

    private fun feedFramesWithoutPrelude(
        analyzer: FaceDistressAnalyzer,
        participant: VirtualParticipant,
        scenario: SyntheticScenario,
        durationMs: Long,
        amplitude: Float,
        startTimestampMs: Long,
        randomSeed: Int
    ) {

        var timestamp =
            startTimestampMs

        val random =
            Random(
                randomSeed
            )

        repeat(
            framesForDuration(
                durationMs
            )
        ) { frameIndex ->

            analyzer.addFrame(
                createFrame(
                    participant =
                        participant,
                    timestampMs =
                        timestamp,
                    frameIndex =
                        frameIndex,
                    scenario =
                        scenario,
                    phase =
                        SequencePhase.ACTIVE,
                    activeElapsedMs =
                        frameIndex *
                                FRAME_INTERVAL_MS,
                    activeDurationMs =
                        durationMs,
                    amplitude =
                        amplitude,
                    random =
                        random
                )
            )

            timestamp +=
                FRAME_INTERVAL_MS
        }
    }

    // ---------------------------------------------------------------------
    // Synthetic frame creation
    // ---------------------------------------------------------------------

    private fun createFrame(
        participant: VirtualParticipant,
        timestampMs: Long,
        frameIndex: Int,
        scenario: SyntheticScenario,
        phase: SequencePhase,
        activeElapsedMs: Long,
        activeDurationMs: Long,
        amplitude: Float,
        random: Random
    ): FaceFrameData {

        val isActive =
            phase ==
                    SequencePhase.ACTIVE

        val useStableFrame =
            isActive &&
                    scenario ==
                    SyntheticScenario
                        .LOW_FACIAL_ACTIVITY

        val jitter =
            if (useStableFrame) {
                0f
            } else {
                deterministicJitter(
                    frameIndex =
                        frameIndex,
                    phaseShift =
                        participant.seed
                )
            }

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

        var eyeWideLeft =
            participant.eyeWideLeft +
                    jitter * 0.30f

        var eyeWideRight =
            participant.eyeWideRight -
                    jitter * 0.25f

        var browDownLeft =
            participant.browDownLeft +
                    jitter * 0.40f

        var browDownRight =
            participant.browDownRight -
                    jitter * 0.35f

        var browInnerUp =
            participant.browInnerUp +
                    jitter * 0.30f

        var browOuterUpLeft =
            participant.browOuterUpLeft +
                    jitter * 0.35f

        var browOuterUpRight =
            participant.browOuterUpRight -
                    jitter * 0.30f

        var leftBrowEyeDistance =
            participant
                .leftBrowEyeDistance +
                    jitter * 0.10f

        var rightBrowEyeDistance =
            participant
                .rightBrowEyeDistance -
                    jitter * 0.10f

        var innerBrowDistance =
            participant
                .innerBrowDistance +
                    jitter * 0.08f

        var geometryReliable =
            true

        if (isActive) {

            when (scenario) {

                SyntheticScenario.NORMAL_STABLE,
                SyntheticScenario.MISSING_FACE_RECOVERY ->
                    Unit

                SyntheticScenario.BLINK_PATTERN -> {

                    val blinkActive =
                        activeElapsedMs %
                                BLINK_CYCLE_MS <
                                BLINK_CLOSED_DURATION_MS

                    if (blinkActive) {
                        eyeBlinkLeft =
                            0.92f

                        eyeBlinkRight =
                            0.92f
                    }
                }

                SyntheticScenario.LONG_EYE_CLOSURE -> {

                    eyeBlinkLeft =
                        0.95f

                    eyeBlinkRight =
                        0.95f
                }

                SyntheticScenario.TIMED_EYE_CLOSURE -> {

                    val closureActive =
                        activeElapsedMs <
                                activeDurationMs

                    if (closureActive) {
                        eyeBlinkLeft =
                            0.95f

                        eyeBlinkRight =
                            0.95f
                    }
                }

                SyntheticScenario.EYE_SQUINT -> {

                    eyeSquintLeft +=
                        amplitude

                    eyeSquintRight +=
                        amplitude
                }

                SyntheticScenario.EYE_WIDE -> {

                    eyeWideLeft +=
                        amplitude

                    eyeWideRight +=
                        amplitude
                }

                SyntheticScenario
                    .BROW_FURROW_CONFIRMED -> {

                    browDownLeft +=
                        amplitude

                    browDownRight +=
                        amplitude

                    val geometryChange =
                        amplitude *
                                0.72f

                    leftBrowEyeDistance -=
                        geometryChange

                    rightBrowEyeDistance -=
                        geometryChange

                    innerBrowDistance -=
                        amplitude *
                                0.50f
                }

                SyntheticScenario
                    .BROW_RAISE_CONFIRMED -> {

                    browInnerUp +=
                        amplitude

                    browOuterUpLeft +=
                        amplitude

                    browOuterUpRight +=
                        amplitude

                    val geometryChange =
                        amplitude *
                                0.72f

                    leftBrowEyeDistance +=
                        geometryChange

                    rightBrowEyeDistance +=
                        geometryChange
                }

                SyntheticScenario
                    .LOW_FACIAL_ACTIVITY ->
                    Unit

                SyntheticScenario
                    .SINGLE_EYE_SQUINT -> {

                    eyeSquintLeft +=
                        amplitude
                }

                SyntheticScenario
                    .BROW_BLENDSHAPE_ONLY -> {

                    browDownLeft +=
                        amplitude

                    browDownRight +=
                        amplitude
                }

                SyntheticScenario
                    .BROW_GEOMETRY_ONLY -> {

                    leftBrowEyeDistance -=
                        amplitude

                    rightBrowEyeDistance -=
                        amplitude

                    innerBrowDistance -=
                        amplitude *
                                0.70f
                }

                SyntheticScenario
                    .SHORT_SQUINT -> {

                    if (
                        activeElapsedMs <
                        SHORT_EXPRESSION_DURATION_MS
                    ) {
                        eyeSquintLeft +=
                            amplitude

                        eyeSquintRight +=
                            amplitude
                    }
                }

                SyntheticScenario
                    .RANDOM_NON_PERSISTENT_MOVEMENT -> {

                    val randomOffset =
                        (
                                random.nextFloat() -
                                        0.5f
                                ) *
                                2f *
                                amplitude

                    eyeSquintLeft +=
                        randomOffset

                    eyeSquintRight -=
                        randomOffset

                    browDownLeft -=
                        randomOffset * 0.7f

                    browDownRight +=
                        randomOffset * 0.7f

                    browInnerUp +=
                        randomOffset * 0.5f
                }

                SyntheticScenario
                    .GEOMETRY_ASYMMETRY_ONLY -> {

                    leftBrowEyeDistance +=
                        amplitude

                    rightBrowEyeDistance -=
                        amplitude *
                                0.15f
                }

                SyntheticScenario
                    .INVALID_GEOMETRY -> {

                    geometryReliable =
                        false
                }
            }
        }

        eyeBlinkLeft =
            eyeBlinkLeft.unitValue()

        eyeBlinkRight =
            eyeBlinkRight.unitValue()

        eyeSquintLeft =
            eyeSquintLeft.unitValue()

        eyeSquintRight =
            eyeSquintRight.unitValue()

        eyeWideLeft =
            eyeWideLeft.unitValue()

        eyeWideRight =
            eyeWideRight.unitValue()

        browDownLeft =
            browDownLeft.unitValue()

        browDownRight =
            browDownRight.unitValue()

        browInnerUp =
            browInnerUp.unitValue()

        browOuterUpLeft =
            browOuterUpLeft.unitValue()

        browOuterUpRight =
            browOuterUpRight.unitValue()

        leftBrowEyeDistance =
            leftBrowEyeDistance
                .coerceIn(
                    0.02f,
                    2.40f
                )

        rightBrowEyeDistance =
            rightBrowEyeDistance
                .coerceIn(
                    0.02f,
                    2.40f
                )

        innerBrowDistance =
            innerBrowDistance
                .coerceIn(
                    0.02f,
                    2.40f
                )

        val asymmetry =
            abs(
                leftBrowEyeDistance -
                        rightBrowEyeDistance
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
                eyeWideLeft,

            eyeWideRight =
                eyeWideRight,

            browDownLeft =
                browDownLeft,

            browDownRight =
                browDownRight,

            browInnerUp =
                browInnerUp,

            browOuterUpLeft =
                browOuterUpLeft,

            browOuterUpRight =
                browOuterUpRight,

            browGeometry =
                BrowGeometryData(
                    leftBrowEyeDistanceRatio =
                        leftBrowEyeDistance,

                    rightBrowEyeDistanceRatio =
                        rightBrowEyeDistance,

                    innerBrowDistanceRatio =
                        innerBrowDistance,

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
    // Baseline creation
    // ---------------------------------------------------------------------

    private fun createBaseline(
        participant: VirtualParticipant,
        activityMedian: Float =
            0.010f,
        facialChangeMedian: Float =
            5f
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

    private fun createVirtualParticipants():
            List<VirtualParticipant> {

        return listOf(
            VirtualParticipant(
                id =
                    "VIRTUAL_01",
                seed =
                    101,
                eyeBlinkLeft =
                    0.045f,
                eyeBlinkRight =
                    0.050f,
                eyeSquintLeft =
                    0.090f,
                eyeSquintRight =
                    0.100f,
                eyeWideLeft =
                    0.025f,
                eyeWideRight =
                    0.030f,
                browDownLeft =
                    0.040f,
                browDownRight =
                    0.045f,
                browInnerUp =
                    0.050f,
                browOuterUpLeft =
                    0.040f,
                browOuterUpRight =
                    0.045f,
                leftBrowEyeDistance =
                    0.350f,
                rightBrowEyeDistance =
                    0.345f,
                innerBrowDistance =
                    0.520f,
                interEyeDistance =
                    0.200f,
                normalBlinkRate =
                    12f,
                normalBlinkDurationMs =
                    180f
            ),

            VirtualParticipant(
                id =
                    "VIRTUAL_02",
                seed =
                    202,
                eyeBlinkLeft =
                    0.090f,
                eyeBlinkRight =
                    0.085f,
                eyeSquintLeft =
                    0.170f,
                eyeSquintRight =
                    0.150f,
                eyeWideLeft =
                    0.015f,
                eyeWideRight =
                    0.020f,
                browDownLeft =
                    0.070f,
                browDownRight =
                    0.065f,
                browInnerUp =
                    0.080f,
                browOuterUpLeft =
                    0.070f,
                browOuterUpRight =
                    0.075f,
                leftBrowEyeDistance =
                    0.390f,
                rightBrowEyeDistance =
                    0.380f,
                innerBrowDistance =
                    0.560f,
                interEyeDistance =
                    0.180f,
                normalBlinkRate =
                    16f,
                normalBlinkDurationMs =
                    210f
            ),

            VirtualParticipant(
                id =
                    "VIRTUAL_03",
                seed =
                    303,
                eyeBlinkLeft =
                    0.030f,
                eyeBlinkRight =
                    0.035f,
                eyeSquintLeft =
                    0.060f,
                eyeSquintRight =
                    0.065f,
                eyeWideLeft =
                    0.060f,
                eyeWideRight =
                    0.055f,
                browDownLeft =
                    0.025f,
                browDownRight =
                    0.030f,
                browInnerUp =
                    0.035f,
                browOuterUpLeft =
                    0.025f,
                browOuterUpRight =
                    0.030f,
                leftBrowEyeDistance =
                    0.320f,
                rightBrowEyeDistance =
                    0.335f,
                innerBrowDistance =
                    0.480f,
                interEyeDistance =
                    0.220f,
                normalBlinkRate =
                    10f,
                normalBlinkDurationMs =
                    165f
            ),

            VirtualParticipant(
                id =
                    "VIRTUAL_04",
                seed =
                    404,
                eyeBlinkLeft =
                    0.120f,
                eyeBlinkRight =
                    0.110f,
                eyeSquintLeft =
                    0.220f,
                eyeSquintRight =
                    0.200f,
                eyeWideLeft =
                    0.010f,
                eyeWideRight =
                    0.012f,
                browDownLeft =
                    0.100f,
                browDownRight =
                    0.095f,
                browInnerUp =
                    0.110f,
                browOuterUpLeft =
                    0.100f,
                browOuterUpRight =
                    0.095f,
                leftBrowEyeDistance =
                    0.430f,
                rightBrowEyeDistance =
                    0.420f,
                innerBrowDistance =
                    0.610f,
                interEyeDistance =
                    0.160f,
                normalBlinkRate =
                    20f,
                normalBlinkDurationMs =
                    230f
            )
        )
    }

    // ---------------------------------------------------------------------
    // Result helpers
    // ---------------------------------------------------------------------

    private fun phaseForTimestamp(
        windowEndTimestampMs: Long,
        activeDurationMs: Long
    ): SequencePhase {

        return when {
            windowEndTimestampMs <=
                    PRELUDE_DURATION_MS ->
                SequencePhase.PRELUDE

            windowEndTimestampMs <=
                    PRELUDE_DURATION_MS +
                    activeDurationMs ->
                SequencePhase.ACTIVE

            else ->
                SequencePhase.RECOVERY
        }
    }

    private fun componentScore(
        result: FaceDistressResult,
        contributor: FaceDistressContributor
    ): Float {

        return when (contributor) {

            FaceDistressContributor.NONE ->
                0f

            FaceDistressContributor.EYE_SQUINT,
            FaceDistressContributor.EYE_WIDE,
            FaceDistressContributor.BLINK_PATTERN ->
                result.eyesScore

            FaceDistressContributor.BROW_FURROW,
            FaceDistressContributor.BROW_RAISE ->
                result.browsScore

            FaceDistressContributor
                .LOW_FACIAL_ACTIVITY ->
                result.activityScore
        }
    }

    private fun FaceDistressResult.toWindowCsvRow(
        testGroup: String,
        participantId: String,
        scenario: String,
        repetition: Int,
        severityOrder: Int?,
        usageIndex: Int?,
        phase: SequencePhase,
        expectedContributor: FaceDistressContributor,
        expectedFinalPositive: Boolean
    ): WindowCsvRow {

        return WindowCsvRow(
            testGroup =
                testGroup,
            participantId =
                participantId,
            scenario =
                scenario,
            repetition =
                repetition,
            severityOrder =
                severityOrder,
            usageIndex =
                usageIndex,
            phase =
                phase.name,
            expectedFinalPositive =
                expectedFinalPositive,
            expectedContributor =
                expectedContributor.name,
            score =
                score,
            level =
                level,
            eyesScore =
                eyesScore,
            browsScore =
                browsScore,
            activityScore =
                activityScore,
            peakFeatureScore =
                peakFeatureScore,
            topContributor =
                topContributor.name,
            isReliable =
                isReliable,
            positiveLevel =
                level > 0,
            contributorMatched =
                topContributor ==
                        expectedContributor,
            windowStartTimestampMs =
                windowStartTimestampMs,
            windowEndTimestampMs =
                windowEndTimestampMs,
            eyeSquintLeftRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .EYE_SQUINT_LEFT
                ]?.score,
            eyeSquintRightRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .EYE_SQUINT_RIGHT
                ]?.score,
            eyeWideLeftRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .EYE_WIDE_LEFT
                ]?.score,
            eyeWideRightRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .EYE_WIDE_RIGHT
                ]?.score,
            browDownLeftRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_DOWN_LEFT
                ]?.score,
            browDownRightRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_DOWN_RIGHT
                ]?.score,
            browInnerUpRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_INNER_UP
                ]?.score,
            browOuterUpLeftRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_OUTER_UP_LEFT
                ]?.score,
            browOuterUpRightRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_OUTER_UP_RIGHT
                ]?.score,
            browEyeDistanceLeftRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_EYE_DISTANCE_LEFT
                ]?.score,
            browEyeDistanceRightRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_EYE_DISTANCE_RIGHT
                ]?.score,
            innerBrowDistanceRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .INNER_BROW_DISTANCE
                ]?.score,
            geometryAsymmetryRawScore =
                rawFeatureResults[
                    FaceBaselineFeature
                        .BROW_GEOMETRY_ASYMMETRY
                ]?.score,
            blinkRate =
                derivedMetrics[
                    FaceBaselineFeature
                        .BLINK_RATE
                ],
            averageBlinkDuration =
                derivedMetrics[
                    FaceBaselineFeature
                        .AVERAGE_BLINK_DURATION
                ],
            longEyeClosureDuration =
                derivedMetrics[
                    FaceBaselineFeature
                        .LONG_EYE_CLOSURE_DURATION
                ],
            facialActivityLevel =
                derivedMetrics[
                    FaceBaselineFeature
                        .FACIAL_ACTIVITY_LEVEL
                ],
            lowActivityDuration =
                derivedMetrics[
                    FaceBaselineFeature
                        .LOW_ACTIVITY_DURATION
                ],
            facialChangeCount =
                derivedMetrics[
                    FaceBaselineFeature
                        .FACIAL_CHANGE_COUNT
                ]
        )
    }

    private fun ScenarioRunResult.toScenarioCsvRow(
        severityFamily: String? =
            null
    ): ScenarioCsvRow {

        return ScenarioCsvRow(
            testGroup =
                testGroup,
            participantId =
                participantId,
            scenario =
                scenario,
            severityFamily =
                severityFamily,
            repetition =
                repetition,
            severityOrder =
                severityOrder,
            expectedContributor =
                expectedContributor.name,
            expectedFinalPositive =
                expectedFinalPositive,
            expectedComponentActivation =
                expectedComponentActivation,
            activeReliableWindowCount =
                activeReliableWindowCount,
            positiveWindowPercent =
                positiveWindowPercent,
            contributorMatchPercent =
                contributorMatchPercent,
            componentActivationPercent =
                componentActivationPercent,
            averageScore =
                averageScore,
            maximumScore =
                maximumScore,
            maximumLevel =
                maximumLevel,
            averageEyesScore =
                averageEyesScore,
            averageBrowsScore =
                averageBrowsScore,
            averageActivityScore =
                averageActivityScore,
            maximumExpectedComponentScore =
                maximumExpectedComponentScore,
            recoveredToZero =
                recoveredToZero
        )
    }

    // ---------------------------------------------------------------------
    // CSV output
    // ---------------------------------------------------------------------

    private fun writeWindowCsv(
        file: File,
        rows: List<WindowCsvRow>
    ) {

        val header =
            listOf(
                "testGroup",
                "participantId",
                "scenario",
                "repetition",
                "severityOrder",
                "usageIndex",
                "phase",
                "expectedFinalPositive",
                "expectedContributor",
                "score",
                "level",
                "eyesScore",
                "browsScore",
                "activityScore",
                "peakFeatureScore",
                "topContributor",
                "isReliable",
                "positiveLevel",
                "contributorMatched",
                "windowStartTimestampMs",
                "windowEndTimestampMs",
                "eyeSquintLeftRawScore",
                "eyeSquintRightRawScore",
                "eyeWideLeftRawScore",
                "eyeWideRightRawScore",
                "browDownLeftRawScore",
                "browDownRightRawScore",
                "browInnerUpRawScore",
                "browOuterUpLeftRawScore",
                "browOuterUpRightRawScore",
                "browEyeDistanceLeftRawScore",
                "browEyeDistanceRightRawScore",
                "innerBrowDistanceRawScore",
                "geometryAsymmetryRawScore",
                "blinkRate",
                "averageBlinkDuration",
                "longEyeClosureDuration",
                "facialActivityLevel",
                "lowActivityDuration",
                "facialChangeCount"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(",")
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.testGroup,
                        row.participantId,
                        row.scenario,
                        row.repetition,
                        row.severityOrder,
                        row.usageIndex,
                        row.phase,
                        row.expectedFinalPositive,
                        row.expectedContributor,
                        row.score,
                        row.level,
                        row.eyesScore,
                        row.browsScore,
                        row.activityScore,
                        row.peakFeatureScore,
                        row.topContributor,
                        row.isReliable,
                        row.positiveLevel,
                        row.contributorMatched,
                        row.windowStartTimestampMs,
                        row.windowEndTimestampMs,
                        row.eyeSquintLeftRawScore,
                        row.eyeSquintRightRawScore,
                        row.eyeWideLeftRawScore,
                        row.eyeWideRightRawScore,
                        row.browDownLeftRawScore,
                        row.browDownRightRawScore,
                        row.browInnerUpRawScore,
                        row.browOuterUpLeftRawScore,
                        row.browOuterUpRightRawScore,
                        row.browEyeDistanceLeftRawScore,
                        row.browEyeDistanceRightRawScore,
                        row.innerBrowDistanceRawScore,
                        row.geometryAsymmetryRawScore,
                        row.blinkRate,
                        row.averageBlinkDuration,
                        row.longEyeClosureDuration,
                        row.facialActivityLevel,
                        row.lowActivityDuration,
                        row.facialChangeCount
                    ).toCsvLine()
                )
            }
        }
    }

    private fun writeScenarioCsv(
        file: File,
        rows: List<ScenarioCsvRow>
    ) {

        val header =
            listOf(
                "testGroup",
                "participantId",
                "scenario",
                "severityFamily",
                "repetition",
                "severityOrder",
                "expectedContributor",
                "expectedFinalPositive",
                "expectedComponentActivation",
                "activeReliableWindowCount",
                "positiveWindowPercent",
                "contributorMatchPercent",
                "componentActivationPercent",
                "averageScore",
                "maximumScore",
                "maximumLevel",
                "averageEyesScore",
                "averageBrowsScore",
                "averageActivityScore",
                "maximumExpectedComponentScore",
                "recoveredToZero"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(",")
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.testGroup,
                        row.participantId,
                        row.scenario,
                        row.severityFamily,
                        row.repetition,
                        row.severityOrder,
                        row.expectedContributor,
                        row.expectedFinalPositive,
                        row.expectedComponentActivation,
                        row.activeReliableWindowCount,
                        row.positiveWindowPercent,
                        row.contributorMatchPercent,
                        row.componentActivationPercent,
                        row.averageScore,
                        row.maximumScore,
                        row.maximumLevel,
                        row.averageEyesScore,
                        row.averageBrowsScore,
                        row.averageActivityScore,
                        row.maximumExpectedComponentScore,
                        row.recoveredToZero
                    ).toCsvLine()
                )
            }
        }
    }

    private fun writeSeverityCsv(
        file: File,
        rows: List<SeveritySequenceCsvRow>
    ) {

        val header =
            listOf(
                "participantId",
                "family",
                "repetition",
                "weakComponentScore",
                "moderateComponentScore",
                "strongComponentScore",
                "veryStrongComponentScore",
                "weakFinalScore",
                "moderateFinalScore",
                "strongFinalScore",
                "veryStrongFinalScore",
                "weakLevel",
                "moderateLevel",
                "strongLevel",
                "veryStrongLevel",
                "componentMonotonic",
                "finalScoreMonotonic",
                "levelMonotonic",
                "componentIncreasedAtLeastOnce",
                "finalScoreIncreasedAtLeastOnce"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(",")
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.participantId,
                        row.family,
                        row.repetition,
                        row.weakComponentScore,
                        row.moderateComponentScore,
                        row.strongComponentScore,
                        row.veryStrongComponentScore,
                        row.weakFinalScore,
                        row.moderateFinalScore,
                        row.strongFinalScore,
                        row.veryStrongFinalScore,
                        row.weakLevel,
                        row.moderateLevel,
                        row.strongLevel,
                        row.veryStrongLevel,
                        row.componentMonotonic,
                        row.finalScoreMonotonic,
                        row.levelMonotonic,
                        row.componentIncreasedAtLeastOnce,
                        row.finalScoreIncreasedAtLeastOnce
                    ).toCsvLine()
                )
            }
        }
    }

    private fun writeAdaptationCsv(
        file: File,
        rows: List<AdaptationCsvRow>
    ) {

        val header =
            listOf(
                "participantId",
                "recordType",
                "usageIndex",
                "updateProduced",
                "rawBaselineUnchanged",
                "derivedMetricCount",
                "activityMedianBefore",
                "activityMedianAfter",
                "estimatedActivityTarget",
                "distanceToActivityTarget",
                "distanceDidNotIncrease",
                "averageNormalScore",
                "normalScoreStandardDeviation",
                "positiveNormalWindowPercent",
                "distressUpdateBlocked",
                "invalidFrameUpdateBlocked"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(",")
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.participantId,
                        row.recordType,
                        row.usageIndex,
                        row.updateProduced,
                        row.rawBaselineUnchanged,
                        row.derivedMetricCount,
                        row.activityMedianBefore,
                        row.activityMedianAfter,
                        row.estimatedActivityTarget,
                        row.distanceToActivityTarget,
                        row.distanceDidNotIncrease,
                        row.averageNormalScore,
                        row.normalScoreStandardDeviation,
                        row.positiveNormalWindowPercent,
                        row.distressUpdateBlocked,
                        row.invalidFrameUpdateBlocked
                    ).toCsvLine()
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------

    private fun buildSummary(
        windowRows: List<WindowCsvRow>,
        scenarioRows: List<ScenarioCsvRow>,
        severityRows: List<SeveritySequenceCsvRow>,
        adaptationRows: List<AdaptationCsvRow>
    ): String {

        val basicRows =
            scenarioRows.filter {
                it.testGroup ==
                        BASIC_GROUP
            }

        val robustnessRows =
            scenarioRows.filter {
                it.testGroup ==
                        ROBUSTNESS_GROUP
            }

        val expectedPositiveBasic =
            basicRows.filter {
                it.expectedFinalPositive
            }

        val expectedNegativeBasic =
            basicRows.filter {
                !it.expectedFinalPositive
            }

        val detectedPositiveBasic =
            expectedPositiveBasic.count {
                it.positiveWindowPercent > 0f
            }

        val rejectedNegativeBasic =
            expectedNegativeBasic.count {
                it.positiveWindowPercent == 0f
            }

        val robustnessFalsePositiveRuns =
            robustnessRows.count {
                it.scenario !=
                        SyntheticScenario
                            .MISSING_FACE_RECOVERY
                            .name &&
                        it.positiveWindowPercent > 0f
            }

        val componentMonotonicCount =
            severityRows.count {
                it.componentMonotonic
            }

        val finalScoreMonotonicCount =
            severityRows.count {
                it.finalScoreMonotonic
            }

        val levelMonotonicCount =
            severityRows.count {
                it.levelMonotonic
            }

        val adaptationUpdates =
            adaptationRows.filter {
                it.recordType ==
                        ADAPTATION_UPDATE_RECORD
            }

        val producedUpdates =
            adaptationUpdates.count {
                it.updateProduced
            }

        val rawProtectedUpdates =
            adaptationUpdates.count {
                it.rawBaselineUnchanged
            }

        val nonIncreasingDistances =
            adaptationUpdates
                .mapNotNull {
                    it.distanceDidNotIncrease
                }
                .count {
                    it
                }

        val comparableDistances =
            adaptationUpdates.count {
                it.distanceDidNotIncrease !=
                        null
            }

        val distressBlocked =
            adaptationRows
                .filter {
                    it.recordType ==
                            DISTRESS_BLOCK_RECORD
                }
                .count {
                    it.distressUpdateBlocked ==
                            true
                }

        val invalidBlocked =
            adaptationRows
                .filter {
                    it.recordType ==
                            INVALID_BLOCK_RECORD
                }
                .count {
                    it.invalidFrameUpdateBlocked ==
                            true
                }

        return """
        Complete synthetic facial evaluation finished.

        Virtual participants: ${createVirtualParticipants().size}
        Completed facial windows: ${windowRows.size}
        Scenario runs: ${scenarioRows.size}
        Severity sequences: ${severityRows.size}
        Adaptation records: ${adaptationRows.size}

        Basic controlled scenarios:
        Expected-positive runs detected at least once:
        $detectedPositiveBasic / ${expectedPositiveBasic.size} (${percentageText(detectedPositiveBasic, expectedPositiveBasic.size)})

        Expected-negative runs with no positive level:
        $rejectedNegativeBasic / ${expectedNegativeBasic.size} (${percentageText(rejectedNegativeBasic, expectedNegativeBasic.size)})

        Robustness:
        Runs with at least one unexpected positive window:
        $robustnessFalsePositiveRuns / ${robustnessRows.count { it.scenario != SyntheticScenario.MISSING_FACE_RECOVERY.name }} (${percentageText(robustnessFalsePositiveRuns, robustnessRows.count { it.scenario != SyntheticScenario.MISSING_FACE_RECOVERY.name })})

        Severity response:
        Component-score monotonic sequences:
        $componentMonotonicCount / ${severityRows.size} (${percentageText(componentMonotonicCount, severityRows.size)})

        Final-score monotonic sequences:
        $finalScoreMonotonicCount / ${severityRows.size} (${percentageText(finalScoreMonotonicCount, severityRows.size)})

        Final-level monotonic sequences:
        $levelMonotonicCount / ${severityRows.size} (${percentageText(levelMonotonicCount, severityRows.size)})

        Derived-baseline adaptation:
        Baseline updates produced:
        $producedUpdates / ${adaptationUpdates.size} (${percentageText(producedUpdates, adaptationUpdates.size)})

        Updates preserving all 15 raw metrics:
        $rawProtectedUpdates / ${adaptationUpdates.size} (${percentageText(rawProtectedUpdates, adaptationUpdates.size)})

        Comparable updates whose target distance did not increase:
        $nonIncreasingDistances / $comparableDistances (${percentageText(nonIncreasingDistances, comparableDistances)})

        Strong-distress sequences blocked from learning:
        $distressBlocked / ${createVirtualParticipants().size} (${percentageText(distressBlocked, createVirtualParticipants().size)})

        Invalid-frame sequences blocked from learning:
        $invalidBlocked / ${createVirtualParticipants().size} (${percentageText(invalidBlocked, createVirtualParticipants().size)})

        Interpretation note:
        These are controlled engineering tests of algorithm behavior.
        Synthetic expression levels are not clinical labels and do not
        establish medical accuracy for human distress.
        """.trimIndent()
    }

    // ---------------------------------------------------------------------
    // General utilities
    // ---------------------------------------------------------------------

    private fun percentageText(
        numerator: Int,
        denominator: Int
    ): String {

        if (denominator <= 0) {
            return "0.00%"
        }

        return String.format(
            java.util.Locale.US,
            "%.2f%%",
            numerator *
                    100.0 /
                    denominator
        )
    }

    private fun List<Any?>.toCsvLine():
            String {

        return joinToString(",") { value ->
            csvValue(value)
        }
    }

    private fun csvValue(
        value: Any?
    ): String {

        if (value == null) {
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

    private fun Float.unitValue():
            Float {

        return coerceIn(
            0f,
            1f
        )
    }

    private fun framesForDuration(
        durationMs: Long
    ): Int {

        return (
                durationMs /
                        FRAME_INTERVAL_MS
                ).toInt()
            .coerceAtLeast(1)
    }

    private fun List<Float>.averageOrZero():
            Float {

        if (isEmpty()) {
            return 0f
        }

        return average()
            .toFloat()
    }

    private fun <T> List<T>.percentage(
        predicate: (T) -> Boolean
    ): Float {

        if (isEmpty()) {
            return 0f
        }

        return count(predicate) *
                100f /
                size.toFloat()
    }

    private fun standardDeviation(
        values: List<Float>
    ): Float {

        if (values.isEmpty()) {
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

    private fun isNonDecreasing(
        values: List<Float>
    ): Boolean {

        return values
            .zipWithNext()
            .all { pair ->
                pair.second +
                        MONOTONIC_TOLERANCE >=
                        pair.first
            }
    }

    private fun increasedAtLeastOnce(
        values: List<Float>
    ): Boolean {

        return values
            .zipWithNext()
            .any { pair ->
                pair.second >
                        pair.first +
                        MONOTONIC_TOLERANCE
            }
    }

    // ---------------------------------------------------------------------
    // Internal data models
    // ---------------------------------------------------------------------

    private enum class SyntheticScenario {
        NORMAL_STABLE,
        BLINK_PATTERN,
        LONG_EYE_CLOSURE,
        TIMED_EYE_CLOSURE,
        EYE_SQUINT,
        EYE_WIDE,
        BROW_FURROW_CONFIRMED,
        BROW_RAISE_CONFIRMED,
        LOW_FACIAL_ACTIVITY,

        SINGLE_EYE_SQUINT,
        BROW_BLENDSHAPE_ONLY,
        BROW_GEOMETRY_ONLY,
        SHORT_SQUINT,
        RANDOM_NON_PERSISTENT_MOVEMENT,
        GEOMETRY_ASYMMETRY_ONLY,
        INVALID_GEOMETRY,
        MISSING_FACE_RECOVERY
    }

    private enum class SequencePhase {
        PRELUDE,
        ACTIVE,
        RECOVERY
    }

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

    private data class ScenarioConfiguration(
        val scenario: SyntheticScenario,
        val expectedContributor:
        FaceDistressContributor,
        val expectedFinalPositive: Boolean,
        val expectedComponentActivation: Boolean,
        val activeDurationMs: Long,
        val amplitude: Float,
        val useLowActivityBaseline: Boolean =
            false
    )

    private data class SeverityFamily(
        val familyName: String,
        val scenario: SyntheticScenario,
        val expectedContributor:
        FaceDistressContributor,
        val amplitudes: List<Float>,
        val activeDurationsMs: List<Long>,
        val useLowActivityBaseline: Boolean =
            false
    )

    private data class ResultWithPhase(
        val phase: SequencePhase,
        val result: FaceDistressResult
    )

    private data class ScenarioRunResult(
        val testGroup: String,
        val participantId: String,
        val scenario: String,
        val repetition: Int,
        val severityOrder: Int?,
        val expectedContributor:
        FaceDistressContributor,
        val expectedFinalPositive: Boolean,
        val expectedComponentActivation: Boolean,
        val activeReliableWindowCount: Int,
        val positiveWindowPercent: Float,
        val contributorMatchPercent: Float,
        val componentActivationPercent: Float,
        val averageScore: Float,
        val maximumScore: Float,
        val maximumLevel: Int,
        val averageEyesScore: Float,
        val averageBrowsScore: Float,
        val averageActivityScore: Float,
        val maximumExpectedComponentScore: Float,
        val recoveredToZero: Boolean
    )

    private data class WindowCsvRow(
        val testGroup: String,
        val participantId: String,
        val scenario: String,
        val repetition: Int,
        val severityOrder: Int?,
        val usageIndex: Int?,
        val phase: String,
        val expectedFinalPositive: Boolean,
        val expectedContributor: String,
        val score: Float,
        val level: Int,
        val eyesScore: Float,
        val browsScore: Float,
        val activityScore: Float,
        val peakFeatureScore: Float,
        val topContributor: String,
        val isReliable: Boolean,
        val positiveLevel: Boolean,
        val contributorMatched: Boolean,
        val windowStartTimestampMs: Long,
        val windowEndTimestampMs: Long,
        val eyeSquintLeftRawScore: Float?,
        val eyeSquintRightRawScore: Float?,
        val eyeWideLeftRawScore: Float?,
        val eyeWideRightRawScore: Float?,
        val browDownLeftRawScore: Float?,
        val browDownRightRawScore: Float?,
        val browInnerUpRawScore: Float?,
        val browOuterUpLeftRawScore: Float?,
        val browOuterUpRightRawScore: Float?,
        val browEyeDistanceLeftRawScore: Float?,
        val browEyeDistanceRightRawScore: Float?,
        val innerBrowDistanceRawScore: Float?,
        val geometryAsymmetryRawScore: Float?,
        val blinkRate: Float?,
        val averageBlinkDuration: Float?,
        val longEyeClosureDuration: Float?,
        val facialActivityLevel: Float?,
        val lowActivityDuration: Float?,
        val facialChangeCount: Float?
    )

    private data class ScenarioCsvRow(
        val testGroup: String,
        val participantId: String,
        val scenario: String,
        val severityFamily: String?,
        val repetition: Int,
        val severityOrder: Int?,
        val expectedContributor: String,
        val expectedFinalPositive: Boolean,
        val expectedComponentActivation: Boolean,
        val activeReliableWindowCount: Int,
        val positiveWindowPercent: Float,
        val contributorMatchPercent: Float,
        val componentActivationPercent: Float,
        val averageScore: Float,
        val maximumScore: Float,
        val maximumLevel: Int,
        val averageEyesScore: Float,
        val averageBrowsScore: Float,
        val averageActivityScore: Float,
        val maximumExpectedComponentScore: Float,
        val recoveredToZero: Boolean
    )

    private data class SeveritySequenceCsvRow(
        val participantId: String,
        val family: String,
        val repetition: Int,

        val weakComponentScore: Float,
        val moderateComponentScore: Float,
        val strongComponentScore: Float,
        val veryStrongComponentScore: Float,

        val weakFinalScore: Float,
        val moderateFinalScore: Float,
        val strongFinalScore: Float,
        val veryStrongFinalScore: Float,

        val weakLevel: Int,
        val moderateLevel: Int,
        val strongLevel: Int,
        val veryStrongLevel: Int,

        val componentMonotonic: Boolean,
        val finalScoreMonotonic: Boolean,
        val levelMonotonic: Boolean,
        val componentIncreasedAtLeastOnce: Boolean,
        val finalScoreIncreasedAtLeastOnce: Boolean
    )

    private data class AdaptationCsvRow(
        val participantId: String,
        val recordType: String,
        val usageIndex: Int?,
        val updateProduced: Boolean,
        val rawBaselineUnchanged: Boolean,
        val derivedMetricCount: Int,
        val activityMedianBefore: Float?,
        val activityMedianAfter: Float?,
        val estimatedActivityTarget: Float?,
        val distanceToActivityTarget: Float?,
        val distanceDidNotIncrease: Boolean?,
        val averageNormalScore: Float?,
        val normalScoreStandardDeviation: Float?,
        val positiveNormalWindowPercent: Float?,
        val distressUpdateBlocked: Boolean?,
        val invalidFrameUpdateBlocked: Boolean?
    )

    companion object {

        private const val BASIC_GROUP =
            "BASIC"

        private const val ROBUSTNESS_GROUP =
            "ROBUSTNESS"

        private const val SEVERITY_GROUP =
            "SEVERITY"

        private const val ADAPTATION_GROUP =
            "ADAPTATION"

        private const val ADAPTATION_UPDATE_RECORD =
            "BASELINE_UPDATE"

        private const val DISTRESS_BLOCK_RECORD =
            "DISTRESS_LEARNING_BLOCK"

        private const val INVALID_BLOCK_RECORD =
            "INVALID_FRAME_LEARNING_BLOCK"

        /*
         * The real camera uses 15 FPS by default.
         */
        private const val FRAME_INTERVAL_MS =
            67L

        private const val PRELUDE_DURATION_MS =
            3_000L

        private const val RECOVERY_DURATION_MS =
            5_000L

        private const val BLINK_CYCLE_MS =
            1_000L

        private const val BLINK_CLOSED_DURATION_MS =
            200L

        private const val SHORT_EXPRESSION_DURATION_MS =
            700L

        private const val BASIC_REPETITIONS =
            3

        private const val ROBUSTNESS_REPETITIONS =
            3

        private const val SEVERITY_REPETITIONS =
            3

        private const val ADAPTATION_USE_COUNT =
            5

        /*
         * More than 60 clean completed windows are needed because
         * learning starts only after sufficient recent history exists.
         */
        private const val LEARNING_WINDOW_TARGET =
            78

        private const val INVALID_FRAME_COUNT =
            700

        private const val RECOVERY_TAIL_WINDOW_COUNT =
            3

        private const val COMPONENT_ACTIVE_THRESHOLD =
            0.05f

        private const val MONOTONIC_TOLERANCE =
            0.03f

        /*
         * These values mirror the production merge rule only for
         * reporting the estimated stable target in the CSV.
         *
         * They are not used to calculate algorithm scores.
         */
        private const val EXISTING_WEIGHT_FOR_REPORT =
            0.80f

        private const val NEW_WEIGHT_FOR_REPORT =
            0.20f
    }
}