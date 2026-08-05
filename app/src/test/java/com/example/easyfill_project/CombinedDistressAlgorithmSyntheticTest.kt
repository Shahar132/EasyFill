package com.example.easyfill_project.distress_scoring

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Synthetic integration evaluation of the multimodal distress score.
 *
 * This test uses the production DistressScoringManager and
 * DistressConfirmationManager directly.
 *
 * Important mode behavior:
 *
 * FORM_FILLING combines:
 * - Form behavior = 30%
 * - Face = 35%
 * - Hand = 35%
 *
 * VOICE_RECORDING combines:
 * - Voice = 35%
 * - Face = 35%
 * - Hand = 30%
 *
 * The four sources are part of the complete system, but they are not all
 * weighted at the same moment. The active DistressMode chooses the relevant
 * three-source combination.
 *
 * The test verifies:
 *
 * 1. Every available-score combination from 0 to 4.
 * 2. Weight normalization when one or more modalities are unavailable.
 * 3. The difference between unavailable (null) and reliable calm (0).
 * 4. Input clamping to the valid range 0..4.
 * 5. Recording results are published only after all three components finish.
 * 6. Submission order does not change the recording result.
 * 7. Form-filling requires two consecutive matching non-zero windows.
 * 8. Voice-recording results are confirmed immediately.
 * 9. Unopened form suggestions can be upgraded only after confirmation.
 * 10. Opened suggestions are not upgraded.
 *
 * Output:
 *
 * app/build/synthetic-combined-distress-evaluation/
 * ├── synthetic_combined_distress_scores.csv
 * ├── synthetic_combined_distress_confirmation.csv
 * └── synthetic_combined_distress_summary.txt
 */
class CombinedDistressAlgorithmSyntheticTest {

    @Test
    fun generateCombinedDistressEvaluationDataset() = runBlocking {

        val outputDirectory =
            resolveOutputDirectory()

        val scoreRows =
            mutableListOf<ScoreRow>()

        val confirmationRows =
            mutableListOf<ConfirmationRow>()

        evaluateFormFillingFusion(
            rows =
                scoreRows
        )

        evaluateVoiceRecordingFusion(
            rows =
                scoreRows
        )

        evaluateInputClamping(
            rows =
                scoreRows
        )

        evaluateFormConfirmation(
            rows =
                confirmationRows
        )

        evaluateVoiceRecordingConfirmation(
            rows =
                confirmationRows
        )

        confirmationScopes.forEach { scope ->
            scope.cancel()
        }

        val scoreFile =
            File(
                outputDirectory,
                "synthetic_combined_distress_scores.csv"
            )

        val confirmationFile =
            File(
                outputDirectory,
                "synthetic_combined_distress_confirmation.csv"
            )

        val summaryFile =
            File(
                outputDirectory,
                "synthetic_combined_distress_summary.txt"
            )

        writeScoreCsv(
            file =
                scoreFile,
            rows =
                scoreRows
        )

        writeConfirmationCsv(
            file =
                confirmationFile,
            rows =
                confirmationRows
        )

        val summary =
            buildSummary(
                scoreRows =
                    scoreRows,
                confirmationRows =
                    confirmationRows
            )

        summaryFile.writeText(
            summary
        )

        println(summary)
        println()
        println(
            "Scores CSV: ${scoreFile.absolutePath}"
        )
        println(
            "Confirmation CSV: ${confirmationFile.absolutePath}"
        )
        println(
            "Summary: ${summaryFile.absolutePath}"
        )

        assertTrue(
            scoreFile.exists()
        )

        assertTrue(
            confirmationFile.exists()
        )

        assertTrue(
            summaryFile.exists()
        )
    }

    // ---------------------------------------------------------------------
    // FORM_FILLING weighted fusion
    // ---------------------------------------------------------------------

    private fun evaluateFormFillingFusion(
        rows: MutableList<ScoreRow>
    ) {

        val availabilityMasks =
            0..7

        availabilityMasks.forEach { mask ->

            val handAvailable =
                mask and
                        HAND_MASK !=
                        0

            val faceAvailable =
                mask and
                        FACE_MASK !=
                        0

            val formAvailable =
                mask and
                        FORM_MASK !=
                        0

            val handValues =
                if (
                    handAvailable
                ) {
                    0..4
                } else {
                    0..0
                }

            val faceValues =
                if (
                    faceAvailable
                ) {
                    0..4
                } else {
                    0..0
                }

            val formValues =
                if (
                    formAvailable
                ) {
                    0..4
                } else {
                    0..0
                }

            handValues.forEach { handScore ->
                faceValues.forEach { faceScore ->
                    formValues.forEach { formScore ->

                        resetFormScoringManager()

                        if (
                            formAvailable
                        ) {
                            DistressScoringManager
                                .updateFormBehaviorScore(
                                    formScore
                                )
                        }

                        if (
                            faceAvailable
                        ) {
                            DistressScoringManager
                                .updateFormFaceScore(
                                    score =
                                        faceScore.toFloat(),
                                    timestampMs =
                                        1_000L
                                )
                        }

                        if (
                            handAvailable
                        ) {
                            DistressScoringManager
                                .updateHandScore(
                                    handScore
                                )
                        }

                        val expectedWeightedScore =
                            normalizedWeightedScore(
                                components =
                                    listOf(
                                        OptionalWeightedScore(
                                            score =
                                                if (
                                                    formAvailable
                                                ) {
                                                    formScore.toDouble()
                                                } else {
                                                    null
                                                },
                                            weight =
                                                FORM_WEIGHT
                                        ),

                                        OptionalWeightedScore(
                                            score =
                                                if (
                                                    faceAvailable
                                                ) {
                                                    faceScore.toDouble()
                                                } else {
                                                    null
                                                },
                                            weight =
                                                FORM_FACE_WEIGHT
                                        ),

                                        OptionalWeightedScore(
                                            score =
                                                if (
                                                    handAvailable
                                                ) {
                                                    handScore.toDouble()
                                                } else {
                                                    null
                                                },
                                            weight =
                                                FORM_HAND_WEIGHT
                                        )
                                    )
                            ) ?: 0.0

                        val expectedLevel =
                            expectedWeightedScore
                                .roundToInt()
                                .coerceIn(
                                    0,
                                    4
                                )

                        val actualLevel =
                            DistressScoringManager
                                .totalScore
                                .value

                        assertEquals(
                            "FORM_FILLING weighted result mismatch.",
                            expectedLevel,
                            actualLevel
                        )

                        rows.add(
                            ScoreRow(
                                mode =
                                    DistressMode
                                        .FORM_FILLING
                                        .name,
                                scenarioType =
                                    "AVAILABILITY_AND_WEIGHTING",
                                availabilityMask =
                                    mask,
                                submissionOrder =
                                    null,

                                handAvailable =
                                    handAvailable,
                                faceAvailable =
                                    faceAvailable,
                                voiceAvailable =
                                    false,
                                formAvailable =
                                    formAvailable,

                                handInput =
                                    if (
                                        handAvailable
                                    ) {
                                        handScore.toDouble()
                                    } else {
                                        null
                                    },
                                faceInput =
                                    if (
                                        faceAvailable
                                    ) {
                                        faceScore.toDouble()
                                    } else {
                                        null
                                    },
                                voiceInput =
                                    null,
                                formInput =
                                    if (
                                        formAvailable
                                    ) {
                                        formScore.toDouble()
                                    } else {
                                        null
                                    },

                                expectedWeightedScore =
                                    expectedWeightedScore,
                                actualWeightedScore =
                                    null,

                                expectedLevel =
                                    expectedLevel,
                                actualLevel =
                                    actualLevel,

                                publishedBeforeAllCompleted =
                                    null,
                                finalMode =
                                    DistressScoringManager
                                        .mode
                                        .value
                                        .name,

                                passed =
                                    expectedLevel ==
                                            actualLevel
                            )
                        )
                    }
                }
            }
        }

        /*
         * Voice is a system source, but it must not participate
         * in FORM_FILLING fusion.
         */
        for (
        voiceDisplayScore in
        0..4
        ) {

            resetFormScoringManager()

            DistressScoringManager
                .updateFormBehaviorScore(
                    2
                )

            DistressScoringManager
                .updateFormFaceScore(
                    score =
                        2f,
                    timestampMs =
                        2_000L
                )

            DistressScoringManager
                .updateHandScore(
                    2
                )

            DistressScoringManager
                .updateVoiceScore(
                    voiceDisplayScore
                )

            val actualLevel =
                DistressScoringManager
                    .totalScore
                    .value

            assertEquals(
                "Voice incorrectly affected FORM_FILLING fusion.",
                2,
                actualLevel
            )

            rows.add(
                ScoreRow(
                    mode =
                        DistressMode
                            .FORM_FILLING
                            .name,
                    scenarioType =
                        "VOICE_EXCLUDED_IN_FORM_MODE",
                    availabilityMask =
                        7,
                    submissionOrder =
                        null,

                    handAvailable =
                        true,
                    faceAvailable =
                        true,
                    voiceAvailable =
                        true,
                    formAvailable =
                        true,

                    handInput =
                        2.0,
                    faceInput =
                        2.0,
                    voiceInput =
                        voiceDisplayScore
                            .toDouble(),
                    formInput =
                        2.0,

                    expectedWeightedScore =
                        2.0,
                    actualWeightedScore =
                        null,

                    expectedLevel =
                        2,
                    actualLevel =
                        actualLevel,

                    publishedBeforeAllCompleted =
                        null,
                    finalMode =
                        DistressScoringManager
                            .mode
                            .value
                            .name,

                    passed =
                        actualLevel ==
                                2
                )
            )
        }
    }

    // ---------------------------------------------------------------------
    // VOICE_RECORDING weighted fusion
    // ---------------------------------------------------------------------

    private suspend fun evaluateVoiceRecordingFusion(
        rows: MutableList<ScoreRow>
    ) = coroutineScope {

        val availabilityMasks =
            0..7

        var scenarioIndex =
            0

        availabilityMasks.forEach { mask ->

            val handAvailable =
                mask and
                        HAND_MASK !=
                        0

            val faceAvailable =
                mask and
                        FACE_MASK !=
                        0

            val voiceAvailable =
                mask and
                        VOICE_MASK !=
                        0

            val handValues =
                if (
                    handAvailable
                ) {
                    0..4
                } else {
                    0..0
                }

            val faceValues =
                if (
                    faceAvailable
                ) {
                    0..4
                } else {
                    0..0
                }

            val voiceValues =
                if (
                    voiceAvailable
                ) {
                    0..4
                } else {
                    0..0
                }

            handValues.forEach { handScore ->
                faceValues.forEach { faceScore ->
                    voiceValues.forEach { voiceScore ->

                        val handInput =
                            if (
                                handAvailable
                            ) {
                                handScore.toDouble()
                            } else {
                                null
                            }

                        val faceInput =
                            if (
                                faceAvailable
                            ) {
                                faceScore.toDouble()
                            } else {
                                null
                            }

                        val voiceInput =
                            if (
                                voiceAvailable
                            ) {
                                voiceScore
                            } else {
                                null
                            }

                        val order =
                            SUBMISSION_ORDERS[
                                scenarioIndex %
                                        SUBMISSION_ORDERS.size
                            ]

                        scenarioIndex +=
                            1

                        val deferredResult =
                            async(
                                start =
                                    CoroutineStart
                                        .UNDISPATCHED
                            ) {
                                withTimeout(
                                    2_000L
                                ) {
                                    DistressScoringManager
                                        .completedVoiceRecordings
                                        .first()
                                }
                            }

                        DistressScoringManager
                            .beginVoiceRecordingSession()

                        val submissions =
                            mapOf<String, () -> Unit>(
                                "HAND" to {
                                    DistressScoringManager
                                        .submitVoiceRecordingHandAverage(
                                            handInput
                                        )
                                },

                                "FACE" to {
                                    DistressScoringManager
                                        .submitVoiceRecordingFaceAverage(
                                            faceInput
                                        )
                                },

                                "VOICE" to {
                                    DistressScoringManager
                                        .submitVoiceRecordingVoiceScore(
                                            voiceInput
                                        )
                                }
                            )

                        submissions
                            .getValue(
                                order[0]
                            )
                            .invoke()

                        submissions
                            .getValue(
                                order[1]
                            )
                            .invoke()

                        val publishedBeforeAllCompleted =
                            deferredResult
                                .isCompleted

                        assertFalse(
                            "Recording result was published before all components completed.",
                            publishedBeforeAllCompleted
                        )

                        submissions
                            .getValue(
                                order[2]
                            )
                            .invoke()

                        val result =
                            deferredResult
                                .await()

                        val expectedWeightedScore =
                            normalizedWeightedScore(
                                components =
                                    listOf(
                                        OptionalWeightedScore(
                                            score =
                                                voiceInput
                                                    ?.toDouble(),
                                            weight =
                                                RECORDING_VOICE_WEIGHT
                                        ),

                                        OptionalWeightedScore(
                                            score =
                                                faceInput,
                                            weight =
                                                RECORDING_FACE_WEIGHT
                                        ),

                                        OptionalWeightedScore(
                                            score =
                                                handInput,
                                            weight =
                                                RECORDING_HAND_WEIGHT
                                        )
                                    )
                            ) ?: 0.0

                        val expectedLevel =
                            expectedWeightedScore
                                .roundToInt()
                                .coerceIn(
                                    0,
                                    4
                                )

                        assertEquals(
                            "VOICE_RECORDING final level mismatch.",
                            expectedLevel,
                            result.level
                        )

                        assertEquals(
                            "VOICE_RECORDING weighted score mismatch.",
                            expectedWeightedScore,
                            result.weightedScore,
                            DOUBLE_TOLERANCE
                        )

                        assertEquals(
                            voiceAvailable,
                            result.voiceAvailable
                        )

                        assertEquals(
                            faceAvailable,
                            result.faceAvailable
                        )

                        assertEquals(
                            handAvailable,
                            result.handAvailable
                        )

                        assertEquals(
                            "Recording manager did not return to FORM_FILLING.",
                            DistressMode.FORM_FILLING,
                            DistressScoringManager
                                .mode
                                .value
                        )

                        rows.add(
                            ScoreRow(
                                mode =
                                    DistressMode
                                        .VOICE_RECORDING
                                        .name,
                                scenarioType =
                                    "AVAILABILITY_WEIGHTING_AND_ORDER",
                                availabilityMask =
                                    mask,
                                submissionOrder =
                                    order.joinToString(
                                        ">"
                                    ),

                                handAvailable =
                                    handAvailable,
                                faceAvailable =
                                    faceAvailable,
                                voiceAvailable =
                                    voiceAvailable,
                                formAvailable =
                                    false,

                                handInput =
                                    handInput,
                                faceInput =
                                    faceInput,
                                voiceInput =
                                    voiceInput
                                        ?.toDouble(),
                                formInput =
                                    null,

                                expectedWeightedScore =
                                    expectedWeightedScore,
                                actualWeightedScore =
                                    result.weightedScore,

                                expectedLevel =
                                    expectedLevel,
                                actualLevel =
                                    result.level,

                                publishedBeforeAllCompleted =
                                    publishedBeforeAllCompleted,
                                finalMode =
                                    DistressScoringManager
                                        .mode
                                        .value
                                        .name,

                                passed =
                                    expectedLevel ==
                                            result.level &&
                                            !publishedBeforeAllCompleted &&
                                            DistressScoringManager
                                                .mode
                                                .value ==
                                            DistressMode
                                                .FORM_FILLING
                            )
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Input clamping
    // ---------------------------------------------------------------------

    private suspend fun evaluateInputClamping(
        rows: MutableList<ScoreRow>
    ) = coroutineScope {

        val formCases =
            listOf(
                Triple(
                    -5,
                    -3f,
                    -1
                ),

                Triple(
                    9,
                    7f,
                    12
                ),

                Triple(
                    -5,
                    8f,
                    2
                )
            )

        formCases.forEachIndexed { index, values ->

            resetFormScoringManager()

            DistressScoringManager
                .updateHandScore(
                    values.first
                )

            DistressScoringManager
                .updateFormFaceScore(
                    score =
                        values.second,
                    timestampMs =
                        10_000L +
                                index
                )

            DistressScoringManager
                .updateFormBehaviorScore(
                    values.third
                )

            val safeHand =
                values.first
                    .coerceIn(
                        0,
                        4
                    )

            val safeFace =
                values.second
                    .coerceIn(
                        0f,
                        4f
                    )

            val safeForm =
                values.third
                    .coerceIn(
                        0,
                        4
                    )

            val expectedWeighted =
                normalizedWeightedScore(
                    listOf(
                        OptionalWeightedScore(
                            safeForm.toDouble(),
                            FORM_WEIGHT
                        ),
                        OptionalWeightedScore(
                            safeFace.toDouble(),
                            FORM_FACE_WEIGHT
                        ),
                        OptionalWeightedScore(
                            safeHand.toDouble(),
                            FORM_HAND_WEIGHT
                        )
                    )
                ) ?: 0.0

            val expectedLevel =
                expectedWeighted
                    .roundToInt()
                    .coerceIn(
                        0,
                        4
                    )

            val actualLevel =
                DistressScoringManager
                    .totalScore
                    .value

            assertEquals(
                expectedLevel,
                actualLevel
            )

            rows.add(
                ScoreRow(
                    mode =
                        DistressMode
                            .FORM_FILLING
                            .name,
                    scenarioType =
                        "INPUT_CLAMPING",
                    availabilityMask =
                        7,
                    submissionOrder =
                        null,
                    handAvailable =
                        true,
                    faceAvailable =
                        true,
                    voiceAvailable =
                        false,
                    formAvailable =
                        true,
                    handInput =
                        values.first
                            .toDouble(),
                    faceInput =
                        values.second
                            .toDouble(),
                    voiceInput =
                        null,
                    formInput =
                        values.third
                            .toDouble(),
                    expectedWeightedScore =
                        expectedWeighted,
                    actualWeightedScore =
                        null,
                    expectedLevel =
                        expectedLevel,
                    actualLevel =
                        actualLevel,
                    publishedBeforeAllCompleted =
                        null,
                    finalMode =
                        DistressScoringManager
                            .mode
                            .value
                            .name,
                    passed =
                        expectedLevel ==
                                actualLevel
                )
            )
        }

        val deferredResult =
            async(
                start =
                    CoroutineStart
                        .UNDISPATCHED
            ) {
                withTimeout(
                    2_000L
                ) {
                    DistressScoringManager
                        .completedVoiceRecordings
                        .first()
                }
            }

        DistressScoringManager
            .beginVoiceRecordingSession()

        DistressScoringManager
            .submitVoiceRecordingHandAverage(
                -4.0
            )

        DistressScoringManager
            .submitVoiceRecordingFaceAverage(
                8.0
            )

        DistressScoringManager
            .submitVoiceRecordingVoiceScore(
                9
            )

        val result =
            deferredResult
                .await()

        val expectedWeighted =
            normalizedWeightedScore(
                listOf(
                    OptionalWeightedScore(
                        4.0,
                        RECORDING_VOICE_WEIGHT
                    ),
                    OptionalWeightedScore(
                        4.0,
                        RECORDING_FACE_WEIGHT
                    ),
                    OptionalWeightedScore(
                        0.0,
                        RECORDING_HAND_WEIGHT
                    )
                )
            ) ?: 0.0

        val expectedLevel =
            expectedWeighted
                .roundToInt()
                .coerceIn(
                    0,
                    4
                )

        assertEquals(
            expectedLevel,
            result.level
        )

        rows.add(
            ScoreRow(
                mode =
                    DistressMode
                        .VOICE_RECORDING
                        .name,
                scenarioType =
                    "INPUT_CLAMPING",
                availabilityMask =
                    7,
                submissionOrder =
                    "HAND>FACE>VOICE",
                handAvailable =
                    true,
                faceAvailable =
                    true,
                voiceAvailable =
                    true,
                formAvailable =
                    false,
                handInput =
                    -4.0,
                faceInput =
                    8.0,
                voiceInput =
                    9.0,
                formInput =
                    null,
                expectedWeightedScore =
                    expectedWeighted,
                actualWeightedScore =
                    result.weightedScore,
                expectedLevel =
                    expectedLevel,
                actualLevel =
                    result.level,
                publishedBeforeAllCompleted =
                    false,
                finalMode =
                    DistressScoringManager
                        .mode
                        .value
                        .name,
                passed =
                    expectedLevel ==
                            result.level
            )
        )
    }

    // ---------------------------------------------------------------------
    // Confirmation behavior
    // ---------------------------------------------------------------------

    private fun evaluateFormConfirmation(
        rows: MutableList<ConfirmationRow>
    ) {

        for (
        level in
        1..4
        ) {

            val singleWindowManager =
                confirmationManager()

            singleWindowManager
                .processLevel(
                    level
                )

            rows.add(
                confirmationRow(
                    scenario =
                        "ONE_WINDOW_NOT_ENOUGH_LEVEL_$level",
                    source =
                        "FORM_FILLING",
                    inputSequence =
                        listOf(
                            level
                        ),
                    expectedConfirmedLevel =
                        0,
                    manager =
                        singleWindowManager,
                    expectedEventType =
                        null
                )
            )

            val twoWindowManager =
                confirmationManager()

            twoWindowManager
                .processLevel(
                    level
                )

            twoWindowManager
                .processLevel(
                    level
                )

            rows.add(
                confirmationRow(
                    scenario =
                        "TWO_MATCHING_WINDOWS_CONFIRM_LEVEL_$level",
                    source =
                        "FORM_FILLING",
                    inputSequence =
                        listOf(
                            level,
                            level
                        ),
                    expectedConfirmedLevel =
                        level,
                    manager =
                        twoWindowManager,
                    expectedEventType =
                        "ShowDefaultSuggestion"
                )
            )
        }

        val mismatchManager =
            confirmationManager()

        listOf(
            1,
            2,
            1
        ).forEach {
            mismatchManager
                .processLevel(
                    it
                )
        }

        rows.add(
            confirmationRow(
                scenario =
                    "NON_CONSECUTIVE_LEVELS_NOT_CONFIRMED",
                source =
                    "FORM_FILLING",
                inputSequence =
                    listOf(
                        1,
                        2,
                        1
                    ),
                expectedConfirmedLevel =
                    0,
                manager =
                    mismatchManager,
                expectedEventType =
                    null
            )
        )

        val upgradeManager =
            confirmationManager()

        listOf(
            1,
            1,
            2,
            2
        ).forEach {
            upgradeManager
                .processLevel(
                    it
                )
        }

        rows.add(
            confirmationRow(
                scenario =
                    "UNOPENED_SUGGESTION_UPGRADED",
                source =
                    "FORM_FILLING",
                inputSequence =
                    listOf(
                        1,
                        1,
                        2,
                        2
                    ),
                expectedConfirmedLevel =
                    2,
                manager =
                    upgradeManager,
                expectedEventType =
                    "ShowDefaultSuggestion",
                expectedEventLevel =
                    2
            )
        )

        val openedManager =
            confirmationManager()

        openedManager
            .processLevel(
                1
            )

        openedManager
            .processLevel(
                1
            )

        openedManager
            .onPendingSuggestionOpened()

        openedManager
            .processLevel(
                2
            )

        openedManager
            .processLevel(
                2
            )

        rows.add(
            confirmationRow(
                scenario =
                    "OPENED_SUGGESTION_NOT_UPGRADED",
                source =
                    "FORM_FILLING",
                inputSequence =
                    listOf(
                        1,
                        1,
                        2,
                        2
                    ),
                expectedConfirmedLevel =
                    1,
                manager =
                    openedManager,
                expectedEventType =
                    "ShowDefaultSuggestion",
                expectedEventLevel =
                    1
            )
        )

        val resetManager =
            confirmationManager()

        listOf(
            2,
            2,
            0
        ).forEach {
            resetManager
                .processLevel(
                    it
                )
        }

        rows.add(
            confirmationRow(
                scenario =
                    "LEVEL_ZERO_RESETS_MEASUREMENT",
                source =
                    "FORM_FILLING",
                inputSequence =
                    listOf(
                        2,
                        2,
                        0
                    ),
                expectedConfirmedLevel =
                    0,
                manager =
                    resetManager,
                expectedEventType =
                    "ShowDefaultSuggestion",
                expectedEventLevel =
                    2
            )
        )

        val highClampManager =
            confirmationManager()

        highClampManager
            .processLevel(
                9
            )

        highClampManager
            .processLevel(
                9
            )

        rows.add(
            confirmationRow(
                scenario =
                    "RAW_LEVEL_CLAMPED_TO_FOUR",
                source =
                    "FORM_FILLING",
                inputSequence =
                    listOf(
                        9,
                        9
                    ),
                expectedConfirmedLevel =
                    4,
                manager =
                    highClampManager,
                expectedEventType =
                    "ShowDefaultSuggestion",
                expectedEventLevel =
                    4
            )
        )
    }

    private fun evaluateVoiceRecordingConfirmation(
        rows: MutableList<ConfirmationRow>
    ) {

        for (
        level in
        0..4
        ) {

            val manager =
                confirmationManager()

            manager
                .processVoiceRecording(
                    VoiceRecordingDistressResult(
                        level =
                            level,
                        voiceScore =
                            level,
                        faceAverage =
                            level.toDouble(),
                        handAverage =
                            level.toDouble(),
                        voiceAvailable =
                            true,
                        faceAvailable =
                            true,
                        handAvailable =
                            true,
                        weightedScore =
                            level.toDouble()
                    )
                )

            rows.add(
                confirmationRow(
                    scenario =
                        "RECORDING_IMMEDIATE_LEVEL_$level",
                    source =
                        "VOICE_RECORDING",
                    inputSequence =
                        listOf(
                            level
                        ),
                    expectedConfirmedLevel =
                        level,
                    manager =
                        manager,
                    expectedEventType =
                        if (
                            level >
                            0
                        ) {
                            "ShowDefaultSuggestion"
                        } else {
                            null
                        },
                    expectedEventLevel =
                        if (
                            level >
                            0
                        ) {
                            level
                        } else {
                            null
                        }
                )
            )
        }
    }

    // ---------------------------------------------------------------------
    // Manager helpers
    // ---------------------------------------------------------------------

    private fun resetFormScoringManager() {

        DistressScoringManager
            .setMode(
                DistressMode
                    .FORM_FILLING
            )

        DistressScoringManager
            .clearFormHandScore()

        DistressScoringManager
            .clearFormFaceScore()

        DistressScoringManager
            .clearFormBehaviorScore()

        DistressScoringManager
            .updateVoiceScore(
                0
            )
    }

    private fun confirmationManager():
            DistressConfirmationManager {

        val scope =
            CoroutineScope(
                SupervisorJob() +
                        Dispatchers
                            .Unconfined
            )

        confirmationScopes.add(
            scope
        )

        return DistressConfirmationManager(
            scope =
                scope,
            requiredMatchingWindows =
                2
        )
    }

    private fun confirmationRow(
        scenario: String,
        source: String,
        inputSequence: List<Int>,
        expectedConfirmedLevel: Int,
        manager: DistressConfirmationManager,
        expectedEventType: String?,
        expectedEventLevel: Int? = null
    ): ConfirmationRow {

        val event =
            manager
                .uiEvent
                .value

        val actualEventType =
            event
                ?.javaClass
                ?.simpleName

        val actualEventLevel =
            when (
                event
            ) {
                is DistressUiEvent
                .ShowDefaultSuggestion ->
                    event.level

                is DistressUiEvent
                .ShowAlternativeSuggestion ->
                    event.level

                is DistressUiEvent
                .ShowCalmingMessage ->
                    event.level

                else ->
                    null
            }

        val actualConfirmedLevel =
            manager
                .confirmedLevel
                .value

        val passed =
            actualConfirmedLevel ==
                    expectedConfirmedLevel &&
                    actualEventType ==
                    expectedEventType &&
                    (
                            expectedEventLevel ==
                                    null ||
                                    actualEventLevel ==
                                    expectedEventLevel
                            )

        assertTrue(
            "Confirmation scenario failed: $scenario",
            passed
        )

        return ConfirmationRow(
            scenario =
                scenario,
            source =
                source,
            inputSequence =
                inputSequence
                    .joinToString(
                        ">"
                    ),
            expectedConfirmedLevel =
                expectedConfirmedLevel,
            actualConfirmedLevel =
                actualConfirmedLevel,
            expectedEventType =
                expectedEventType,
            actualEventType =
                actualEventType,
            expectedEventLevel =
                expectedEventLevel,
            actualEventLevel =
                actualEventLevel,
            passed =
                passed
        )
    }

    // ---------------------------------------------------------------------
    // Mathematics
    // ---------------------------------------------------------------------

    private fun normalizedWeightedScore(
        components: List<OptionalWeightedScore>
    ): Double? {

        val available =
            components.filter {
                it.score
                    ?.isFinite() ==
                        true &&
                        it.weight >
                        0.0
            }

        if (
            available.isEmpty()
        ) {
            return null
        }

        val totalWeight =
            available.sumOf {
                it.weight
            }

        if (
            totalWeight <=
            0.0
        ) {
            return null
        }

        return (
                available.sumOf {
                    it.score!! *
                            it.weight
                } /
                        totalWeight
                ).coerceIn(
                0.0,
                4.0
            )
    }

    // ---------------------------------------------------------------------
    // Output
    // ---------------------------------------------------------------------

    private fun writeScoreCsv(
        file: File,
        rows: List<ScoreRow>
    ) {

        val header =
            listOf(
                "mode",
                "scenarioType",
                "availabilityMask",
                "submissionOrder",
                "handAvailable",
                "faceAvailable",
                "voiceAvailable",
                "formAvailable",
                "handInput",
                "faceInput",
                "voiceInput",
                "formInput",
                "expectedWeightedScore",
                "actualWeightedScore",
                "expectedLevel",
                "actualLevel",
                "publishedBeforeAllCompleted",
                "finalMode",
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
                        row.mode,
                        row.scenarioType,
                        row.availabilityMask,
                        row.submissionOrder,
                        row.handAvailable,
                        row.faceAvailable,
                        row.voiceAvailable,
                        row.formAvailable,
                        row.handInput,
                        row.faceInput,
                        row.voiceInput,
                        row.formInput,
                        row.expectedWeightedScore,
                        row.actualWeightedScore,
                        row.expectedLevel,
                        row.actualLevel,
                        row.publishedBeforeAllCompleted,
                        row.finalMode,
                        row.passed
                    ).toCsvLine()
                )
            }
        }
    }

    private fun writeConfirmationCsv(
        file: File,
        rows: List<ConfirmationRow>
    ) {

        val header =
            listOf(
                "scenario",
                "source",
                "inputSequence",
                "expectedConfirmedLevel",
                "actualConfirmedLevel",
                "expectedEventType",
                "actualEventType",
                "expectedEventLevel",
                "actualEventLevel",
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
                        row.scenario,
                        row.source,
                        row.inputSequence,
                        row.expectedConfirmedLevel,
                        row.actualConfirmedLevel,
                        row.expectedEventType,
                        row.actualEventType,
                        row.expectedEventLevel,
                        row.actualEventLevel,
                        row.passed
                    ).toCsvLine()
                )
            }
        }
    }

    private fun buildSummary(
        scoreRows: List<ScoreRow>,
        confirmationRows: List<ConfirmationRow>
    ): String {

        val formRows =
            scoreRows.filter {
                it.mode ==
                        DistressMode
                            .FORM_FILLING
                            .name
            }

        val recordingRows =
            scoreRows.filter {
                it.mode ==
                        DistressMode
                            .VOICE_RECORDING
                            .name
            }

        val scoreRowsPassed =
            scoreRows.count {
                it.passed
            }

        val formRowsPassed =
            formRows.count {
                it.passed
            }

        val recordingRowsPassed =
            recordingRows.count {
                it.passed
            }

        val noPrematurePublication =
            recordingRows.count {
                it.publishedBeforeAllCompleted !=
                        true
            }

        val confirmationPassed =
            confirmationRows.count {
                it.passed
            }

        val expectedPositiveRows =
            scoreRows.filter {
                it.expectedLevel >
                        0
            }

        val expectedNegativeRows =
            scoreRows.filter {
                it.expectedLevel ==
                        0
            }

        val truePositive =
            expectedPositiveRows.count {
                it.actualLevel >
                        0
            }

        val falseNegative =
            expectedPositiveRows.count {
                it.actualLevel ==
                        0
            }

        val trueNegative =
            expectedNegativeRows.count {
                it.actualLevel ==
                        0
            }

        val falsePositive =
            expectedNegativeRows.count {
                it.actualLevel >
                        0
            }

        val accuracy =
            safeRatio(
                truePositive +
                        trueNegative,
                scoreRows.size
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
        Combined multimodal distress evaluation completed.

        Important mode structure:
        FORM_FILLING uses form behavior, face and hand.
        VOICE_RECORDING uses voice, face and hand.
        The four system sources are not weighted simultaneously.

        Total weighted-fusion rows:
        ${scoreRows.size}

        Weighted-fusion rows matching expected levels:
        $scoreRowsPassed / ${scoreRows.size} (${percentage(scoreRowsPassed, scoreRows.size)})

        FORM_FILLING rows matching expected levels:
        $formRowsPassed / ${formRows.size} (${percentage(formRowsPassed, formRows.size)})

        VOICE_RECORDING rows matching expected levels:
        $recordingRowsPassed / ${recordingRows.size} (${percentage(recordingRowsPassed, recordingRows.size)})

        Recording rows with no premature publication:
        $noPrematurePublication / ${recordingRows.size} (${percentage(noPrematurePublication, recordingRows.size)})

        Confirmation scenarios passed:
        $confirmationPassed / ${confirmationRows.size} (${percentage(confirmationPassed, confirmationRows.size)})

        Synthetic binary confusion matrix:
        TP = $truePositive
        TN = $trueNegative
        FP = $falsePositive
        FN = $falseNegative

        Accuracy = ${formatPercent(accuracy)}
        Sensitivity = ${formatPercent(sensitivity)}
        Specificity = ${formatPercent(specificity)}
        Precision = ${formatPercent(precision)}
        F1-score = ${formatPercent(f1)}

        Interpretation note:
        This test validates multimodal weighting, missing-modality
        normalization, completion order and confirmation behavior.
        It does not validate whether each source detected distress correctly;
        those source algorithms are evaluated separately.
        """.trimIndent()
    }

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
            "build/synthetic-combined-distress-evaluation"
        ).apply {
            check(
                exists() ||
                        mkdirs()
            )
        }
    }

    private fun List<Any?>.toCsvLine():
            String {

        return joinToString(
            separator =
                ","
        ) {
            csvValue(
                it
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
            denominator <=
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
    // Models and constants
    // ---------------------------------------------------------------------

    private data class OptionalWeightedScore(
        val score: Double?,
        val weight: Double
    )

    private data class ScoreRow(
        val mode: String,
        val scenarioType: String,
        val availabilityMask: Int,
        val submissionOrder: String?,

        val handAvailable: Boolean,
        val faceAvailable: Boolean,
        val voiceAvailable: Boolean,
        val formAvailable: Boolean,

        val handInput: Double?,
        val faceInput: Double?,
        val voiceInput: Double?,
        val formInput: Double?,

        val expectedWeightedScore: Double,
        val actualWeightedScore: Double?,

        val expectedLevel: Int,
        val actualLevel: Int,

        val publishedBeforeAllCompleted: Boolean?,
        val finalMode: String,

        val passed: Boolean
    )

    private data class ConfirmationRow(
        val scenario: String,
        val source: String,
        val inputSequence: String,
        val expectedConfirmedLevel: Int,
        val actualConfirmedLevel: Int,
        val expectedEventType: String?,
        val actualEventType: String?,
        val expectedEventLevel: Int?,
        val actualEventLevel: Int?,
        val passed: Boolean
    )

    companion object {

        private const val HAND_MASK =
            1

        private const val FACE_MASK =
            2

        private const val VOICE_MASK =
            4

        private const val FORM_MASK =
            4

        private const val FORM_WEIGHT =
            0.30

        private const val FORM_FACE_WEIGHT =
            0.35

        private const val FORM_HAND_WEIGHT =
            0.35

        private const val RECORDING_VOICE_WEIGHT =
            0.35

        private const val RECORDING_FACE_WEIGHT =
            0.35

        private const val RECORDING_HAND_WEIGHT =
            0.30

        private const val DOUBLE_TOLERANCE =
            1e-9

        private val SUBMISSION_ORDERS =
            listOf(
                listOf(
                    "HAND",
                    "FACE",
                    "VOICE"
                ),

                listOf(
                    "HAND",
                    "VOICE",
                    "FACE"
                ),

                listOf(
                    "FACE",
                    "HAND",
                    "VOICE"
                ),

                listOf(
                    "FACE",
                    "VOICE",
                    "HAND"
                ),

                listOf(
                    "VOICE",
                    "HAND",
                    "FACE"
                ),

                listOf(
                    "VOICE",
                    "FACE",
                    "HAND"
                )
            )

        private val confirmationScopes =
            mutableListOf<CoroutineScope>()
    }
}