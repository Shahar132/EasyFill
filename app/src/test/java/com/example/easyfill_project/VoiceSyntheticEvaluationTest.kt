package com.example.easyfill_project.voiceanalysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Complete synthetic engineering evaluation of the current voice algorithm.
 *
 * The production voice score is:
 *
 * speech-rate score (0..2)
 * +
 * RMS-variation score (0..2)
 * =
 * final voice score (0..4)
 *
 * This test covers:
 *
 * 1. Normal, fast, slow and RMS-variation scenarios.
 * 2. Every possible component-score combination.
 * 3. Final voice levels 0 through 4.
 * 4. Personal baselines with different normal speech rates and RMS variation.
 * 5. Speech-rate and RMS threshold boundaries.
 * 6. Reliability and missing-baseline behavior.
 * 7. Pause and hesitation independence from the current score.
 * 8. Monotonic severity sequences.
 *
 * The test creates SpeechAnalysisResult objects directly. It does not wait
 * for real time or invoke Android SpeechRecognizer.
 *
 * The current production implementation does not adapt the voice baseline
 * after the initial calibration. Therefore, this file evaluates scoring,
 * reliability, thresholds and robustness, not baseline convergence.
 *
 * Generated files:
 *
 * app/build/synthetic-voice-evaluation/
 * ├── synthetic_voice_records.csv
 * ├── synthetic_voice_boundaries.csv
 * ├── synthetic_voice_severity_sequences.csv
 * └── synthetic_voice_summary.txt
 */
class VoiceSyntheticEvaluationTest {

    @Test
    fun generateCompleteSyntheticVoiceEvaluationDataset() {

        val outputDirectory =
            resolveOutputDirectory()

        val recordRows =
            mutableListOf<RecordRow>()

        val boundaryRows =
            mutableListOf<BoundaryRow>()

        val severityRows =
            mutableListOf<SeverityRow>()

        virtualParticipants().forEach { participant ->

            runControlledScenarios(
                participant =
                    participant,
                rows =
                    recordRows
            )

            runAllComponentCombinations(
                participant =
                    participant,
                rows =
                    recordRows
            )

            runReliabilityAndRobustnessCases(
                participant =
                    participant,
                rows =
                    recordRows
            )

            runPauseAndHesitationIndependence(
                participant =
                    participant,
                rows =
                    recordRows
            )

            runBoundaryEvaluation(
                participant =
                    participant,
                rows =
                    boundaryRows
            )

            runSeverityEvaluation(
                participant =
                    participant,
                recordRows =
                    recordRows,
                severityRows =
                    severityRows
            )
        }

        val recordsFile =
            File(
                outputDirectory,
                "synthetic_voice_records.csv"
            )

        val boundariesFile =
            File(
                outputDirectory,
                "synthetic_voice_boundaries.csv"
            )

        val severityFile =
            File(
                outputDirectory,
                "synthetic_voice_severity_sequences.csv"
            )

        val summaryFile =
            File(
                outputDirectory,
                "synthetic_voice_summary.txt"
            )

        writeRecordCsv(
            file =
                recordsFile,
            rows =
                recordRows
        )

        writeBoundaryCsv(
            file =
                boundariesFile,
            rows =
                boundaryRows
        )

        writeSeverityCsv(
            file =
                severityFile,
            rows =
                severityRows
        )

        val summary =
            buildSummary(
                recordRows =
                    recordRows,
                boundaryRows =
                    boundaryRows,
                severityRows =
                    severityRows
            )

        summaryFile.writeText(
            summary
        )

        println(summary)
        println()
        println(
            "Records CSV: ${recordsFile.absolutePath}"
        )
        println(
            "Boundaries CSV: ${boundariesFile.absolutePath}"
        )
        println(
            "Severity CSV: ${severityFile.absolutePath}"
        )
        println(
            "Summary: ${summaryFile.absolutePath}"
        )

        assertTrue(
            "No synthetic voice records were generated.",
            recordRows.isNotEmpty()
        )

        assertTrue(
            "No boundary records were generated.",
            boundaryRows.isNotEmpty()
        )

        assertTrue(
            "No severity sequences were generated.",
            severityRows.isNotEmpty()
        )

        assertTrue(
            "One or more output files were not created.",
            listOf(
                recordsFile,
                boundariesFile,
                severityFile,
                summaryFile
            ).all {
                it.exists()
            }
        )
    }

    // ---------------------------------------------------------------------
    // Controlled scenarios
    // ---------------------------------------------------------------------

    private fun runControlledScenarios(
        participant: VirtualParticipant,
        rows: MutableList<RecordRow>
    ) {

        val scenarios =
            listOf(
                Scenario(
                    name =
                        "NORMAL_VOICE",
                    expectedSpeechRateScore =
                        0,
                    expectedRmsScore =
                        0,
                    speechDeviation =
                        0.10,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        1.00,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "MODERATELY_FAST_SPEECH",
                    expectedSpeechRateScore =
                        1,
                    expectedRmsScore =
                        0,
                    speechDeviation =
                        0.35,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        1.00,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "MODERATELY_SLOW_SPEECH",
                    expectedSpeechRateScore =
                        1,
                    expectedRmsScore =
                        0,
                    speechDeviation =
                        0.35,
                    speechDirection =
                        SpeechDirection.SLOWER,
                    rmsRatio =
                        1.00,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "HIGHLY_FAST_SPEECH",
                    expectedSpeechRateScore =
                        2,
                    expectedRmsScore =
                        0,
                    speechDeviation =
                        0.55,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        1.00,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "HIGHLY_SLOW_SPEECH",
                    expectedSpeechRateScore =
                        2,
                    expectedRmsScore =
                        0,
                    speechDeviation =
                        0.55,
                    speechDirection =
                        SpeechDirection.SLOWER,
                    rmsRatio =
                        1.00,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "MILD_RMS_VARIATION",
                    expectedSpeechRateScore =
                        0,
                    expectedRmsScore =
                        1,
                    speechDeviation =
                        0.10,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        1.60,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "HIGH_RMS_VARIATION",
                    expectedSpeechRateScore =
                        0,
                    expectedRmsScore =
                        2,
                    speechDeviation =
                        0.10,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        2.20,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "MODERATE_RATE_AND_MILD_RMS",
                    expectedSpeechRateScore =
                        1,
                    expectedRmsScore =
                        1,
                    speechDeviation =
                        0.35,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        1.60,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "HIGH_RATE_AND_MILD_RMS",
                    expectedSpeechRateScore =
                        2,
                    expectedRmsScore =
                        1,
                    speechDeviation =
                        0.55,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        1.60,
                    expectedAvailable =
                        true
                ),

                Scenario(
                    name =
                        "HIGH_RATE_AND_HIGH_RMS",
                    expectedSpeechRateScore =
                        2,
                    expectedRmsScore =
                        2,
                    speechDeviation =
                        0.55,
                    speechDirection =
                        SpeechDirection.FASTER,
                    rmsRatio =
                        2.20,
                    expectedAvailable =
                        true
                )
            )

        scenarios.forEach { scenario ->

            repeat(
                CONTROLLED_REPETITIONS
            ) { repetitionIndex ->

                val currentRate =
                    currentRateForWeightedDeviation(
                        baselineRate =
                            participant.baselineSpeechRate,
                        weightedDeviation =
                            scenario.speechDeviation,
                        direction =
                            scenario.speechDirection
                    )

                val currentRmsVariation =
                    participant.baselineRmsVariation *
                            scenario.rmsRatio

                val analysis =
                    createReliableAnalysis(
                        speechRate =
                            currentRate,
                        rmsVariation =
                            currentRmsVariation,
                        pauseCount =
                            repetitionIndex,
                        hesitationCount =
                            repetitionIndex
                    )

                val result =
                    scoreRecording(
                        analysis =
                            analysis,
                        baseline =
                            participant.toBaseline()
                    )

                val expectedVoiceScore =
                    scenario.expectedSpeechRateScore +
                            scenario.expectedRmsScore

                assertEquals(
                    "Unexpected speech-rate score in ${scenario.name}.",
                    scenario.expectedSpeechRateScore,
                    result.speechRateScore
                )

                assertEquals(
                    "Unexpected RMS score in ${scenario.name}.",
                    scenario.expectedRmsScore,
                    result.rmsScore
                )

                assertEquals(
                    "Unexpected final voice score in ${scenario.name}.",
                    expectedVoiceScore,
                    result.voiceScore
                )

                rows.add(
                    result.toRecordRow(
                        testGroup =
                            GROUP_CONTROLLED,
                        participant =
                            participant,
                        scenario =
                            scenario.name,
                        repetition =
                            repetitionIndex + 1,
                        expectedAvailable =
                            scenario.expectedAvailable,
                        expectedSpeechRateScore =
                            scenario.expectedSpeechRateScore,
                        expectedRmsScore =
                            scenario.expectedRmsScore,
                        expectedVoiceScore =
                            expectedVoiceScore,
                        analysis =
                            analysis
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Every component-score combination
    // ---------------------------------------------------------------------

    private fun runAllComponentCombinations(
        participant: VirtualParticipant,
        rows: MutableList<RecordRow>
    ) {

        for (
        expectedSpeechScore in
        0..2
        ) {
            for (
            expectedRmsScore in
            0..2
            ) {

                val speechDeviation =
                    when (
                        expectedSpeechScore
                    ) {
                        0 ->
                            0.10

                        1 ->
                            0.35

                        else ->
                            0.55
                    }

                val rmsRatio =
                    when (
                        expectedRmsScore
                    ) {
                        0 ->
                            1.00

                        1 ->
                            1.60

                        else ->
                            2.20
                    }

                val currentRate =
                    currentRateForWeightedDeviation(
                        baselineRate =
                            participant.baselineSpeechRate,
                        weightedDeviation =
                            speechDeviation,
                        direction =
                            SpeechDirection.FASTER
                    )

                val analysis =
                    createReliableAnalysis(
                        speechRate =
                            currentRate,
                        rmsVariation =
                            participant
                                .baselineRmsVariation *
                                    rmsRatio
                    )

                val result =
                    scoreRecording(
                        analysis =
                            analysis,
                        baseline =
                            participant.toBaseline()
                    )

                val expectedVoiceScore =
                    (
                            expectedSpeechScore +
                                    expectedRmsScore
                            ).coerceIn(
                            0,
                            4
                        )

                assertEquals(
                    expectedSpeechScore,
                    result.speechRateScore
                )

                assertEquals(
                    expectedRmsScore,
                    result.rmsScore
                )

                assertEquals(
                    expectedVoiceScore,
                    result.voiceScore
                )

                rows.add(
                    result.toRecordRow(
                        testGroup =
                            GROUP_COMBINATION_MATRIX,
                        participant =
                            participant,
                        scenario =
                            "SPEECH_${expectedSpeechScore}_RMS_${expectedRmsScore}",
                        repetition =
                            1,
                        expectedAvailable =
                            true,
                        expectedSpeechRateScore =
                            expectedSpeechScore,
                        expectedRmsScore =
                            expectedRmsScore,
                        expectedVoiceScore =
                            expectedVoiceScore,
                        analysis =
                            analysis
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Reliability and robustness
    // ---------------------------------------------------------------------

    private fun runReliabilityAndRobustnessCases(
        participant: VirtualParticipant,
        rows: MutableList<RecordRow>
    ) {

        val cases =
            listOf(
                RobustnessCase(
                    scenario =
                        "DURATION_BELOW_MINIMUM",
                    analysis =
                        createAnalysis(
                            durationSeconds =
                                SpeechAudioAnalyzer
                                    .MIN_RELIABLE_DURATION_SECONDS -
                                        0.10,
                            wordCount =
                                20,
                            speechRate =
                                2.0,
                            rmsVariation =
                                participant
                                    .baselineRmsVariation,
                            isReliable =
                                false
                        ),
                    baseline =
                        participant.toBaseline(),
                    expectedAvailable =
                        false,
                    expectedSpeechRateScore =
                        null,
                    expectedRmsScore =
                        null,
                    expectedVoiceScore =
                        null
                ),

                RobustnessCase(
                    scenario =
                        "NO_RECOGNIZED_WORDS",
                    analysis =
                        createAnalysis(
                            durationSeconds =
                                SpeechAudioAnalyzer
                                    .MIN_RELIABLE_DURATION_SECONDS,
                            wordCount =
                                0,
                            speechRate =
                                0.0,
                            rmsVariation =
                                participant
                                    .baselineRmsVariation,
                            isReliable =
                                false
                        ),
                    baseline =
                        participant.toBaseline(),
                    expectedAvailable =
                        false,
                    expectedSpeechRateScore =
                        null,
                    expectedRmsScore =
                        null,
                    expectedVoiceScore =
                        null
                ),

                RobustnessCase(
                    scenario =
                        "MISSING_SPEECH_RATE_BASELINE",
                    analysis =
                        createReliableAnalysis(
                            speechRate =
                                participant
                                    .baselineSpeechRate,
                            rmsVariation =
                                participant
                                    .baselineRmsVariation
                        ),
                    baseline =
                        participant
                            .toBaseline()
                            .copy(
                                speechRate =
                                    null
                            ),
                    expectedAvailable =
                        false,
                    expectedSpeechRateScore =
                        null,
                    expectedRmsScore =
                        null,
                    expectedVoiceScore =
                        null
                ),

                RobustnessCase(
                    scenario =
                        "MISSING_RMS_BASELINE",
                    analysis =
                        createReliableAnalysis(
                            speechRate =
                                participant
                                    .baselineSpeechRate,
                            rmsVariation =
                                participant
                                    .baselineRmsVariation
                        ),
                    baseline =
                        participant
                            .toBaseline()
                            .copy(
                                rmsVariation =
                                    null
                            ),
                    expectedAvailable =
                        false,
                    expectedSpeechRateScore =
                        null,
                    expectedRmsScore =
                        null,
                    expectedVoiceScore =
                        null
                ),

                /*
                 * Production checks null baseline values before scoring,
                 * but the scorers themselves also protect against zero.
                 */
                RobustnessCase(
                    scenario =
                        "ZERO_BASELINE_VALUES",
                    analysis =
                        createReliableAnalysis(
                            speechRate =
                                participant
                                    .baselineSpeechRate,
                            rmsVariation =
                                participant
                                    .baselineRmsVariation
                        ),
                    baseline =
                        VoiceBaseline(
                            speechRate =
                                0.0,
                            rmsVariation =
                                0.0
                        ),
                    expectedAvailable =
                        true,
                    expectedSpeechRateScore =
                        0,
                    expectedRmsScore =
                        0,
                    expectedVoiceScore =
                        0
                ),

                RobustnessCase(
                    scenario =
                        "ZERO_CURRENT_RMS_VARIATION",
                    analysis =
                        createReliableAnalysis(
                            speechRate =
                                participant
                                    .baselineSpeechRate,
                            rmsVariation =
                                0.0
                        ),
                    baseline =
                        participant.toBaseline(),
                    expectedAvailable =
                        true,
                    expectedSpeechRateScore =
                        SpeechRateScorer
                            .calculateVoiceScore(
                                baselineSpeechRate =
                                    participant
                                        .baselineSpeechRate,
                                currentSpeechRate =
                                    participant
                                        .baselineSpeechRate
                            ),
                    expectedRmsScore =
                        0,
                    expectedVoiceScore =
                        SpeechRateScorer
                            .calculateVoiceScore(
                                baselineSpeechRate =
                                    participant
                                        .baselineSpeechRate,
                                currentSpeechRate =
                                    participant
                                        .baselineSpeechRate
                            )
                )
            )

        cases.forEach { case ->

            val result =
                scoreRecording(
                    analysis =
                        case.analysis,
                    baseline =
                        case.baseline
                )

            assertEquals(
                "Unexpected availability in ${case.scenario}.",
                case.expectedAvailable,
                result.available
            )

            assertEquals(
                case.expectedSpeechRateScore,
                result.speechRateScore
            )

            assertEquals(
                case.expectedRmsScore,
                result.rmsScore
            )

            assertEquals(
                case.expectedVoiceScore,
                result.voiceScore
            )

            rows.add(
                result.toRecordRow(
                    testGroup =
                        GROUP_ROBUSTNESS,
                    participant =
                        participant,
                    scenario =
                        case.scenario,
                    repetition =
                        1,
                    expectedAvailable =
                        case.expectedAvailable,
                    expectedSpeechRateScore =
                        case.expectedSpeechRateScore,
                    expectedRmsScore =
                        case.expectedRmsScore,
                    expectedVoiceScore =
                        case.expectedVoiceScore,
                    analysis =
                        case.analysis
                )
            )
        }
    }

    // ---------------------------------------------------------------------
    // Pause and hesitation independence
    // ---------------------------------------------------------------------

    private fun runPauseAndHesitationIndependence(
        participant: VirtualParticipant,
        rows: MutableList<RecordRow>
    ) {

        val currentRate =
            currentRateForWeightedDeviation(
                baselineRate =
                    participant.baselineSpeechRate,
                weightedDeviation =
                    0.35,
                direction =
                    SpeechDirection.FASTER
            )

        val quietMetadataAnalysis =
            createReliableAnalysis(
                speechRate =
                    currentRate,
                rmsVariation =
                    participant.baselineRmsVariation *
                            1.60,
                pauseCount =
                    0,
                hesitationCount =
                    0
            )

        val manyMetadataAnalysis =
            createReliableAnalysis(
                speechRate =
                    currentRate,
                rmsVariation =
                    participant.baselineRmsVariation *
                            1.60,
                pauseCount =
                    25,
                hesitationCount =
                    18
            )

        val firstResult =
            scoreRecording(
                analysis =
                    quietMetadataAnalysis,
                baseline =
                    participant.toBaseline()
            )

        val secondResult =
            scoreRecording(
                analysis =
                    manyMetadataAnalysis,
                baseline =
                    participant.toBaseline()
            )

        assertEquals(
            "Pause and hesitation metadata changed the voice score.",
            firstResult.voiceScore,
            secondResult.voiceScore
        )

        rows.add(
            firstResult.toRecordRow(
                testGroup =
                    GROUP_METADATA_INDEPENDENCE,
                participant =
                    participant,
                scenario =
                    "NO_PAUSES_OR_HESITATIONS",
                repetition =
                    1,
                expectedAvailable =
                    true,
                expectedSpeechRateScore =
                    1,
                expectedRmsScore =
                    1,
                expectedVoiceScore =
                    2,
                analysis =
                    quietMetadataAnalysis
            )
        )

        rows.add(
            secondResult.toRecordRow(
                testGroup =
                    GROUP_METADATA_INDEPENDENCE,
                participant =
                    participant,
                scenario =
                    "MANY_PAUSES_AND_HESITATIONS",
                repetition =
                    1,
                expectedAvailable =
                    true,
                expectedSpeechRateScore =
                    1,
                expectedRmsScore =
                    1,
                expectedVoiceScore =
                    2,
                analysis =
                    manyMetadataAnalysis
            )
        )
    }

    // ---------------------------------------------------------------------
    // Boundary evaluation
    // ---------------------------------------------------------------------

    private fun runBoundaryEvaluation(
        participant: VirtualParticipant,
        rows: MutableList<BoundaryRow>
    ) {

        val speechBoundaryCases =
            listOf(
                SpeechBoundaryCase(
                    name =
                        "SPEECH_BELOW_MILD",
                    targetWeightedDeviation =
                        0.299,
                    expectedScore =
                        0
                ),

                SpeechBoundaryCase(
                    name =
                        "SPEECH_ABOVE_MILD",
                    targetWeightedDeviation =
                        0.301,
                    expectedScore =
                        1
                ),

                SpeechBoundaryCase(
                    name =
                        "SPEECH_BELOW_HIGH",
                    targetWeightedDeviation =
                        0.499,
                    expectedScore =
                        1
                ),

                SpeechBoundaryCase(
                    name =
                        "SPEECH_ABOVE_HIGH",
                    targetWeightedDeviation =
                        0.501,
                    expectedScore =
                        2
                )
            )

        speechBoundaryCases.forEach { case ->

            val currentRate =
                currentRateForWeightedDeviation(
                    baselineRate =
                        participant.baselineSpeechRate,
                    weightedDeviation =
                        case.targetWeightedDeviation,
                    direction =
                        SpeechDirection.FASTER
                )

            val actualWeightedDeviation =
                weightedSpeechRateDeviation(
                    baselineRate =
                        participant.baselineSpeechRate,
                    currentRate =
                        currentRate
                )

            val actualScore =
                SpeechRateScorer
                    .calculateVoiceScore(
                        baselineSpeechRate =
                            participant
                                .baselineSpeechRate,
                        currentSpeechRate =
                            currentRate
                    )

            assertEquals(
                case.expectedScore,
                actualScore
            )

            rows.add(
                BoundaryRow(
                    participantId =
                        participant.id,
                    component =
                        "SPEECH_RATE",
                    boundaryCase =
                        case.name,
                    baselineValue =
                        participant
                            .baselineSpeechRate,
                    currentValue =
                        currentRate,
                    ratioOrWeightedDeviation =
                        actualWeightedDeviation,
                    expectedScore =
                        case.expectedScore,
                    actualScore =
                        actualScore,
                    passed =
                        actualScore ==
                                case.expectedScore
                )
            )
        }

        val rmsBoundaryCases =
            listOf(
                RmsBoundaryCase(
                    name =
                        "RMS_BELOW_MILD",
                    ratio =
                        1.499,
                    expectedScore =
                        0
                ),

                RmsBoundaryCase(
                    name =
                        "RMS_AT_MILD",
                    ratio =
                        1.500,
                    expectedScore =
                        1
                ),

                RmsBoundaryCase(
                    name =
                        "RMS_BELOW_HIGH",
                    ratio =
                        1.999,
                    expectedScore =
                        1
                ),

                RmsBoundaryCase(
                    name =
                        "RMS_AT_HIGH",
                    ratio =
                        2.000,
                    expectedScore =
                        2
                )
            )

        rmsBoundaryCases.forEach { case ->

            val currentValue =
                participant
                    .baselineRmsVariation *
                        case.ratio

            val actualScore =
                VoiceRmsScorer
                    .calculateScore(
                        baselineVariation =
                            participant
                                .baselineRmsVariation,
                        currentVariation =
                            currentValue
                    )

            assertEquals(
                case.expectedScore,
                actualScore
            )

            rows.add(
                BoundaryRow(
                    participantId =
                        participant.id,
                    component =
                        "RMS_VARIATION",
                    boundaryCase =
                        case.name,
                    baselineValue =
                        participant
                            .baselineRmsVariation,
                    currentValue =
                        currentValue,
                    ratioOrWeightedDeviation =
                        currentValue /
                                participant
                                    .baselineRmsVariation,
                    expectedScore =
                        case.expectedScore,
                    actualScore =
                        actualScore,
                    passed =
                        actualScore ==
                                case.expectedScore
                )
            )
        }
    }

    // ---------------------------------------------------------------------
    // Severity evaluation
    // ---------------------------------------------------------------------

    private fun runSeverityEvaluation(
        participant: VirtualParticipant,
        recordRows: MutableList<RecordRow>,
        severityRows: MutableList<SeverityRow>
    ) {

        listOf(
            SpeechDirection.FASTER,
            SpeechDirection.SLOWER
        ).forEach { direction ->

            repeat(
                SEVERITY_REPETITIONS
            ) { repetitionIndex ->

                val configurations =
                    listOf(
                        SeverityConfiguration(
                            expectedLevel =
                                0,
                            weightedSpeechDeviation =
                                0.10,
                            rmsRatio =
                                1.00
                        ),

                        SeverityConfiguration(
                            expectedLevel =
                                1,
                            weightedSpeechDeviation =
                                0.35,
                            rmsRatio =
                                1.00
                        ),

                        SeverityConfiguration(
                            expectedLevel =
                                2,
                            weightedSpeechDeviation =
                                0.35,
                            rmsRatio =
                                1.60
                        ),

                        SeverityConfiguration(
                            expectedLevel =
                                3,
                            weightedSpeechDeviation =
                                0.55,
                            rmsRatio =
                                1.60
                        ),

                        SeverityConfiguration(
                            expectedLevel =
                                4,
                            weightedSpeechDeviation =
                                0.55,
                            rmsRatio =
                                2.20
                        )
                    )

                val actualLevels =
                    mutableListOf<Int>()

                configurations.forEach { configuration ->

                    val currentRate =
                        currentRateForWeightedDeviation(
                            baselineRate =
                                participant
                                    .baselineSpeechRate,
                            weightedDeviation =
                                configuration
                                    .weightedSpeechDeviation,
                            direction =
                                direction
                        )

                    val analysis =
                        createReliableAnalysis(
                            speechRate =
                                currentRate,
                            rmsVariation =
                                participant
                                    .baselineRmsVariation *
                                        configuration.rmsRatio,
                            pauseCount =
                                repetitionIndex,
                            hesitationCount =
                                repetitionIndex
                        )

                    val result =
                        scoreRecording(
                            analysis =
                                analysis,
                            baseline =
                                participant.toBaseline()
                        )

                    assertEquals(
                        configuration.expectedLevel,
                        result.voiceScore
                    )

                    actualLevels.add(
                        result.voiceScore
                            ?: -1
                    )

                    recordRows.add(
                        result.toRecordRow(
                            testGroup =
                                GROUP_SEVERITY,
                            participant =
                                participant,
                            scenario =
                                "${direction.name}_LEVEL_${configuration.expectedLevel}",
                            repetition =
                                repetitionIndex + 1,
                            expectedAvailable =
                                true,
                            expectedSpeechRateScore =
                                when (
                                    configuration
                                        .expectedLevel
                                ) {
                                    0 ->
                                        0

                                    1,
                                    2 ->
                                        1

                                    else ->
                                        2
                                },
                            expectedRmsScore =
                                when (
                                    configuration
                                        .expectedLevel
                                ) {
                                    0,
                                    1 ->
                                        0

                                    2,
                                    3 ->
                                        1

                                    else ->
                                        2
                                },
                            expectedVoiceScore =
                                configuration
                                    .expectedLevel,
                            analysis =
                                analysis
                        )
                    )
                }

                val monotonic =
                    actualLevels
                        .zipWithNext()
                        .all { pair ->
                            pair.second >=
                                    pair.first
                        }

                val exactExpectedSequence =
                    actualLevels ==
                            listOf(
                                0,
                                1,
                                2,
                                3,
                                4
                            )

                assertTrue(
                    "Voice severity was not monotonic.",
                    monotonic
                )

                assertTrue(
                    "Voice severity did not produce levels 0 through 4.",
                    exactExpectedSequence
                )

                severityRows.add(
                    SeverityRow(
                        participantId =
                            participant.id,
                        direction =
                            direction.name,
                        repetition =
                            repetitionIndex + 1,
                        level0Score =
                            actualLevels[0],
                        level1Score =
                            actualLevels[1],
                        level2Score =
                            actualLevels[2],
                        level3Score =
                            actualLevels[3],
                        level4Score =
                            actualLevels[4],
                        monotonic =
                            monotonic,
                        exactExpectedSequence =
                            exactExpectedSequence
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Production-like scoring
    // ---------------------------------------------------------------------

    private fun scoreRecording(
        analysis: SpeechAnalysisResult,
        baseline: VoiceBaseline
    ): ScoreResult {

        if (
            !analysis.isReliable
        ) {
            return ScoreResult(
                available =
                    false,
                failureReason =
                    "UNRELIABLE_RECORDING",
                speechRateScore =
                    null,
                rmsScore =
                    null,
                voiceScore =
                    null
            )
        }

        val baselineSpeechRate =
            baseline.speechRate

        val baselineRmsVariation =
            baseline.rmsVariation

        if (
            baselineSpeechRate ==
            null
        ) {
            return ScoreResult(
                available =
                    false,
                failureReason =
                    "MISSING_SPEECH_RATE_BASELINE",
                speechRateScore =
                    null,
                rmsScore =
                    null,
                voiceScore =
                    null
            )
        }

        if (
            baselineRmsVariation ==
            null
        ) {
            return ScoreResult(
                available =
                    false,
                failureReason =
                    "MISSING_RMS_BASELINE",
                speechRateScore =
                    null,
                rmsScore =
                    null,
                voiceScore =
                    null
            )
        }

        val speechRateScore =
            SpeechRateScorer
                .calculateVoiceScore(
                    baselineSpeechRate =
                        baselineSpeechRate,
                    currentSpeechRate =
                        analysis
                            .speechRateWordsPerSecond
                )

        val rmsScore =
            VoiceRmsScorer
                .calculateScore(
                    baselineVariation =
                        baselineRmsVariation,
                    currentVariation =
                        analysis
                            .rmsVariation
                            .toDouble()
                )

        val voiceScore =
            (
                    speechRateScore +
                            rmsScore
                    ).coerceIn(
                    0,
                    4
                )

        return ScoreResult(
            available =
                true,
            failureReason =
                null,
            speechRateScore =
                speechRateScore,
            rmsScore =
                rmsScore,
            voiceScore =
                voiceScore
        )
    }

    // ---------------------------------------------------------------------
    // Synthetic analysis creation
    // ---------------------------------------------------------------------

    private fun createReliableAnalysis(
        speechRate: Double,
        rmsVariation: Double,
        pauseCount: Int = 0,
        hesitationCount: Int = 0
    ): SpeechAnalysisResult {

        val durationSeconds =
            RELIABLE_DURATION_SECONDS

        val wordCount =
            max(
                1,
                (
                        speechRate *
                                durationSeconds
                        ).toInt()
            )

        return createAnalysis(
            durationSeconds =
                durationSeconds,
            wordCount =
                wordCount,
            speechRate =
                speechRate,
            rmsVariation =
                rmsVariation,
            isReliable =
                true,
            pauseCount =
                pauseCount,
            hesitationCount =
                hesitationCount
        )
    }

    private fun createAnalysis(
        durationSeconds: Double,
        wordCount: Int,
        speechRate: Double,
        rmsVariation: Double,
        isReliable: Boolean,
        pauseCount: Int = 0,
        hesitationCount: Int = 0
    ): SpeechAnalysisResult {

        val text =
            if (
                wordCount <=
                0
            ) {
                ""
            } else {
                List(
                    wordCount
                ) {
                    "מילה"
                }.joinToString(
                    " "
                )
            }

        val pauseDurations =
            List(
                pauseCount
            ) {
                1_500L +
                        it *
                        100L
            }

        val averagePause =
            if (
                pauseDurations.isEmpty()
            ) {
                0.0
            } else {
                pauseDurations.average()
            }

        return SpeechAnalysisResult(
            finalText =
                text,
            durationSeconds =
                durationSeconds,
            isReliable =
                isReliable,
            speechRateWordsPerSecond =
                speechRate,
            averageRms =
                5.0f,
            maxRms =
                (
                        5.0 +
                                rmsVariation
                        ).toFloat(),
            rmsVariation =
                rmsVariation.toFloat(),
            pauseCount =
                pauseCount,
            pauseDurationsMs =
                pauseDurations,
            averagePauseMs =
                averagePause,
            hesitationCount =
                hesitationCount
        )
    }

    // ---------------------------------------------------------------------
    // Speech-rate mathematics
    // ---------------------------------------------------------------------

    private fun currentRateForWeightedDeviation(
        baselineRate: Double,
        weightedDeviation: Double,
        direction: SpeechDirection
    ): Double {

        require(
            baselineRate >
                    0.0
        )

        require(
            weightedDeviation in
                    0.0..
                    0.95
        )

        val coefficient =
            0.7 /
                    baselineRate +
                    0.3 /
                    HEBREW_NORMAL_SPEECH_RATE

        return when (
            direction
        ) {
            SpeechDirection.FASTER ->
                (
                        weightedDeviation +
                                1.0
                        ) /
                        coefficient

            SpeechDirection.SLOWER ->
                (
                        1.0 -
                                weightedDeviation
                        ) /
                        coefficient
        }
    }

    private fun weightedSpeechRateDeviation(
        baselineRate: Double,
        currentRate: Double
    ): Double {

        val baselineDeviation =
            abs(
                currentRate -
                        baselineRate
            ) /
                    baselineRate

        val normalDeviation =
            abs(
                currentRate -
                        HEBREW_NORMAL_SPEECH_RATE
            ) /
                    HEBREW_NORMAL_SPEECH_RATE

        return 0.7 *
                baselineDeviation +
                0.3 *
                normalDeviation
    }

    // ---------------------------------------------------------------------
    // CSV conversion
    // ---------------------------------------------------------------------

    private fun ScoreResult.toRecordRow(
        testGroup: String,
        participant: VirtualParticipant,
        scenario: String,
        repetition: Int,
        expectedAvailable: Boolean,
        expectedSpeechRateScore: Int?,
        expectedRmsScore: Int?,
        expectedVoiceScore: Int?,
        analysis: SpeechAnalysisResult
    ): RecordRow {

        val baselineSpeechRate =
            participant
                .baselineSpeechRate

        val baselineRmsVariation =
            participant
                .baselineRmsVariation

        return RecordRow(
            testGroup =
                testGroup,
            participantId =
                participant.id,
            scenario =
                scenario,
            repetition =
                repetition,
            expectedAvailable =
                expectedAvailable,
            actualAvailable =
                available,
            failureReason =
                failureReason,
            isReliable =
                analysis.isReliable,
            durationSeconds =
                analysis.durationSeconds,
            recognizedWordCount =
                analysis.finalText
                    .trim()
                    .split(
                        "\\s+".toRegex()
                    )
                    .filter {
                        it.isNotBlank()
                    }
                    .size,
            baselineSpeechRate =
                baselineSpeechRate,
            currentSpeechRate =
                analysis
                    .speechRateWordsPerSecond,
            weightedSpeechRateDeviation =
                if (
                    analysis
                        .speechRateWordsPerSecond >
                    0.0
                ) {
                    weightedSpeechRateDeviation(
                        baselineRate =
                            baselineSpeechRate,
                        currentRate =
                            analysis
                                .speechRateWordsPerSecond
                    )
                } else {
                    null
                },
            expectedSpeechRateScore =
                expectedSpeechRateScore,
            actualSpeechRateScore =
                speechRateScore,
            baselineRmsVariation =
                baselineRmsVariation,
            currentRmsVariation =
                analysis
                    .rmsVariation
                    .toDouble(),
            rmsVariationRatio =
                if (
                    baselineRmsVariation >
                    0.0
                ) {
                    analysis
                        .rmsVariation /
                            baselineRmsVariation
                } else {
                    null
                },
            expectedRmsScore =
                expectedRmsScore,
            actualRmsScore =
                rmsScore,
            expectedVoiceScore =
                expectedVoiceScore,
            actualVoiceScore =
                voiceScore,
            matchesExpectedAvailability =
                available ==
                        expectedAvailable,
            matchesExpectedSpeechScore =
                speechRateScore ==
                        expectedSpeechRateScore,
            matchesExpectedRmsScore =
                rmsScore ==
                        expectedRmsScore,
            matchesExpectedVoiceScore =
                voiceScore ==
                        expectedVoiceScore,
            pauseCount =
                analysis.pauseCount,
            averagePauseMs =
                analysis.averagePauseMs,
            hesitationCount =
                analysis.hesitationCount
        )
    }

    // ---------------------------------------------------------------------
    // CSV writing
    // ---------------------------------------------------------------------

    private fun writeRecordCsv(
        file: File,
        rows: List<RecordRow>
    ) {

        val header =
            listOf(
                "testGroup",
                "participantId",
                "scenario",
                "repetition",
                "expectedAvailable",
                "actualAvailable",
                "failureReason",
                "isReliable",
                "durationSeconds",
                "recognizedWordCount",
                "baselineSpeechRate",
                "currentSpeechRate",
                "weightedSpeechRateDeviation",
                "expectedSpeechRateScore",
                "actualSpeechRateScore",
                "baselineRmsVariation",
                "currentRmsVariation",
                "rmsVariationRatio",
                "expectedRmsScore",
                "actualRmsScore",
                "expectedVoiceScore",
                "actualVoiceScore",
                "matchesExpectedAvailability",
                "matchesExpectedSpeechScore",
                "matchesExpectedRmsScore",
                "matchesExpectedVoiceScore",
                "pauseCount",
                "averagePauseMs",
                "hesitationCount"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(
                    ","
                )
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.testGroup,
                        row.participantId,
                        row.scenario,
                        row.repetition,
                        row.expectedAvailable,
                        row.actualAvailable,
                        row.failureReason,
                        row.isReliable,
                        row.durationSeconds,
                        row.recognizedWordCount,
                        row.baselineSpeechRate,
                        row.currentSpeechRate,
                        row.weightedSpeechRateDeviation,
                        row.expectedSpeechRateScore,
                        row.actualSpeechRateScore,
                        row.baselineRmsVariation,
                        row.currentRmsVariation,
                        row.rmsVariationRatio,
                        row.expectedRmsScore,
                        row.actualRmsScore,
                        row.expectedVoiceScore,
                        row.actualVoiceScore,
                        row.matchesExpectedAvailability,
                        row.matchesExpectedSpeechScore,
                        row.matchesExpectedRmsScore,
                        row.matchesExpectedVoiceScore,
                        row.pauseCount,
                        row.averagePauseMs,
                        row.hesitationCount
                    ).toCsvLine()
                )
            }
        }
    }

    private fun writeBoundaryCsv(
        file: File,
        rows: List<BoundaryRow>
    ) {

        val header =
            listOf(
                "participantId",
                "component",
                "boundaryCase",
                "baselineValue",
                "currentValue",
                "ratioOrWeightedDeviation",
                "expectedScore",
                "actualScore",
                "passed"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(
                    ","
                )
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.participantId,
                        row.component,
                        row.boundaryCase,
                        row.baselineValue,
                        row.currentValue,
                        row.ratioOrWeightedDeviation,
                        row.expectedScore,
                        row.actualScore,
                        row.passed
                    ).toCsvLine()
                )
            }
        }
    }

    private fun writeSeverityCsv(
        file: File,
        rows: List<SeverityRow>
    ) {

        val header =
            listOf(
                "participantId",
                "direction",
                "repetition",
                "level0Score",
                "level1Score",
                "level2Score",
                "level3Score",
                "level4Score",
                "monotonic",
                "exactExpectedSequence"
            )

        file.bufferedWriter().use { writer ->

            writer.appendLine(
                header.joinToString(
                    ","
                )
            )

            rows.forEach { row ->

                writer.appendLine(
                    listOf(
                        row.participantId,
                        row.direction,
                        row.repetition,
                        row.level0Score,
                        row.level1Score,
                        row.level2Score,
                        row.level3Score,
                        row.level4Score,
                        row.monotonic,
                        row.exactExpectedSequence
                    ).toCsvLine()
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------

    private fun buildSummary(
        recordRows: List<RecordRow>,
        boundaryRows: List<BoundaryRow>,
        severityRows: List<SeverityRow>
    ): String {

        val controlledRows =
            recordRows.filter {
                it.testGroup ==
                        GROUP_CONTROLLED
            }

        val combinationRows =
            recordRows.filter {
                it.testGroup ==
                        GROUP_COMBINATION_MATRIX
            }

        val robustnessRows =
            recordRows.filter {
                it.testGroup ==
                        GROUP_ROBUSTNESS
            }

        val metadataRows =
            recordRows.filter {
                it.testGroup ==
                        GROUP_METADATA_INDEPENDENCE
            }

        val exactControlled =
            controlledRows.count {
                it.matchesExpectedAvailability &&
                        it.matchesExpectedSpeechScore &&
                        it.matchesExpectedRmsScore &&
                        it.matchesExpectedVoiceScore
            }

        val exactCombinations =
            combinationRows.count {
                it.matchesExpectedAvailability &&
                        it.matchesExpectedSpeechScore &&
                        it.matchesExpectedRmsScore &&
                        it.matchesExpectedVoiceScore
            }

        val robustnessPassed =
            robustnessRows.count {
                it.matchesExpectedAvailability &&
                        it.matchesExpectedSpeechScore &&
                        it.matchesExpectedRmsScore &&
                        it.matchesExpectedVoiceScore
            }

        val metadataPassed =
            metadataRows.count {
                it.matchesExpectedVoiceScore
            }

        val boundariesPassed =
            boundaryRows.count {
                it.passed
            }

        val monotonicSequences =
            severityRows.count {
                it.monotonic
            }

        val exactSequences =
            severityRows.count {
                it.exactExpectedSequence
            }

        val expectedScoredRows =
            recordRows.filter {
                it.expectedAvailable &&
                        it.expectedVoiceScore !=
                        null
            }

        val truePositive =
            expectedScoredRows.count {
                (
                        it.expectedVoiceScore
                            ?: 0
                        ) >
                        0 &&
                        (
                                it.actualVoiceScore
                                    ?: 0
                                ) >
                        0
            }

        val trueNegative =
            expectedScoredRows.count {
                it.expectedVoiceScore ==
                        0 &&
                        it.actualVoiceScore ==
                        0
            }

        val falsePositive =
            expectedScoredRows.count {
                it.expectedVoiceScore ==
                        0 &&
                        (
                                it.actualVoiceScore
                                    ?: 0
                                ) >
                        0
            }

        val falseNegative =
            expectedScoredRows.count {
                (
                        it.expectedVoiceScore
                            ?: 0
                        ) >
                        0 &&
                        (
                                it.actualVoiceScore
                                    ?: 0
                                ) ==
                        0
            }

        val accuracy =
            safeRatio(
                truePositive +
                        trueNegative,
                truePositive +
                        trueNegative +
                        falsePositive +
                        falseNegative
            )

        val sensitivity =
            safeRatio(
                truePositive,
                truePositive +
                        falseNegative
            )

        val specificity =
            safeRatio(
                trueNegative,
                trueNegative +
                        falsePositive
            )

        val precision =
            safeRatio(
                truePositive,
                truePositive +
                        falsePositive
            )

        val f1 =
            if (
                precision +
                sensitivity >
                0.0
            ) {
                2.0 *
                        precision *
                        sensitivity /
                        (
                                precision +
                                        sensitivity
                                )
            } else {
                0.0
            }

        return """
        Complete synthetic voice evaluation finished.

        Virtual participants: ${virtualParticipants().size}
        Total record rows: ${recordRows.size}
        Boundary rows: ${boundaryRows.size}
        Severity sequences: ${severityRows.size}

        Controlled scenario runs matching all expected scores:
        $exactControlled / ${controlledRows.size} (${percentage(exactControlled, controlledRows.size)})

        Component-score combinations matching exactly:
        $exactCombinations / ${combinationRows.size} (${percentage(exactCombinations, combinationRows.size)})

        Reliability and robustness cases matching exactly:
        $robustnessPassed / ${robustnessRows.size} (${percentage(robustnessPassed, robustnessRows.size)})

        Pause and hesitation independence checks:
        $metadataPassed / ${metadataRows.size} (${percentage(metadataPassed, metadataRows.size)})

        Boundary checks passed:
        $boundariesPassed / ${boundaryRows.size} (${percentage(boundariesPassed, boundaryRows.size)})

        Monotonic severity sequences:
        $monotonicSequences / ${severityRows.size} (${percentage(monotonicSequences, severityRows.size)})

        Exact severity sequences 0 -> 1 -> 2 -> 3 -> 4:
        $exactSequences / ${severityRows.size} (${percentage(exactSequences, severityRows.size)})

        Controlled synthetic binary confusion matrix:
        TP = $truePositive
        TN = $trueNegative
        FP = $falsePositive
        FN = $falseNegative

        Accuracy = ${formatPercent(accuracy)}
        Sensitivity = ${formatPercent(sensitivity)}
        Specificity = ${formatPercent(specificity)}
        Precision = ${formatPercent(precision)}
        F1-score = ${formatPercent(f1)}

        Important:
        The current voice baseline is created once and is not adapted
        across later uses. Therefore, no baseline-convergence claim is
        made for the voice modality.

        Interpretation note:
        These are controlled engineering tests of the current scoring
        rules. They do not establish clinical voice-distress accuracy.
        """.trimIndent()
    }

    // ---------------------------------------------------------------------
    // General utilities
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
            "build/synthetic-voice-evaluation"
        ).apply {

            check(
                exists() ||
                        mkdirs()
            ) {
                "Could not create output directory: $absolutePath"
            }
        }
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
            text.contains(
                ","
            ) ||
            text.contains(
                "\""
            ) ||
            text.contains(
                "\n"
            )
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

    private fun safeRatio(
        numerator: Int,
        denominator: Int
    ): Double {

        return if (
            denominator ==
            0
        ) {
            0.0
        } else {
            numerator.toDouble() /
                    denominator.toDouble()
        }
    }

    private fun percentage(
        numerator: Int,
        denominator: Int
    ): String {

        return formatPercent(
            safeRatio(
                numerator =
                    numerator,
                denominator =
                    denominator
            )
        )
    }

    private fun formatPercent(
        ratio: Double
    ): String {

        return String.format(
            Locale.US,
            "%.2f%%",
            ratio *
                    100.0
        )
    }

    // ---------------------------------------------------------------------
    // Models
    // ---------------------------------------------------------------------

    private enum class SpeechDirection {
        FASTER,
        SLOWER
    }

    private data class VirtualParticipant(
        val id: String,
        val baselineSpeechRate: Double,
        val baselineRmsVariation: Double
    ) {

        fun toBaseline():
                VoiceBaseline {

            return VoiceBaseline(
                speechRate =
                    baselineSpeechRate,
                rmsVariation =
                    baselineRmsVariation
            )
        }
    }

    private data class VoiceBaseline(
        val speechRate: Double?,
        val rmsVariation: Double?
    )

    private data class Scenario(
        val name: String,
        val expectedSpeechRateScore: Int,
        val expectedRmsScore: Int,
        val speechDeviation: Double,
        val speechDirection: SpeechDirection,
        val rmsRatio: Double,
        val expectedAvailable: Boolean
    )

    private data class RobustnessCase(
        val scenario: String,
        val analysis: SpeechAnalysisResult,
        val baseline: VoiceBaseline,
        val expectedAvailable: Boolean,
        val expectedSpeechRateScore: Int?,
        val expectedRmsScore: Int?,
        val expectedVoiceScore: Int?
    )

    private data class SpeechBoundaryCase(
        val name: String,
        val targetWeightedDeviation: Double,
        val expectedScore: Int
    )

    private data class RmsBoundaryCase(
        val name: String,
        val ratio: Double,
        val expectedScore: Int
    )

    private data class SeverityConfiguration(
        val expectedLevel: Int,
        val weightedSpeechDeviation: Double,
        val rmsRatio: Double
    )

    private data class ScoreResult(
        val available: Boolean,
        val failureReason: String?,
        val speechRateScore: Int?,
        val rmsScore: Int?,
        val voiceScore: Int?
    )

    private data class RecordRow(
        val testGroup: String,
        val participantId: String,
        val scenario: String,
        val repetition: Int,
        val expectedAvailable: Boolean,
        val actualAvailable: Boolean,
        val failureReason: String?,
        val isReliable: Boolean,
        val durationSeconds: Double,
        val recognizedWordCount: Int,
        val baselineSpeechRate: Double,
        val currentSpeechRate: Double,
        val weightedSpeechRateDeviation: Double?,
        val expectedSpeechRateScore: Int?,
        val actualSpeechRateScore: Int?,
        val baselineRmsVariation: Double,
        val currentRmsVariation: Double,
        val rmsVariationRatio: Double?,
        val expectedRmsScore: Int?,
        val actualRmsScore: Int?,
        val expectedVoiceScore: Int?,
        val actualVoiceScore: Int?,
        val matchesExpectedAvailability: Boolean,
        val matchesExpectedSpeechScore: Boolean,
        val matchesExpectedRmsScore: Boolean,
        val matchesExpectedVoiceScore: Boolean,
        val pauseCount: Int,
        val averagePauseMs: Double,
        val hesitationCount: Int
    )

    private data class BoundaryRow(
        val participantId: String,
        val component: String,
        val boundaryCase: String,
        val baselineValue: Double,
        val currentValue: Double,
        val ratioOrWeightedDeviation: Double,
        val expectedScore: Int,
        val actualScore: Int,
        val passed: Boolean
    )

    private data class SeverityRow(
        val participantId: String,
        val direction: String,
        val repetition: Int,
        val level0Score: Int,
        val level1Score: Int,
        val level2Score: Int,
        val level3Score: Int,
        val level4Score: Int,
        val monotonic: Boolean,
        val exactExpectedSequence: Boolean
    )

    private fun virtualParticipants():
            List<VirtualParticipant> {

        return listOf(
            VirtualParticipant(
                id =
                    "VIRTUAL_01",
                baselineSpeechRate =
                    1.80,
                baselineRmsVariation =
                    0.80
            ),

            VirtualParticipant(
                id =
                    "VIRTUAL_02",
                baselineSpeechRate =
                    2.05,
                baselineRmsVariation =
                    1.20
            ),

            VirtualParticipant(
                id =
                    "VIRTUAL_03",
                baselineSpeechRate =
                    2.30,
                baselineRmsVariation =
                    1.70
            ),

            VirtualParticipant(
                id =
                    "VIRTUAL_04",
                baselineSpeechRate =
                    2.50,
                baselineRmsVariation =
                    2.30
            )
        )
    }

    companion object {

        private const val HEBREW_NORMAL_SPEECH_RATE =
            2.57

        private const val RELIABLE_DURATION_SECONDS =
            15.0

        private const val CONTROLLED_REPETITIONS =
            3

        private const val SEVERITY_REPETITIONS =
            3

        private const val GROUP_CONTROLLED =
            "CONTROLLED"

        private const val GROUP_COMBINATION_MATRIX =
            "COMPONENT_COMBINATION_MATRIX"

        private const val GROUP_ROBUSTNESS =
            "ROBUSTNESS"

        private const val GROUP_METADATA_INDEPENDENCE =
            "PAUSE_HESITATION_INDEPENDENCE"

        private const val GROUP_SEVERITY =
            "SEVERITY"
    }
}