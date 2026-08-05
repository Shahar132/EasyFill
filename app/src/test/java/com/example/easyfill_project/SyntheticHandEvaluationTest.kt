package com.example.easyfill_project

import com.example.easyfill_project.hand_analysis.HandTremorEvaluator
import com.example.easyfill_project.hand_analysis.MotionAnalysisResult
import com.example.easyfill_project.hand_analysis.MotionAnalyzer
import com.example.easyfill_project.hand_analysis.MotionBaselineProfile
import com.example.easyfill_project.hand_analysis.MotionBaselineWindowSummary
import com.example.easyfill_project.hand_analysis.TremorSpectrumAnalyzer
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * Controlled synthetic severity-response evaluation.
 *
 * The test does not claim that WEAK, MODERATE, STRONG and
 * VERY_STRONG are clinical human tremor levels.
 *
 * It checks whether increasing the same stable rhythmic signal,
 * relative to each virtual participant's personal noise level,
 * produces a non-decreasing severity index and score.
 */
class SyntheticHandSeverityTrendTest {

    private val sampleRateHz =
        100.0

    private val sampleIntervalNs =
        (
                1_000_000_000.0 /
                        sampleRateHz
                ).toLong()

    private val repetitionsPerFrequency =
        10

    /*
     * Frequencies are intentionally kept away from the known
     * 6 Hz and 13 Hz FFT boundary cases. This test focuses on
     * severity response rather than boundary classification.
     */
    private val frequenciesHz =
        listOf(
            7.0,
            8.0,
            9.0,
            11.0,
            12.0
        )

    private val participants =
        listOf(
            VirtualParticipant(
                id = "VIRTUAL_01",
                accelerationNoiseStd = 0.004,
                gyroscopeNoiseStd = 0.002,
                baselineSeed = 10_001L
            ),
            VirtualParticipant(
                id = "VIRTUAL_02",
                accelerationNoiseStd = 0.008,
                gyroscopeNoiseStd = 0.004,
                baselineSeed = 20_001L
            ),
            VirtualParticipant(
                id = "VIRTUAL_03",
                accelerationNoiseStd = 0.015,
                gyroscopeNoiseStd = 0.007,
                baselineSeed = 30_001L
            ),
            VirtualParticipant(
                id = "VIRTUAL_04",
                accelerationNoiseStd = 0.025,
                gyroscopeNoiseStd = 0.012,
                baselineSeed = 40_001L
            )
        )

    /*
     * Amplitudes are defined as multiples of each participant's
     * personal baseline noise. Therefore, every level represents
     * a comparable relative increase rather than one fixed raw
     * sensor value for every participant.
     */
    private val amplitudeLevels =
        listOf(
            AmplitudeLevel(
                name = "WEAK",
                order = 1,
                baselineNoiseMultiplier = 5.0
            ),
            AmplitudeLevel(
                name = "MODERATE",
                order = 2,
                baselineNoiseMultiplier = 10.0
            ),
            AmplitudeLevel(
                name = "STRONG",
                order = 3,
                baselineNoiseMultiplier = 20.0
            ),
            AmplitudeLevel(
                name = "VERY_STRONG",
                order = 4,
                baselineNoiseMultiplier = 40.0
            )
        )

    @Test
    fun generateSyntheticSeverityTrendDataset() {

        val outputDirectory =
            createOutputDirectory()

        val rows =
            mutableListOf<SeverityEvaluationRow>()

        participants.forEach { participant ->

            val profile =
                createSyntheticBaselineProfile(
                    participant = participant
                )

            frequenciesHz.forEachIndexed {
                    frequencyIndex,
                    frequencyHz ->

                repeat(
                    repetitionsPerFrequency
                ) { repetitionIndex ->

                    /*
                     * Every amplitude level in one sequence uses
                     * the same seed. This keeps phase and random
                     * noise identical, so amplitude is the main
                     * controlled difference between the levels.
                     */
                    val seed =
                        participant.baselineSeed +
                                frequencyIndex * 10_000L +
                                repetitionIndex

                    amplitudeLevels.forEach { amplitudeLevel ->

                        val accelerationAmplitude =
                            participant.accelerationNoiseStd *
                                    amplitudeLevel
                                        .baselineNoiseMultiplier

                        val gyroscopeAmplitude =
                            participant.gyroscopeNoiseStd *
                                    amplitudeLevel
                                        .baselineNoiseMultiplier

                        val current =
                            createFiveSecondStableTremorWindow(
                                participant = participant,
                                frequencyHz = frequencyHz,
                                accelerationAmplitude =
                                    accelerationAmplitude,
                                gyroscopeAmplitude =
                                    gyroscopeAmplitude,
                                seed = seed
                            )

                        check(current.isReliable) {
                            """
                            Synthetic severity window was unreliable.
                            participant=${participant.id}
                            frequencyHz=$frequencyHz
                            amplitudeLevel=${amplitudeLevel.name}
                            repetition=${repetitionIndex + 1}
                            duration=${current.durationSeconds}
                            """.trimIndent()
                        }

                        val evaluation =
                            HandTremorEvaluator.evaluate(
                                profile = profile,
                                current = current
                            )

                        rows.add(
                            SeverityEvaluationRow(
                                participantId =
                                    participant.id,
                                frequencyHz =
                                    frequencyHz,
                                repetition =
                                    repetitionIndex + 1,
                                randomSeed =
                                    seed,
                                amplitudeLevel =
                                    amplitudeLevel.name,
                                amplitudeOrder =
                                    amplitudeLevel.order,
                                baselineNoiseMultiplier =
                                    amplitudeLevel
                                        .baselineNoiseMultiplier,
                                accelerationAmplitude =
                                    accelerationAmplitude,
                                gyroscopeAmplitude =
                                    gyroscopeAmplitude,
                                tremorConfirmed =
                                    evaluation.tremorConfirmed,
                                score =
                                    evaluation.severity.score,
                                severityIndex =
                                    evaluation
                                        .severity
                                        .severityIndex,
                                measuredPeakFrequencyHz =
                                    evaluation
                                        .spectrum
                                        .peakFrequencyHz,
                                currentAccelerationP95 =
                                    current
                                        .accelerationP95
                                        .toDouble(),
                                currentGyroscopeP95 =
                                    current
                                        .gyroscopeP95
                                        .toDouble()
                            )
                        )
                    }
                }
            }
        }

        val sequenceRows =
            createSequenceSummaries(
                rows = rows
            )

        val totalSequences =
            sequenceRows.size

        val scoreMonotonicCount =
            sequenceRows.count {
                it.scoreMonotonic
            }

        val severityMonotonicCount =
            sequenceRows.count {
                it.severityIndexMonotonic
            }

        val allLevelsConfirmedCount =
            sequenceRows.count {
                it.allLevelsConfirmed
            }

        val scoreIncreasedCount =
            sequenceRows.count {
                it.scoreIncreasedAtLeastOnce
            }

        val rowCsvFile =
            File(
                outputDirectory,
                "synthetic_hand_severity_rows.csv"
            )

        val sequenceCsvFile =
            File(
                outputDirectory,
                "synthetic_hand_severity_sequences.csv"
            )

        val summaryFile =
            File(
                outputDirectory,
                "synthetic_hand_severity_summary.txt"
            )

        writeRowCsv(
            rows = rows,
            destination = rowCsvFile
        )

        writeSequenceCsv(
            rows = sequenceRows,
            destination = sequenceCsvFile
        )

        writeSummary(
            totalWindows = rows.size,
            totalSequences = totalSequences,
            scoreMonotonicCount =
                scoreMonotonicCount,
            severityMonotonicCount =
                severityMonotonicCount,
            allLevelsConfirmedCount =
                allLevelsConfirmedCount,
            scoreIncreasedCount =
                scoreIncreasedCount,
            destination = summaryFile
        )

        println(
            """

            Synthetic hand severity trend evaluation completed.

            Window evaluations: ${rows.size}
            Severity sequences: $totalSequences

            Score monotonic sequences:
            $scoreMonotonicCount / $totalSequences (${formatPercentage(scoreMonotonicCount, totalSequences)}%)

            Severity-index monotonic sequences:
            $severityMonotonicCount / $totalSequences (${formatPercentage(severityMonotonicCount, totalSequences)}%)

            All four levels confirmed as tremor:
            $allLevelsConfirmedCount / $totalSequences (${formatPercentage(allLevelsConfirmedCount, totalSequences)}%)

            Score increased at least once:
            $scoreIncreasedCount / $totalSequences (${formatPercentage(scoreIncreasedCount, totalSequences)}%)

            Row CSV:
            ${rowCsvFile.absolutePath}

            Sequence CSV:
            ${sequenceCsvFile.absolutePath}

            Summary:
            ${summaryFile.absolutePath}
            """.trimIndent()
        )
    }

    private fun createSequenceSummaries(
        rows: List<SeverityEvaluationRow>
    ): List<SeveritySequenceRow> {

        return rows
            .groupBy { row ->
                SequenceKey(
                    participantId =
                        row.participantId,
                    frequencyHz =
                        row.frequencyHz,
                    repetition =
                        row.repetition
                )
            }
            .map { (key, sequenceRows) ->

                val orderedRows =
                    sequenceRows
                        .sortedBy { row ->
                            row.amplitudeOrder
                        }

                check(
                    orderedRows.size ==
                            amplitudeLevels.size
                ) {
                    """
                    Incomplete severity sequence.
                    participant=${key.participantId}
                    frequencyHz=${key.frequencyHz}
                    repetition=${key.repetition}
                    rows=${orderedRows.size}
                    """.trimIndent()
                }

                val scores =
                    orderedRows.map { row ->
                        row.score
                    }

                val severityIndexes =
                    orderedRows.map { row ->
                        row.severityIndex
                    }

                val scoreMonotonic =
                    scores
                        .zipWithNext()
                        .all { (left, right) ->
                            left <= right
                        }

                val severityIndexMonotonic =
                    severityIndexes
                        .zipWithNext()
                        .all { (left, right) ->
                            left <= right + 1e-9
                        }

                val scoreIncreasedAtLeastOnce =
                    scores
                        .zipWithNext()
                        .any { (left, right) ->
                            right > left
                        }

                SeveritySequenceRow(
                    participantId =
                        key.participantId,
                    frequencyHz =
                        key.frequencyHz,
                    repetition =
                        key.repetition,

                    weakScore =
                        scores[0],
                    moderateScore =
                        scores[1],
                    strongScore =
                        scores[2],
                    veryStrongScore =
                        scores[3],

                    weakSeverityIndex =
                        severityIndexes[0],
                    moderateSeverityIndex =
                        severityIndexes[1],
                    strongSeverityIndex =
                        severityIndexes[2],
                    veryStrongSeverityIndex =
                        severityIndexes[3],

                    weakConfirmed =
                        orderedRows[0]
                            .tremorConfirmed,
                    moderateConfirmed =
                        orderedRows[1]
                            .tremorConfirmed,
                    strongConfirmed =
                        orderedRows[2]
                            .tremorConfirmed,
                    veryStrongConfirmed =
                        orderedRows[3]
                            .tremorConfirmed,

                    scoreMonotonic =
                        scoreMonotonic,
                    severityIndexMonotonic =
                        severityIndexMonotonic,
                    allLevelsConfirmed =
                        orderedRows.all { row ->
                            row.tremorConfirmed
                        },
                    scoreIncreasedAtLeastOnce =
                        scoreIncreasedAtLeastOnce
                )
            }
            .sortedWith(
                compareBy<SeveritySequenceRow> {
                    it.participantId
                }.thenBy {
                    it.frequencyHz
                }.thenBy {
                    it.repetition
                }
            )
    }

    private fun createOutputDirectory(): File {

        val workingDirectory =
            File(
                System.getProperty("user.dir")
            )

        val appDirectory =
            if (workingDirectory.name == "app") {
                workingDirectory
            } else {
                File(
                    workingDirectory,
                    "app"
                )
            }

        return File(
            appDirectory,
            "build/synthetic-hand-severity-trend"
        ).apply {
            check(exists() || mkdirs()) {
                "Could not create output directory: $absolutePath"
            }
        }
    }

    /*
     * Creates one quiet ten-second personal baseline and turns
     * it into the same five-window accumulated profile structure
     * used by the production algorithm.
     */
    private fun createSyntheticBaselineProfile(
        participant: VirtualParticipant
    ): MotionBaselineProfile {

        val analyzer =
            MotionAnalyzer()

        analyzer.startContinuous()

        val random =
            Random(
                participant.baselineSeed
            )

        val totalSeconds =
            11.0

        val sampleCount =
            (
                    totalSeconds *
                            sampleRateHz
                    ).toInt()

        val startTimestampNs =
            1_000_000_000L

        for (
        sampleIndex in
        0..sampleCount
        ) {
            val timestampNs =
                startTimestampNs +
                        sampleIndex *
                        sampleIntervalNs

            val accNoiseX =
                random.nextGaussian() *
                        participant.accelerationNoiseStd

            val accNoiseY =
                random.nextGaussian() *
                        participant.accelerationNoiseStd

            val accNoiseZ =
                random.nextGaussian() *
                        participant.accelerationNoiseStd

            val gyroNoiseX =
                random.nextGaussian() *
                        participant.gyroscopeNoiseStd

            val gyroNoiseY =
                random.nextGaussian() *
                        participant.gyroscopeNoiseStd

            val gyroNoiseZ =
                random.nextGaussian() *
                        participant.gyroscopeNoiseStd

            analyzer.addAccelerometer(
                x = accNoiseX.toFloat(),
                y = accNoiseY.toFloat(),
                z = (9.81 + accNoiseZ).toFloat(),
                timestampNs = timestampNs
            )

            analyzer.addGyroscope(
                x = gyroNoiseX.toFloat(),
                y = gyroNoiseY.toFloat(),
                z = gyroNoiseZ.toFloat(),
                timestampNs = timestampNs
            )
        }

        val baselineResult =
            analyzer.snapshot(
                windowSeconds = 10.0
            )

        check(baselineResult.isReliable) {
            """
            Synthetic baseline was unreliable.
            participant=${participant.id}
            duration=${baselineResult.durationSeconds}
            """.trimIndent()
        }

        val summaries =
            (
                    0 until 5
                    ).map { windowIndex ->

                    val window =
                        sliceMotionWindow(
                            source = baselineResult,
                            startSeconds =
                                windowIndex * 2.0,
                            requestedDurationSeconds =
                                2.0
                        )

                    checkNotNull(window) {
                        """
                        Could not create synthetic baseline window.
                        participant=${participant.id}
                        windowIndex=$windowIndex
                        """.trimIndent()
                    }

                    createBaselineWindowSummary(
                        window = window
                    )
                }

        return calculateBaselineProfile(
            windows = summaries
        )
    }

    /*
     * Generates six seconds and returns the latest five seconds.
     * The first second warms up the production filters.
     */
    private fun createFiveSecondStableTremorWindow(
        participant: VirtualParticipant,
        frequencyHz: Double,
        accelerationAmplitude: Double,
        gyroscopeAmplitude: Double,
        seed: Long
    ): MotionAnalysisResult {

        val analyzer =
            MotionAnalyzer()

        analyzer.startContinuous()

        val random =
            Random(seed)

        val totalSeconds =
            6.0

        val warmupSeconds =
            1.0

        val sampleCount =
            (
                    totalSeconds *
                            sampleRateHz
                    ).toInt()

        val startTimestampNs =
            2_000_000_000L

        val randomPhase =
            random.nextDouble() *
                    2.0 *
                    PI

        for (
        sampleIndex in
        0..sampleCount
        ) {
            val timeSeconds =
                sampleIndex /
                        sampleRateHz

            val measuredTimeSeconds =
                timeSeconds -
                        warmupSeconds

            val timestampNs =
                startTimestampNs +
                        sampleIndex *
                        sampleIntervalNs

            val stableSignal =
                if (measuredTimeSeconds >= 0.0) {
                    sin(
                        2.0 *
                                PI *
                                frequencyHz *
                                measuredTimeSeconds +
                                randomPhase
                    )
                } else {
                    0.0
                }

            val accelerationSignal =
                stableSignal *
                        accelerationAmplitude

            val gyroscopeSignal =
                stableSignal *
                        gyroscopeAmplitude

            val accNoiseX =
                random.nextGaussian() *
                        participant.accelerationNoiseStd

            val accNoiseY =
                random.nextGaussian() *
                        participant.accelerationNoiseStd

            val accNoiseZ =
                random.nextGaussian() *
                        participant.accelerationNoiseStd

            val gyroNoiseX =
                random.nextGaussian() *
                        participant.gyroscopeNoiseStd

            val gyroNoiseY =
                random.nextGaussian() *
                        participant.gyroscopeNoiseStd

            val gyroNoiseZ =
                random.nextGaussian() *
                        participant.gyroscopeNoiseStd

            analyzer.addAccelerometer(
                x =
                    (
                            accelerationSignal +
                                    accNoiseX
                            ).toFloat(),
                y =
                    (
                            accelerationSignal * 0.55 +
                                    accNoiseY
                            ).toFloat(),
                z =
                    (
                            9.81 +
                                    accelerationSignal * 0.25 +
                                    accNoiseZ
                            ).toFloat(),
                timestampNs =
                    timestampNs
            )

            analyzer.addGyroscope(
                x =
                    (
                            gyroscopeSignal +
                                    gyroNoiseX
                            ).toFloat(),
                y =
                    (
                            gyroscopeSignal * 0.60 +
                                    gyroNoiseY
                            ).toFloat(),
                z =
                    (
                            gyroscopeSignal * 0.30 +
                                    gyroNoiseZ
                            ).toFloat(),
                timestampNs =
                    timestampNs
            )
        }

        return analyzer.snapshot(
            windowSeconds = 5.0
        )
    }

    private fun createBaselineWindowSummary(
        window: MotionAnalysisResult
    ): MotionBaselineWindowSummary {

        val spectrum =
            TremorSpectrumAnalyzer
                .analyzeAxes(
                    xValues =
                        window.accelerationXValues,
                    yValues =
                        window.accelerationYValues,
                    zValues =
                        window.accelerationZValues,
                    timestampsNs =
                        window.accelerationTimestampsNs,
                    minHz = 6.0,
                    maxHz = 13.0
                )

        check(spectrum.sampleCount > 0) {
            "Synthetic baseline spectrum was empty"
        }

        return MotionBaselineWindowSummary(
            durationSeconds =
                window.durationSeconds,
            accelerationP95 =
                window.accelerationP95.toDouble(),
            gyroscopeP95 =
                window.gyroscopeP95.toDouble(),
            accelerationVariation =
                window
                    .accelerationVariation
                    .toDouble(),
            gyroscopeVariation =
                window
                    .gyroscopeVariation
                    .toDouble(),
            peakFrequencyHz =
                spectrum.peakFrequencyHz,
            bandAveragePower =
                spectrum.bandAveragePower,
            peakNeighborhoodPower =
                spectrum.peakNeighborhoodPower,
            concentrationRatio =
                spectrum.concentrationRatio,
            narrowbandRatio =
                spectrum.narrowbandRatio,
            rhythmicEnergyShare =
                spectrum.rhythmicEnergyShare
        )
    }

    private fun calculateBaselineProfile(
        windows: List<MotionBaselineWindowSummary>
    ): MotionBaselineProfile {

        val accelerationP95 =
            calculateStatistics(
                windows.map {
                    it.accelerationP95
                }
            )

        val gyroscopeP95 =
            calculateStatistics(
                windows.map {
                    it.gyroscopeP95
                }
            )

        val accelerationVariation =
            calculateStatistics(
                windows.map {
                    it.accelerationVariation
                }
            )

        val gyroscopeVariation =
            calculateStatistics(
                windows.map {
                    it.gyroscopeVariation
                }
            )

        val bandAveragePower =
            calculateStatistics(
                windows.map {
                    it.bandAveragePower
                }
            )

        val peakNeighborhoodPower =
            calculateStatistics(
                windows.map {
                    it.peakNeighborhoodPower
                }
            )

        val rhythmicEnergyShare =
            calculateStatistics(
                windows.map {
                    it.rhythmicEnergyShare
                }
            )

        return MotionBaselineProfile(
            accelerationP95Mean =
                accelerationP95.mean,
            accelerationP95M2 =
                accelerationP95.m2,
            gyroscopeP95Mean =
                gyroscopeP95.mean,
            gyroscopeP95M2 =
                gyroscopeP95.m2,
            accelerationVariationMean =
                accelerationVariation.mean,
            accelerationVariationM2 =
                accelerationVariation.m2,
            gyroscopeVariationMean =
                gyroscopeVariation.mean,
            gyroscopeVariationM2 =
                gyroscopeVariation.m2,
            bandAveragePowerMean =
                bandAveragePower.mean,
            bandAveragePowerM2 =
                bandAveragePower.m2,
            peakNeighborhoodPowerMean =
                peakNeighborhoodPower.mean,
            peakNeighborhoodPowerM2 =
                peakNeighborhoodPower.m2,
            rhythmicEnergyShareMean =
                rhythmicEnergyShare.mean,
            rhythmicEnergyShareM2 =
                rhythmicEnergyShare.m2,
            totalBaselineSeconds =
                windows.sumOf {
                    it.durationSeconds
                },
            validSessionCount = 1,
            totalWindowCount =
                windows.size
        )
    }

    private fun calculateStatistics(
        values: List<Double>
    ): FeatureStatistics {

        var count = 0
        var mean = 0.0
        var m2 = 0.0

        values.forEach { value ->
            count += 1

            val delta =
                value - mean

            mean +=
                delta / count

            val secondDelta =
                value - mean

            m2 +=
                delta * secondDelta
        }

        return FeatureStatistics(
            mean = mean,
            m2 = m2.coerceAtLeast(0.0)
        )
    }

    private fun sliceMotionWindow(
        source: MotionAnalysisResult,
        startSeconds: Double,
        requestedDurationSeconds: Double
    ): MotionAnalysisResult? {

        if (
            source.accelerationTimestampsNs.size < 2 ||
            source.gyroscopeTimestampsNs.size < 2
        ) {
            return null
        }

        val commonStartTimestampNs =
            maxOf(
                source.accelerationTimestampsNs.first(),
                source.gyroscopeTimestampsNs.first()
            )

        val commonEndTimestampNs =
            minOf(
                source.accelerationTimestampsNs.last(),
                source.gyroscopeTimestampsNs.last()
            )

        val requestedStartTimestampNs =
            commonStartTimestampNs +
                    (
                            startSeconds *
                                    1_000_000_000.0
                            ).toLong()

        val requestedEndTimestampNs =
            requestedStartTimestampNs +
                    (
                            requestedDurationSeconds *
                                    1_000_000_000.0
                            ).toLong()

        val actualEndTimestampNs =
            minOf(
                requestedEndTimestampNs,
                commonEndTimestampNs
            )

        val accelerationRange =
            findTimestampRange(
                timestampsNs =
                    source.accelerationTimestampsNs,
                startTimestampNs =
                    requestedStartTimestampNs,
                endTimestampNs =
                    actualEndTimestampNs
            ) ?: return null

        val gyroscopeRange =
            findTimestampRange(
                timestampsNs =
                    source.gyroscopeTimestampsNs,
                startTimestampNs =
                    requestedStartTimestampNs,
                endTimestampNs =
                    actualEndTimestampNs
            ) ?: return null

        val accelerationValues =
            source.accelerationValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationXValues =
            source.accelerationXValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationYValues =
            source.accelerationYValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationZValues =
            source.accelerationZValues
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val accelerationTimestamps =
            source.accelerationTimestampsNs
                .subList(
                    accelerationRange.first,
                    accelerationRange.last + 1
                )
                .toList()

        val gyroscopeValues =
            source.gyroscopeValues
                .subList(
                    gyroscopeRange.first,
                    gyroscopeRange.last + 1
                )
                .toList()

        val gyroscopeTimestamps =
            source.gyroscopeTimestampsNs
                .subList(
                    gyroscopeRange.first,
                    gyroscopeRange.last + 1
                )
                .toList()

        val durationSeconds =
            minOf(
                accelerationTimestamps
                    .durationSeconds(),
                gyroscopeTimestamps
                    .durationSeconds()
            )

        return MotionAnalysisResult(
            durationSeconds =
                durationSeconds,
            averageAcceleration =
                accelerationValues.averageOrZero(),
            maxAcceleration =
                accelerationValues.maxOrNull()
                    ?: 0f,
            accelerationVariation =
                accelerationValues.variation(),
            accelerationP95 =
                accelerationValues.percentile95(),
            averageGyroscope =
                gyroscopeValues.averageOrZero(),
            maxGyroscope =
                gyroscopeValues.maxOrNull()
                    ?: 0f,
            gyroscopeVariation =
                gyroscopeValues.variation(),
            gyroscopeP95 =
                gyroscopeValues.percentile95(),
            isReliable =
                durationSeconds >=
                        requestedDurationSeconds * 0.85,
            accelerationValues =
                accelerationValues,
            gyroscopeValues =
                gyroscopeValues,
            accelerationXValues =
                accelerationXValues,
            accelerationYValues =
                accelerationYValues,
            accelerationZValues =
                accelerationZValues,
            accelerationTimestampsNs =
                accelerationTimestamps,
            gyroscopeTimestampsNs =
                gyroscopeTimestamps
        )
    }

    private fun findTimestampRange(
        timestampsNs: List<Long>,
        startTimestampNs: Long,
        endTimestampNs: Long
    ): IntRange? {

        val firstIndex =
            timestampsNs.indexOfFirst {
                it >= startTimestampNs
            }

        val lastIndex =
            timestampsNs.indexOfLast {
                it <= endTimestampNs
            }

        if (
            firstIndex < 0 ||
            lastIndex < firstIndex
        ) {
            return null
        }

        return firstIndex..lastIndex
    }

    private fun writeRowCsv(
        rows: List<SeverityEvaluationRow>,
        destination: File
    ) {

        destination.bufferedWriter().use { writer ->

            writer.appendLine(
                listOf(
                    "participantId",
                    "frequencyHz",
                    "repetition",
                    "randomSeed",
                    "amplitudeLevel",
                    "amplitudeOrder",
                    "baselineNoiseMultiplier",
                    "accelerationAmplitude",
                    "gyroscopeAmplitude",
                    "tremorConfirmed",
                    "score",
                    "severityIndex",
                    "measuredPeakFrequencyHz",
                    "currentAccelerationP95",
                    "currentGyroscopeP95"
                ).joinToString(",")
            )

            rows.forEach { row ->
                writer.appendLine(
                    listOf(
                        row.participantId,
                        format(row.frequencyHz),
                        row.repetition,
                        row.randomSeed,
                        row.amplitudeLevel,
                        row.amplitudeOrder,
                        format(
                            row.baselineNoiseMultiplier
                        ),
                        format(
                            row.accelerationAmplitude
                        ),
                        format(
                            row.gyroscopeAmplitude
                        ),
                        row.tremorConfirmed,
                        row.score,
                        format(
                            row.severityIndex
                        ),
                        format(
                            row.measuredPeakFrequencyHz
                        ),
                        format(
                            row.currentAccelerationP95
                        ),
                        format(
                            row.currentGyroscopeP95
                        )
                    ).joinToString(",")
                )
            }
        }
    }

    private fun writeSequenceCsv(
        rows: List<SeveritySequenceRow>,
        destination: File
    ) {

        destination.bufferedWriter().use { writer ->

            writer.appendLine(
                listOf(
                    "participantId",
                    "frequencyHz",
                    "repetition",
                    "weakScore",
                    "moderateScore",
                    "strongScore",
                    "veryStrongScore",
                    "weakSeverityIndex",
                    "moderateSeverityIndex",
                    "strongSeverityIndex",
                    "veryStrongSeverityIndex",
                    "weakConfirmed",
                    "moderateConfirmed",
                    "strongConfirmed",
                    "veryStrongConfirmed",
                    "scoreMonotonic",
                    "severityIndexMonotonic",
                    "allLevelsConfirmed",
                    "scoreIncreasedAtLeastOnce"
                ).joinToString(",")
            )

            rows.forEach { row ->
                writer.appendLine(
                    listOf(
                        row.participantId,
                        format(row.frequencyHz),
                        row.repetition,
                        row.weakScore,
                        row.moderateScore,
                        row.strongScore,
                        row.veryStrongScore,
                        format(
                            row.weakSeverityIndex
                        ),
                        format(
                            row.moderateSeverityIndex
                        ),
                        format(
                            row.strongSeverityIndex
                        ),
                        format(
                            row.veryStrongSeverityIndex
                        ),
                        row.weakConfirmed,
                        row.moderateConfirmed,
                        row.strongConfirmed,
                        row.veryStrongConfirmed,
                        row.scoreMonotonic,
                        row.severityIndexMonotonic,
                        row.allLevelsConfirmed,
                        row.scoreIncreasedAtLeastOnce
                    ).joinToString(",")
                )
            }
        }
    }

    private fun writeSummary(
        totalWindows: Int,
        totalSequences: Int,
        scoreMonotonicCount: Int,
        severityMonotonicCount: Int,
        allLevelsConfirmedCount: Int,
        scoreIncreasedCount: Int,
        destination: File
    ) {

        destination.writeText(
            """
            Synthetic hand severity trend evaluation

            Window evaluations: $totalWindows
            Severity sequences: $totalSequences

            Score monotonic sequences:
            $scoreMonotonicCount / $totalSequences (${formatPercentage(scoreMonotonicCount, totalSequences)}%)

            Severity-index monotonic sequences:
            $severityMonotonicCount / $totalSequences (${formatPercentage(severityMonotonicCount, totalSequences)}%)

            All four levels confirmed as tremor:
            $allLevelsConfirmedCount / $totalSequences (${formatPercentage(allLevelsConfirmedCount, totalSequences)}%)

            Score increased at least once:
            $scoreIncreasedCount / $totalSequences (${formatPercentage(scoreIncreasedCount, totalSequences)}%)
            """.trimIndent()
        )
    }

    private fun formatPercentage(
        numerator: Int,
        denominator: Int
    ): String {

        val percentage =
            if (denominator > 0) {
                numerator.toDouble() /
                        denominator *
                        100.0
            } else {
                0.0
            }

        return String.format(
            Locale.US,
            "%.2f",
            percentage
        )
    }

    private fun format(
        value: Double
    ): String {

        return if (value.isFinite()) {
            String.format(
                Locale.US,
                "%.8f",
                value
            )
        } else {
            "null"
        }
    }

    private fun List<Long>.durationSeconds():
            Double {

        if (size < 2) {
            return 0.0
        }

        return (
                last() -
                        first()
                ) / 1_000_000_000.0
    }

    private fun List<Float>.averageOrZero():
            Float {

        return if (isNotEmpty()) {
            average().toFloat()
        } else {
            0f
        }
    }

    private fun List<Float>.variation():
            Float {

        if (isEmpty()) {
            return 0f
        }

        val average =
            average()

        return map { value ->
            abs(
                value -
                        average
            )
        }.average().toFloat()
    }

    private fun List<Float>.percentile95():
            Float {

        if (isEmpty()) {
            return 0f
        }

        val sorted =
            sorted()

        val index =
            (
                    (
                            sorted.size -
                                    1
                            ) *
                            0.95
                    ).toInt()

        return sorted[index]
    }
}

private data class VirtualParticipant(
    val id: String,
    val accelerationNoiseStd: Double,
    val gyroscopeNoiseStd: Double,
    val baselineSeed: Long
)

private data class AmplitudeLevel(
    val name: String,
    val order: Int,
    val baselineNoiseMultiplier: Double
)

private data class SeverityEvaluationRow(
    val participantId: String,
    val frequencyHz: Double,
    val repetition: Int,
    val randomSeed: Long,
    val amplitudeLevel: String,
    val amplitudeOrder: Int,
    val baselineNoiseMultiplier: Double,
    val accelerationAmplitude: Double,
    val gyroscopeAmplitude: Double,
    val tremorConfirmed: Boolean,
    val score: Int,
    val severityIndex: Double,
    val measuredPeakFrequencyHz: Double,
    val currentAccelerationP95: Double,
    val currentGyroscopeP95: Double
)

private data class SequenceKey(
    val participantId: String,
    val frequencyHz: Double,
    val repetition: Int
)

private data class SeveritySequenceRow(
    val participantId: String,
    val frequencyHz: Double,
    val repetition: Int,

    val weakScore: Int,
    val moderateScore: Int,
    val strongScore: Int,
    val veryStrongScore: Int,

    val weakSeverityIndex: Double,
    val moderateSeverityIndex: Double,
    val strongSeverityIndex: Double,
    val veryStrongSeverityIndex: Double,

    val weakConfirmed: Boolean,
    val moderateConfirmed: Boolean,
    val strongConfirmed: Boolean,
    val veryStrongConfirmed: Boolean,

    val scoreMonotonic: Boolean,
    val severityIndexMonotonic: Boolean,
    val allLevelsConfirmed: Boolean,
    val scoreIncreasedAtLeastOnce: Boolean
)

private data class FeatureStatistics(
    val mean: Double,
    val m2: Double
)