package com.example.easyfill_project

import com.example.easyfill_project.hand_analysis.HandTremorEvaluator
import com.example.easyfill_project.hand_analysis.MotionAnalysisResult
import com.example.easyfill_project.hand_analysis.MotionAnalyzer
import com.example.easyfill_project.hand_analysis.MotionBaselineProfile
import com.example.easyfill_project.hand_analysis.MotionBaselineWindowSummary
import com.example.easyfill_project.hand_analysis.TremorSpectrumAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Long-term synthetic stress test for accumulated hand-baseline adaptation.
 *
 * It performs 500 accepted repeated uses for each of four virtual
 * participants and uses the production MotionAnalyzer,
 * TremorSpectrumAnalyzer, baseline models and HandTremorEvaluator.
 *
 * The Welford merge is reproduced here because the production
 * merge function is private inside MotionTrackingController.
 *
 * Output:
 * app/build/synthetic-hand-long-term-adaptation/
 * - synthetic_hand_long_term_adaptation.csv
 * - synthetic_hand_long_term_adaptation_summary.txt
 */
class HandLongTermBaselineAdaptationTest {

    @Test
    fun generateLongTermHandBaselineAdaptationDataset() {
        val outputDirectory = outputDirectory()
        val rows = mutableListOf<Row>()

        participants().forEach { participant ->
            val initialWindows = baselineWindows(
                participant = participant,
                accelerationNoiseStd = participant.initialAccNoise,
                gyroscopeNoiseStd = participant.initialGyroNoise,
                seed = participant.initialSeed
            )

            val stableWindows = baselineWindows(
                participant = participant,
                accelerationNoiseStd = participant.stableAccNoise,
                gyroscopeNoiseStd = participant.stableGyroNoise,
                seed = participant.stableSeed
            )

            val target = Target.from(stableWindows)
            val allAcceptedWindows = initialWindows.toMutableList()

            var profile = profileFrom(
                windows = initialWindows,
                validSessionCount = 1
            )

            var previousDistance = distanceToTarget(
                profile = profile,
                target = target
            )

            repeat(ACCEPTED_USE_COUNT) { index ->
                val useIndex = index + 1
                val activeProfile = profile

                val normalBefore = HandTremorEvaluator.evaluate(
                    profile = activeProfile,
                    current = fiveSecondWindow(
                        participant = participant,
                        type = SignalType.QUIET,
                        accelerationNoiseStd = participant.stableAccNoise,
                        gyroscopeNoiseStd = participant.stableGyroNoise,
                        accelerationAmplitude = 0.0,
                        gyroscopeAmplitude = 0.0,
                        frequencyHz = 0.0,
                        seed = participant.stableSeed + 50_000L + index
                    )
                )

                val updatedProfile = mergeProfile(
                    existing = activeProfile,
                    newWindows = stableWindows
                )

                allAcceptedWindows.addAll(stableWindows)

                val directProfile = profileFrom(
                    windows = allAcceptedWindows,
                    validSessionCount = activeProfile.validSessionCount + 1
                )

                val pooledMatch = profilesEqual(
                    updatedProfile,
                    directProfile
                )

                val distanceAfter = distanceToTarget(
                    profile = updatedProfile,
                    target = target
                )

                val distanceDidNotIncrease =
                    distanceAfter <= previousDistance + DISTANCE_TOLERANCE

                val normalAfter = HandTremorEvaluator.evaluate(
                    profile = updatedProfile,
                    current = fiveSecondWindow(
                        participant = participant,
                        type = SignalType.QUIET,
                        accelerationNoiseStd = participant.stableAccNoise,
                        gyroscopeNoiseStd = participant.stableGyroNoise,
                        accelerationAmplitude = 0.0,
                        gyroscopeAmplitude = 0.0,
                        frequencyHz = 0.0,
                        seed = participant.stableSeed + 60_000L + index
                    )
                )

                rows += Row(
                    participantId = participant.id,
                    recordType = RECORD_UPDATE,
                    usageIndex = useIndex,
                    updateAccepted = true,
                    rejectionReason = null,

                    sessionsBefore = activeProfile.validSessionCount,
                    sessionsAfter = updatedProfile.validSessionCount,
                    windowsBefore = activeProfile.totalWindowCount,
                    windowsAfter = updatedProfile.totalWindowCount,
                    secondsBefore = activeProfile.totalBaselineSeconds,
                    secondsAfter = updatedProfile.totalBaselineSeconds,

                    accelerationP95Mean = updatedProfile.accelerationP95Mean,
                    accelerationP95Std = std(
                        updatedProfile.accelerationP95M2,
                        updatedProfile.totalWindowCount
                    ),
                    gyroscopeP95Mean = updatedProfile.gyroscopeP95Mean,
                    gyroscopeP95Std = std(
                        updatedProfile.gyroscopeP95M2,
                        updatedProfile.totalWindowCount
                    ),
                    accelerationVariationMean =
                        updatedProfile.accelerationVariationMean,
                    accelerationVariationStd = std(
                        updatedProfile.accelerationVariationM2,
                        updatedProfile.totalWindowCount
                    ),
                    gyroscopeVariationMean =
                        updatedProfile.gyroscopeVariationMean,
                    gyroscopeVariationStd = std(
                        updatedProfile.gyroscopeVariationM2,
                        updatedProfile.totalWindowCount
                    ),
                    bandAveragePowerMean =
                        updatedProfile.bandAveragePowerMean,
                    bandAveragePowerStd = std(
                        updatedProfile.bandAveragePowerM2,
                        updatedProfile.totalWindowCount
                    ),
                    peakNeighborhoodPowerMean =
                        updatedProfile.peakNeighborhoodPowerMean,
                    peakNeighborhoodPowerStd = std(
                        updatedProfile.peakNeighborhoodPowerM2,
                        updatedProfile.totalWindowCount
                    ),
                    rhythmicEnergyShareMean =
                        updatedProfile.rhythmicEnergyShareMean,
                    rhythmicEnergyShareStd = std(
                        updatedProfile.rhythmicEnergyShareM2,
                        updatedProfile.totalWindowCount
                    ),

                    targetAccelerationP95 = target.accelerationP95,
                    targetGyroscopeP95 = target.gyroscopeP95,
                    targetAccelerationVariation =
                        target.accelerationVariation,
                    targetGyroscopeVariation =
                        target.gyroscopeVariation,
                    targetBandAveragePower =
                        target.bandAveragePower,
                    targetPeakNeighborhoodPower =
                        target.peakNeighborhoodPower,
                    targetRhythmicEnergyShare =
                        target.rhythmicEnergyShare,

                    distanceBefore = previousDistance,
                    distanceAfter = distanceAfter,
                    distanceDidNotIncrease = distanceDidNotIncrease,

                    normalScoreBefore = normalBefore.severity.score,
                    normalScoreAfter = normalAfter.severity.score,
                    normalTremorBefore = normalBefore.tremorConfirmed,
                    normalTremorAfter = normalAfter.tremorConfirmed,

                    tremorStillDetected = null,
                    candidateReliable = true,
                    candidateWindowCount = WINDOWS_PER_SESSION,
                    pooledStatisticsMatch = pooledMatch,
                    profileUnchanged = false
                )

                assertEquals(
                    activeProfile.validSessionCount + 1,
                    updatedProfile.validSessionCount
                )

                assertEquals(
                    activeProfile.totalWindowCount + WINDOWS_PER_SESSION,
                    updatedProfile.totalWindowCount
                )

                assertTrue(pooledMatch)
                assertTrue(distanceDidNotIncrease)
                assertEquals(0, normalBefore.severity.score)
                assertEquals(0, normalAfter.severity.score)

                profile = updatedProfile
                previousDistance = distanceAfter
            }

            val tremorWindow = fiveSecondWindow(
                participant = participant,
                type = SignalType.STABLE_TREMOR,
                accelerationNoiseStd = participant.stableAccNoise,
                gyroscopeNoiseStd = participant.stableGyroNoise,
                accelerationAmplitude = 0.30,
                gyroscopeAmplitude = 0.15,
                frequencyHz = 9.0,
                seed = participant.stableSeed + 70_000L
            )

            val tremorEvaluation = HandTremorEvaluator.evaluate(
                profile = profile,
                current = tremorWindow
            )

            assertTrue(tremorEvaluation.tremorConfirmed)

            val tremorCandidate = tenSecondSession(
                participant = participant,
                type = SignalType.STABLE_TREMOR,
                accelerationNoiseStd = participant.stableAccNoise,
                gyroscopeNoiseStd = participant.stableGyroNoise,
                accelerationAmplitude = 0.30,
                gyroscopeAmplitude = 0.15,
                frequencyHz = 9.0,
                seed = participant.stableSeed + 80_000L
            )

            val tremorCandidateWindows = summaries(tremorCandidate)

            val tremorBlocked = containsTremor(
                profile = profile,
                candidate = tremorCandidate,
                windows = tremorCandidateWindows
            )

            rows += blockedRow(
                participant = participant,
                recordType = RECORD_TREMOR_BLOCK,
                reason = "SUSTAINED_RHYTHMIC_TREMOR",
                profile = profile,
                candidate = tremorCandidate,
                windows = tremorCandidateWindows,
                blocked = tremorBlocked,
                tremorStillDetected = tremorEvaluation.tremorConfirmed
            )

            assertTrue(tremorBlocked)

            val extremeCandidate = tenSecondSession(
                participant = participant,
                type = SignalType.EXTREME_RANDOM,
                accelerationNoiseStd = participant.stableAccNoise,
                gyroscopeNoiseStd = participant.stableGyroNoise,
                accelerationAmplitude = 1.50,
                gyroscopeAmplitude = 0.80,
                frequencyHz = 0.0,
                seed = participant.stableSeed + 90_000L
            )

            val extremeWindows = summaries(extremeCandidate)

            val extremeBlocked = containsExtremeMovement(
                profile = profile,
                windows = extremeWindows
            )

            rows += blockedRow(
                participant = participant,
                recordType = RECORD_EXTREME_BLOCK,
                reason = "SUSTAINED_EXTREME_MOVEMENT",
                profile = profile,
                candidate = extremeCandidate,
                windows = extremeWindows,
                blocked = extremeBlocked,
                tremorStillDetected = null
            )

            assertTrue(extremeBlocked)

            val emptyAnalyzer = MotionAnalyzer()
            emptyAnalyzer.startContinuous()

            val unreliableCandidate = emptyAnalyzer.snapshot(10.0)
            val unreliableWindows = summaries(unreliableCandidate)

            val unreliableBlocked =
                !unreliableCandidate.isReliable ||
                        unreliableWindows.size != WINDOWS_PER_SESSION

            rows += blockedRow(
                participant = participant,
                recordType = RECORD_UNRELIABLE_BLOCK,
                reason = "UNRELIABLE_SENSOR_DATA",
                profile = profile,
                candidate = unreliableCandidate,
                windows = unreliableWindows,
                blocked = unreliableBlocked,
                tremorStillDetected = null
            )

            assertTrue(unreliableBlocked)
        }

        val csvFile = File(
            outputDirectory,
            "synthetic_hand_long_term_adaptation.csv"
        )

        val summaryFile = File(
            outputDirectory,
            "synthetic_hand_long_term_adaptation_summary.txt"
        )

        writeCsv(csvFile, rows)

        val summary = summary(rows)
        summaryFile.writeText(summary)

        println(summary)
        println()
        println("CSV: ${csvFile.absolutePath}")
        println("Summary: ${summaryFile.absolutePath}")

        assertTrue(csvFile.exists())
        assertTrue(summaryFile.exists())
    }

    // ------------------------------------------------------------------
    // Synthetic data
    // ------------------------------------------------------------------

    private fun baselineWindows(
        participant: Participant,
        accelerationNoiseStd: Double,
        gyroscopeNoiseStd: Double,
        seed: Long
    ): List<MotionBaselineWindowSummary> {
        val result = tenSecondSession(
            participant = participant,
            type = SignalType.QUIET,
            accelerationNoiseStd = accelerationNoiseStd,
            gyroscopeNoiseStd = gyroscopeNoiseStd,
            accelerationAmplitude = 0.0,
            gyroscopeAmplitude = 0.0,
            frequencyHz = 0.0,
            seed = seed
        )

        check(result.isReliable)

        return summaries(result).also {
            check(it.size == WINDOWS_PER_SESSION)
        }
    }

    private fun tenSecondSession(
        participant: Participant,
        type: SignalType,
        accelerationNoiseStd: Double,
        gyroscopeNoiseStd: Double,
        accelerationAmplitude: Double,
        gyroscopeAmplitude: Double,
        frequencyHz: Double,
        seed: Long
    ): MotionAnalysisResult {
        return generatedWindow(
            participant = participant,
            requestedSeconds = 10.0,
            type = type,
            accelerationNoiseStd = accelerationNoiseStd,
            gyroscopeNoiseStd = gyroscopeNoiseStd,
            accelerationAmplitude = accelerationAmplitude,
            gyroscopeAmplitude = gyroscopeAmplitude,
            frequencyHz = frequencyHz,
            seed = seed
        )
    }

    private fun fiveSecondWindow(
        participant: Participant,
        type: SignalType,
        accelerationNoiseStd: Double,
        gyroscopeNoiseStd: Double,
        accelerationAmplitude: Double,
        gyroscopeAmplitude: Double,
        frequencyHz: Double,
        seed: Long
    ): MotionAnalysisResult {
        return generatedWindow(
            participant = participant,
            requestedSeconds = 5.0,
            type = type,
            accelerationNoiseStd = accelerationNoiseStd,
            gyroscopeNoiseStd = gyroscopeNoiseStd,
            accelerationAmplitude = accelerationAmplitude,
            gyroscopeAmplitude = gyroscopeAmplitude,
            frequencyHz = frequencyHz,
            seed = seed
        )
    }

    private fun generatedWindow(
        participant: Participant,
        requestedSeconds: Double,
        type: SignalType,
        accelerationNoiseStd: Double,
        gyroscopeNoiseStd: Double,
        accelerationAmplitude: Double,
        gyroscopeAmplitude: Double,
        frequencyHz: Double,
        seed: Long
    ): MotionAnalysisResult {
        val analyzer = MotionAnalyzer()
        analyzer.startContinuous()

        val random = Random(seed)
        val phase = random.nextDouble() * 2.0 * PI
        val totalSeconds = requestedSeconds + 1.0
        val sampleCount = (totalSeconds * SAMPLE_RATE_HZ).toInt()
        val startTimestampNs = 1_000_000_000L + participant.timestampOffset

        for (sampleIndex in 0..sampleCount) {
            val timeSeconds = sampleIndex / SAMPLE_RATE_HZ
            val measuredTime = timeSeconds - 1.0
            val timestampNs =
                startTimestampNs + sampleIndex * SAMPLE_INTERVAL_NS

            val signal = if (measuredTime >= 0.0) {
                when (type) {
                    SignalType.QUIET -> 0.0
                    SignalType.STABLE_TREMOR ->
                        sin(
                            2.0 * PI * frequencyHz *
                                    measuredTime + phase
                        )
                    SignalType.EXTREME_RANDOM ->
                        random.nextGaussian()
                }
            } else {
                0.0
            }

            val accSignal = signal * accelerationAmplitude
            val gyroSignal = signal * gyroscopeAmplitude

            analyzer.addAccelerometer(
                x = (
                        accSignal +
                                random.nextGaussian() *
                                accelerationNoiseStd
                        ).toFloat(),
                y = (
                        accSignal * 0.55 +
                                random.nextGaussian() *
                                accelerationNoiseStd
                        ).toFloat(),
                z = (
                        9.81 +
                                accSignal * 0.25 +
                                random.nextGaussian() *
                                accelerationNoiseStd
                        ).toFloat(),
                timestampNs = timestampNs
            )

            analyzer.addGyroscope(
                x = (
                        gyroSignal +
                                random.nextGaussian() *
                                gyroscopeNoiseStd
                        ).toFloat(),
                y = (
                        gyroSignal * 0.60 +
                                random.nextGaussian() *
                                gyroscopeNoiseStd
                        ).toFloat(),
                z = (
                        gyroSignal * 0.30 +
                                random.nextGaussian() *
                                gyroscopeNoiseStd
                        ).toFloat(),
                timestampNs = timestampNs
            )
        }

        return analyzer.snapshot(requestedSeconds)
    }

    // ------------------------------------------------------------------
    // Baseline profile
    // ------------------------------------------------------------------

    private fun summaries(
        candidate: MotionAnalysisResult
    ): List<MotionBaselineWindowSummary> {
        if (!candidate.isReliable || candidate.durationSeconds < 9.5) {
            return emptyList()
        }

        return (0 until WINDOWS_PER_SESSION).mapNotNull { index ->
            val window = slice(
                source = candidate,
                startSeconds = index * 2.0,
                durationSeconds = 2.0
            ) ?: return@mapNotNull null

            val spectrum = TremorSpectrumAnalyzer.analyzeAxes(
                xValues = window.accelerationXValues,
                yValues = window.accelerationYValues,
                zValues = window.accelerationZValues,
                timestampsNs = window.accelerationTimestampsNs,
                minHz = 6.0,
                maxHz = 13.0
            )

            if (
                !window.isReliable ||
                window.durationSeconds < 1.70 ||
                spectrum.sampleCount <= 0
            ) {
                return@mapNotNull null
            }

            MotionBaselineWindowSummary(
                durationSeconds = window.durationSeconds,
                accelerationP95 =
                    window.accelerationP95.toDouble(),
                gyroscopeP95 =
                    window.gyroscopeP95.toDouble(),
                accelerationVariation =
                    window.accelerationVariation.toDouble(),
                gyroscopeVariation =
                    window.gyroscopeVariation.toDouble(),
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
    }

    private fun profileFrom(
        windows: List<MotionBaselineWindowSummary>,
        validSessionCount: Int
    ): MotionBaselineProfile {
        val accP95 = stats(windows.map { it.accelerationP95 })
        val gyroP95 = stats(windows.map { it.gyroscopeP95 })
        val accVar = stats(windows.map { it.accelerationVariation })
        val gyroVar = stats(windows.map { it.gyroscopeVariation })
        val band = stats(windows.map { it.bandAveragePower })
        val peak = stats(windows.map { it.peakNeighborhoodPower })
        val rhythmic = stats(windows.map { it.rhythmicEnergyShare })

        return MotionBaselineProfile(
            accelerationP95Mean = accP95.mean,
            accelerationP95M2 = accP95.m2,
            gyroscopeP95Mean = gyroP95.mean,
            gyroscopeP95M2 = gyroP95.m2,
            accelerationVariationMean = accVar.mean,
            accelerationVariationM2 = accVar.m2,
            gyroscopeVariationMean = gyroVar.mean,
            gyroscopeVariationM2 = gyroVar.m2,
            bandAveragePowerMean = band.mean,
            bandAveragePowerM2 = band.m2,
            peakNeighborhoodPowerMean = peak.mean,
            peakNeighborhoodPowerM2 = peak.m2,
            rhythmicEnergyShareMean = rhythmic.mean,
            rhythmicEnergyShareM2 = rhythmic.m2,
            totalBaselineSeconds =
                windows.sumOf { it.durationSeconds },
            validSessionCount = validSessionCount,
            totalWindowCount = windows.size
        )
    }

    private fun mergeProfile(
        existing: MotionBaselineProfile,
        newWindows: List<MotionBaselineWindowSummary>
    ): MotionBaselineProfile {
        val count = existing.totalWindowCount

        val accP95 = mergeStats(
            existing.accelerationP95Mean,
            existing.accelerationP95M2,
            count,
            newWindows.map { it.accelerationP95 }
        )
        val gyroP95 = mergeStats(
            existing.gyroscopeP95Mean,
            existing.gyroscopeP95M2,
            count,
            newWindows.map { it.gyroscopeP95 }
        )
        val accVar = mergeStats(
            existing.accelerationVariationMean,
            existing.accelerationVariationM2,
            count,
            newWindows.map { it.accelerationVariation }
        )
        val gyroVar = mergeStats(
            existing.gyroscopeVariationMean,
            existing.gyroscopeVariationM2,
            count,
            newWindows.map { it.gyroscopeVariation }
        )
        val band = mergeStats(
            existing.bandAveragePowerMean,
            existing.bandAveragePowerM2,
            count,
            newWindows.map { it.bandAveragePower }
        )
        val peak = mergeStats(
            existing.peakNeighborhoodPowerMean,
            existing.peakNeighborhoodPowerM2,
            count,
            newWindows.map { it.peakNeighborhoodPower }
        )
        val rhythmic = mergeStats(
            existing.rhythmicEnergyShareMean,
            existing.rhythmicEnergyShareM2,
            count,
            newWindows.map { it.rhythmicEnergyShare }
        )

        return MotionBaselineProfile(
            accelerationP95Mean = accP95.mean,
            accelerationP95M2 = accP95.m2,
            gyroscopeP95Mean = gyroP95.mean,
            gyroscopeP95M2 = gyroP95.m2,
            accelerationVariationMean = accVar.mean,
            accelerationVariationM2 = accVar.m2,
            gyroscopeVariationMean = gyroVar.mean,
            gyroscopeVariationM2 = gyroVar.m2,
            bandAveragePowerMean = band.mean,
            bandAveragePowerM2 = band.m2,
            peakNeighborhoodPowerMean = peak.mean,
            peakNeighborhoodPowerM2 = peak.m2,
            rhythmicEnergyShareMean = rhythmic.mean,
            rhythmicEnergyShareM2 = rhythmic.m2,
            totalBaselineSeconds =
                existing.totalBaselineSeconds +
                        newWindows.sumOf { it.durationSeconds },
            validSessionCount =
                existing.validSessionCount + 1,
            totalWindowCount =
                existing.totalWindowCount + newWindows.size
        )
    }

    private fun stats(values: List<Double>): Statistics {
        var count = 0
        var mean = 0.0
        var m2 = 0.0

        values.forEach { value ->
            count += 1
            val delta = value - mean
            mean += delta / count
            m2 += delta * (value - mean)
        }

        return Statistics(mean, m2.coerceAtLeast(0.0), count)
    }

    private fun mergeStats(
        oldMean: Double,
        oldM2: Double,
        oldCount: Int,
        newValues: List<Double>
    ): Statistics {
        val new = stats(newValues)
        val totalCount = oldCount + new.count
        val delta = new.mean - oldMean

        val mean =
            oldMean + delta * new.count / totalCount

        val m2 =
            oldM2 +
                    new.m2 +
                    delta * delta *
                    oldCount * new.count /
                    totalCount

        return Statistics(
            mean,
            m2.coerceAtLeast(0.0),
            totalCount
        )
    }

    // ------------------------------------------------------------------
    // Candidate rejection
    // ------------------------------------------------------------------

    private fun containsTremor(
        profile: MotionBaselineProfile,
        candidate: MotionAnalysisResult,
        windows: List<MotionBaselineWindowSummary>
    ): Boolean {
        if (!candidate.isReliable || windows.size != 5) {
            return false
        }

        val spectrum = TremorSpectrumAnalyzer.analyzeAxes(
            xValues = candidate.accelerationXValues,
            yValues = candidate.accelerationYValues,
            zValues = candidate.accelerationZValues,
            timestampsNs = candidate.accelerationTimestampsNs,
            minHz = 6.0,
            maxHz = 13.0
        )

        val upperBand = upperNormal(
            profile.bandAveragePowerMean,
            profile.bandAveragePowerM2,
            profile.totalWindowCount
        )

        val upperPeak = upperNormal(
            profile.peakNeighborhoodPowerMean,
            profile.peakNeighborhoodPowerM2,
            profile.totalWindowCount
        )

        val wholeRhythmicThreshold = max(
            0.35,
            upperNormal(
                profile.rhythmicEnergyShareMean,
                profile.rhythmicEnergyShareM2,
                profile.totalWindowCount
            )
        ).coerceAtMost(0.75)

        val wholePeriodic =
            spectrum.peakFrequencyHz in 6.0..13.0 &&
                    spectrum.concentrationRatio >= 4.0 &&
                    spectrum.narrowbandRatio >= 3.0 &&
                    spectrum.rhythmicEnergyShare >=
                    wholeRhythmicThreshold &&
                    (
                            spectrum.bandAveragePower > upperBand ||
                                    spectrum.peakNeighborhoodPower >
                                    upperPeak
                            )

        val temporalThreshold = max(
            0.30,
            upperNormal(
                profile.rhythmicEnergyShareMean,
                profile.rhythmicEnergyShareM2,
                profile.totalWindowCount
            )
        ).coerceAtMost(0.75)

        val rhythmicWindows = windows.filter { window ->
            window.peakFrequencyHz in 6.0..13.0 &&
                    window.concentrationRatio >= 3.0 &&
                    window.narrowbandRatio >= 2.0 &&
                    window.rhythmicEnergyShare >=
                    temporalThreshold &&
                    (
                            window.bandAveragePower > upperBand ||
                                    window.peakNeighborhoodPower >
                                    upperPeak
                            )
        }

        val frequencies =
            rhythmicWindows.map { it.peakFrequencyHz }

        val spread = if (frequencies.size >= 2) {
            frequencies.maxOrNull()!! -
                    frequencies.minOrNull()!!
        } else {
            Double.POSITIVE_INFINITY
        }

        return wholePeriodic &&
                rhythmicWindows.size >= 3 &&
                spread <= 1.0
    }

    private fun containsExtremeMovement(
        profile: MotionBaselineProfile,
        windows: List<MotionBaselineWindowSummary>
    ): Boolean {
        if (windows.size != 5) {
            return false
        }

        val upperAcc = upperNormal(
            profile.accelerationP95Mean,
            profile.accelerationP95M2,
            profile.totalWindowCount
        )

        val upperGyro = upperNormal(
            profile.gyroscopeP95Mean,
            profile.gyroscopeP95M2,
            profile.totalWindowCount
        )

        return windows.count { window ->
            window.accelerationP95 > upperAcc * 3.0 &&
                    window.gyroscopeP95 > upperGyro * 3.0
        } >= 4
    }

    private fun upperNormal(
        mean: Double,
        m2: Double,
        sampleCount: Int
    ): Double {
        val standardDeviation = std(m2, sampleCount)
        val scale = max(
            standardDeviation,
            max(abs(mean) * 0.15, 1e-9)
        )

        return mean + 2.5 * scale
    }

    // ------------------------------------------------------------------
    // Slicing and statistics
    // ------------------------------------------------------------------

    private fun slice(
        source: MotionAnalysisResult,
        startSeconds: Double,
        durationSeconds: Double
    ): MotionAnalysisResult? {
        if (
            source.accelerationTimestampsNs.size < 2 ||
            source.gyroscopeTimestampsNs.size < 2
        ) {
            return null
        }

        val commonStart = maxOf(
            source.accelerationTimestampsNs.first(),
            source.gyroscopeTimestampsNs.first()
        )

        val commonEnd = minOf(
            source.accelerationTimestampsNs.last(),
            source.gyroscopeTimestampsNs.last()
        )

        val requestedStart =
            commonStart +
                    (startSeconds * 1_000_000_000.0).toLong()

        val requestedEnd =
            minOf(
                requestedStart +
                        (durationSeconds *
                                1_000_000_000.0).toLong(),
                commonEnd
            )

        if (
            requestedStart >= commonEnd ||
            (requestedEnd - requestedStart) /
            1_000_000_000.0 <
            durationSeconds * 0.85
        ) {
            return null
        }

        val accRange = range(
            source.accelerationTimestampsNs,
            requestedStart,
            requestedEnd
        ) ?: return null

        val gyroRange = range(
            source.gyroscopeTimestampsNs,
            requestedStart,
            requestedEnd
        ) ?: return null

        val acc = source.accelerationValues
            .subList(accRange.first, accRange.last + 1)
            .toList()
        val accX = source.accelerationXValues
            .subList(accRange.first, accRange.last + 1)
            .toList()
        val accY = source.accelerationYValues
            .subList(accRange.first, accRange.last + 1)
            .toList()
        val accZ = source.accelerationZValues
            .subList(accRange.first, accRange.last + 1)
            .toList()
        val accTimes = source.accelerationTimestampsNs
            .subList(accRange.first, accRange.last + 1)
            .toList()

        val gyro = source.gyroscopeValues
            .subList(gyroRange.first, gyroRange.last + 1)
            .toList()
        val gyroTimes = source.gyroscopeTimestampsNs
            .subList(gyroRange.first, gyroRange.last + 1)
            .toList()

        val actualDuration = minOf(
            duration(accTimes),
            duration(gyroTimes)
        )

        return MotionAnalysisResult(
            durationSeconds = actualDuration,
            averageAcceleration = average(acc),
            maxAcceleration = acc.maxOrNull() ?: 0f,
            accelerationVariation = variation(acc),
            accelerationP95 = percentile95(acc),
            averageGyroscope = average(gyro),
            maxGyroscope = gyro.maxOrNull() ?: 0f,
            gyroscopeVariation = variation(gyro),
            gyroscopeP95 = percentile95(gyro),
            isReliable =
                actualDuration >= durationSeconds * 0.85,
            accelerationValues = acc,
            gyroscopeValues = gyro,
            accelerationXValues = accX,
            accelerationYValues = accY,
            accelerationZValues = accZ,
            accelerationTimestampsNs = accTimes,
            gyroscopeTimestampsNs = gyroTimes
        )
    }

    private fun range(
        timestamps: List<Long>,
        start: Long,
        end: Long
    ): IntRange? {
        val first = timestamps.indexOfFirst { it >= start }
        val last = timestamps.indexOfLast { it <= end }

        return if (first >= 0 && last >= first) {
            first..last
        } else {
            null
        }
    }

    private fun duration(values: List<Long>): Double =
        if (values.size < 2) {
            0.0
        } else {
            (values.last() - values.first()) /
                    1_000_000_000.0
        }

    private fun average(values: List<Float>): Float =
        if (values.isEmpty()) {
            0f
        } else {
            values.average().toFloat()
        }

    private fun variation(values: List<Float>): Float {
        if (values.isEmpty()) {
            return 0f
        }

        val mean = values.average()

        return values.map {
            abs(it - mean)
        }.average().toFloat()
    }

    private fun percentile95(values: List<Float>): Float {
        if (values.isEmpty()) {
            return 0f
        }

        val sorted = values.sorted()
        return sorted[
            ((sorted.size - 1) * 0.95).toInt()
        ]
    }

    private fun std(m2: Double, count: Int): Double =
        if (count < 2 || m2 <= 0.0) {
            0.0
        } else {
            sqrt(m2 / (count - 1))
        }

    private fun distanceToTarget(
        profile: MotionBaselineProfile,
        target: Target
    ): Double {
        return listOf(
            relativeDistance(
                profile.accelerationP95Mean,
                target.accelerationP95
            ),
            relativeDistance(
                profile.gyroscopeP95Mean,
                target.gyroscopeP95
            ),
            relativeDistance(
                profile.accelerationVariationMean,
                target.accelerationVariation
            ),
            relativeDistance(
                profile.gyroscopeVariationMean,
                target.gyroscopeVariation
            ),
            relativeDistance(
                profile.bandAveragePowerMean,
                target.bandAveragePower
            ),
            relativeDistance(
                profile.peakNeighborhoodPowerMean,
                target.peakNeighborhoodPower
            ),
            relativeDistance(
                profile.rhythmicEnergyShareMean,
                target.rhythmicEnergyShare
            )
        ).average()
    }

    private fun relativeDistance(
        current: Double,
        target: Double
    ): Double =
        abs(current - target) /
                max(abs(target), 1e-12)

    private fun profilesEqual(
        a: MotionBaselineProfile,
        b: MotionBaselineProfile
    ): Boolean {
        val doubles = listOf(
            a.accelerationP95Mean to b.accelerationP95Mean,
            a.accelerationP95M2 to b.accelerationP95M2,
            a.gyroscopeP95Mean to b.gyroscopeP95Mean,
            a.gyroscopeP95M2 to b.gyroscopeP95M2,
            a.accelerationVariationMean to
                    b.accelerationVariationMean,
            a.accelerationVariationM2 to
                    b.accelerationVariationM2,
            a.gyroscopeVariationMean to
                    b.gyroscopeVariationMean,
            a.gyroscopeVariationM2 to
                    b.gyroscopeVariationM2,
            a.bandAveragePowerMean to
                    b.bandAveragePowerMean,
            a.bandAveragePowerM2 to
                    b.bandAveragePowerM2,
            a.peakNeighborhoodPowerMean to
                    b.peakNeighborhoodPowerMean,
            a.peakNeighborhoodPowerM2 to
                    b.peakNeighborhoodPowerM2,
            a.rhythmicEnergyShareMean to
                    b.rhythmicEnergyShareMean,
            a.rhythmicEnergyShareM2 to
                    b.rhythmicEnergyShareM2,
            a.totalBaselineSeconds to
                    b.totalBaselineSeconds
        )

        return doubles.all { (first, second) ->
            abs(first - second) <=
                    1e-10 *
                    max(1.0, max(abs(first), abs(second)))
        } &&
                a.validSessionCount == b.validSessionCount &&
                a.totalWindowCount == b.totalWindowCount
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private fun blockedRow(
        participant: Participant,
        recordType: String,
        reason: String,
        profile: MotionBaselineProfile,
        candidate: MotionAnalysisResult,
        windows: List<MotionBaselineWindowSummary>,
        blocked: Boolean,
        tremorStillDetected: Boolean?
    ): Row {
        return Row(
            participantId = participant.id,
            recordType = recordType,
            usageIndex = null,
            updateAccepted = !blocked,
            rejectionReason = reason,

            sessionsBefore = profile.validSessionCount,
            sessionsAfter = profile.validSessionCount,
            windowsBefore = profile.totalWindowCount,
            windowsAfter = profile.totalWindowCount,
            secondsBefore = profile.totalBaselineSeconds,
            secondsAfter = profile.totalBaselineSeconds,

            accelerationP95Mean = profile.accelerationP95Mean,
            accelerationP95Std = std(
                profile.accelerationP95M2,
                profile.totalWindowCount
            ),
            gyroscopeP95Mean = profile.gyroscopeP95Mean,
            gyroscopeP95Std = std(
                profile.gyroscopeP95M2,
                profile.totalWindowCount
            ),
            accelerationVariationMean =
                profile.accelerationVariationMean,
            accelerationVariationStd = std(
                profile.accelerationVariationM2,
                profile.totalWindowCount
            ),
            gyroscopeVariationMean =
                profile.gyroscopeVariationMean,
            gyroscopeVariationStd = std(
                profile.gyroscopeVariationM2,
                profile.totalWindowCount
            ),
            bandAveragePowerMean =
                profile.bandAveragePowerMean,
            bandAveragePowerStd = std(
                profile.bandAveragePowerM2,
                profile.totalWindowCount
            ),
            peakNeighborhoodPowerMean =
                profile.peakNeighborhoodPowerMean,
            peakNeighborhoodPowerStd = std(
                profile.peakNeighborhoodPowerM2,
                profile.totalWindowCount
            ),
            rhythmicEnergyShareMean =
                profile.rhythmicEnergyShareMean,
            rhythmicEnergyShareStd = std(
                profile.rhythmicEnergyShareM2,
                profile.totalWindowCount
            ),

            targetAccelerationP95 = null,
            targetGyroscopeP95 = null,
            targetAccelerationVariation = null,
            targetGyroscopeVariation = null,
            targetBandAveragePower = null,
            targetPeakNeighborhoodPower = null,
            targetRhythmicEnergyShare = null,

            distanceBefore = null,
            distanceAfter = null,
            distanceDidNotIncrease = null,

            normalScoreBefore = null,
            normalScoreAfter = null,
            normalTremorBefore = null,
            normalTremorAfter = null,

            tremorStillDetected = tremorStillDetected,
            candidateReliable = candidate.isReliable,
            candidateWindowCount = windows.size,
            pooledStatisticsMatch = null,
            profileUnchanged = blocked
        )
    }

    private fun writeCsv(
        file: File,
        rows: List<Row>
    ) {
        val header = listOf(
            "participantId",
            "recordType",
            "usageIndex",
            "updateAccepted",
            "rejectionReason",
            "validSessionCountBefore",
            "validSessionCountAfter",
            "totalWindowCountBefore",
            "totalWindowCountAfter",
            "totalBaselineSecondsBefore",
            "totalBaselineSecondsAfter",
            "accelerationP95Mean",
            "accelerationP95Std",
            "gyroscopeP95Mean",
            "gyroscopeP95Std",
            "accelerationVariationMean",
            "accelerationVariationStd",
            "gyroscopeVariationMean",
            "gyroscopeVariationStd",
            "bandAveragePowerMean",
            "bandAveragePowerStd",
            "peakNeighborhoodPowerMean",
            "peakNeighborhoodPowerStd",
            "rhythmicEnergyShareMean",
            "rhythmicEnergyShareStd",
            "targetAccelerationP95Mean",
            "targetGyroscopeP95Mean",
            "targetAccelerationVariationMean",
            "targetGyroscopeVariationMean",
            "targetBandAveragePowerMean",
            "targetPeakNeighborhoodPowerMean",
            "targetRhythmicEnergyShareMean",
            "normalizedDistanceBefore",
            "normalizedDistanceAfter",
            "distanceDidNotIncrease",
            "normalScoreBeforeUpdate",
            "normalScoreAfterUpdate",
            "normalTremorBeforeUpdate",
            "normalTremorAfterUpdate",
            "tremorStillDetected",
            "candidateReliable",
            "candidateWindowCount",
            "pooledStatisticsMatch",
            "profileUnchanged"
        )

        file.bufferedWriter().use { writer ->
            writer.appendLine(header.joinToString(","))

            rows.forEach { row ->
                writer.appendLine(
                    listOf(
                        row.participantId,
                        row.recordType,
                        row.usageIndex,
                        row.updateAccepted,
                        row.rejectionReason,
                        row.sessionsBefore,
                        row.sessionsAfter,
                        row.windowsBefore,
                        row.windowsAfter,
                        row.secondsBefore,
                        row.secondsAfter,
                        row.accelerationP95Mean,
                        row.accelerationP95Std,
                        row.gyroscopeP95Mean,
                        row.gyroscopeP95Std,
                        row.accelerationVariationMean,
                        row.accelerationVariationStd,
                        row.gyroscopeVariationMean,
                        row.gyroscopeVariationStd,
                        row.bandAveragePowerMean,
                        row.bandAveragePowerStd,
                        row.peakNeighborhoodPowerMean,
                        row.peakNeighborhoodPowerStd,
                        row.rhythmicEnergyShareMean,
                        row.rhythmicEnergyShareStd,
                        row.targetAccelerationP95,
                        row.targetGyroscopeP95,
                        row.targetAccelerationVariation,
                        row.targetGyroscopeVariation,
                        row.targetBandAveragePower,
                        row.targetPeakNeighborhoodPower,
                        row.targetRhythmicEnergyShare,
                        row.distanceBefore,
                        row.distanceAfter,
                        row.distanceDidNotIncrease,
                        row.normalScoreBefore,
                        row.normalScoreAfter,
                        row.normalTremorBefore,
                        row.normalTremorAfter,
                        row.tremorStillDetected,
                        row.candidateReliable,
                        row.candidateWindowCount,
                        row.pooledStatisticsMatch,
                        row.profileUnchanged
                    ).joinToString(",") {
                        csv(it)
                    }
                )
            }
        }
    }

    private fun summary(rows: List<Row>): String {
        val updates = rows.filter {
            it.recordType == RECORD_UPDATE
        }

        val tremor = rows.filter {
            it.recordType == RECORD_TREMOR_BLOCK
        }

        val extreme = rows.filter {
            it.recordType == RECORD_EXTREME_BLOCK
        }

        val unreliable = rows.filter {
            it.recordType == RECORD_UNRELIABLE_BLOCK
        }

        fun passed(
            values: List<Row>,
            predicate: (Row) -> Boolean
        ): String {
            val count = values.count(predicate)
            return "$count / ${values.size} " +
                    "(${percent(count, values.size)})"
        }

        return """
        Long-term synthetic hand baseline-adaptation evaluation completed.

        Virtual participants: ${participants().size}
        Accepted repeated uses: ${updates.size}
        Total output records: ${rows.size}

        Accepted baseline updates:
        ${passed(updates) { it.updateAccepted }}

        Updates adding exactly five windows:
        ${passed(updates) {
            it.windowsAfter == it.windowsBefore + 5
        }}

        Updates adding exactly one session:
        ${passed(updates) {
            it.sessionsAfter == it.sessionsBefore + 1
        }}

        Updates whose distance to the stable target did not increase:
        ${passed(updates) {
            it.distanceDidNotIncrease == true
        }}

        Accumulated profiles matching direct pooled statistics:
        ${passed(updates) {
            it.pooledStatisticsMatch == true
        }}

        Normal windows staying score 0 before updates:
        ${passed(updates) {
            it.normalScoreBefore == 0 &&
                    it.normalTremorBefore == false
        }}

        Normal windows staying score 0 after updates:
        ${passed(updates) {
            it.normalScoreAfter == 0 &&
                    it.normalTremorAfter == false
        }}

        Strong tremor still detected after adaptation:
        ${passed(tremor) {
            it.tremorStillDetected == true
        }}

        Strong-tremor candidates blocked from learning:
        ${passed(tremor) {
            it.profileUnchanged
        }}

        Sustained extreme-movement candidates blocked from learning:
        ${passed(extreme) {
            it.profileUnchanged
        }}

        Unreliable candidates blocked from learning:
        ${passed(unreliable) {
            it.profileUnchanged
        }}

        Interpretation note:
        This is a controlled engineering test of accumulated
        baseline adaptation. It does not establish clinical
        tremor or psychological-distress accuracy.
        """.trimIndent()
    }

    private fun csv(value: Any?): String {
        if (value == null) {
            return ""
        }

        val text = value.toString()

        return if (
            text.contains(",") ||
            text.contains("\"") ||
            text.contains("\n")
        ) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
    }

    private fun percent(
        numerator: Int,
        denominator: Int
    ): String {
        if (denominator == 0) {
            return "0.00%"
        }

        return String.format(
            Locale.US,
            "%.2f%%",
            numerator * 100.0 / denominator
        )
    }

    private fun outputDirectory(): File {
        val working = File(System.getProperty("user.dir"))
        val app = if (working.name == "app") {
            working
        } else {
            File(working, "app")
        }

        return File(
            app,
            "build/synthetic-hand-long-term-adaptation"
        ).apply {
            check(exists() || mkdirs())
        }
    }

    // ------------------------------------------------------------------
    // Models
    // ------------------------------------------------------------------

    private enum class SignalType {
        QUIET,
        STABLE_TREMOR,
        EXTREME_RANDOM
    }

    private data class Participant(
        val id: String,
        val initialAccNoise: Double,
        val initialGyroNoise: Double,
        val stableAccNoise: Double,
        val stableGyroNoise: Double,
        val initialSeed: Long,
        val stableSeed: Long,
        val timestampOffset: Long
    )

    private data class Statistics(
        val mean: Double,
        val m2: Double,
        val count: Int
    )

    private data class Target(
        val accelerationP95: Double,
        val gyroscopeP95: Double,
        val accelerationVariation: Double,
        val gyroscopeVariation: Double,
        val bandAveragePower: Double,
        val peakNeighborhoodPower: Double,
        val rhythmicEnergyShare: Double
    ) {
        companion object {
            fun from(
                windows: List<MotionBaselineWindowSummary>
            ): Target {
                return Target(
                    accelerationP95 =
                        windows.map {
                            it.accelerationP95
                        }.average(),
                    gyroscopeP95 =
                        windows.map {
                            it.gyroscopeP95
                        }.average(),
                    accelerationVariation =
                        windows.map {
                            it.accelerationVariation
                        }.average(),
                    gyroscopeVariation =
                        windows.map {
                            it.gyroscopeVariation
                        }.average(),
                    bandAveragePower =
                        windows.map {
                            it.bandAveragePower
                        }.average(),
                    peakNeighborhoodPower =
                        windows.map {
                            it.peakNeighborhoodPower
                        }.average(),
                    rhythmicEnergyShare =
                        windows.map {
                            it.rhythmicEnergyShare
                        }.average()
                )
            }
        }
    }

    private data class Row(
        val participantId: String,
        val recordType: String,
        val usageIndex: Int?,
        val updateAccepted: Boolean,
        val rejectionReason: String?,

        val sessionsBefore: Int,
        val sessionsAfter: Int,
        val windowsBefore: Int,
        val windowsAfter: Int,
        val secondsBefore: Double,
        val secondsAfter: Double,

        val accelerationP95Mean: Double,
        val accelerationP95Std: Double,
        val gyroscopeP95Mean: Double,
        val gyroscopeP95Std: Double,
        val accelerationVariationMean: Double,
        val accelerationVariationStd: Double,
        val gyroscopeVariationMean: Double,
        val gyroscopeVariationStd: Double,
        val bandAveragePowerMean: Double,
        val bandAveragePowerStd: Double,
        val peakNeighborhoodPowerMean: Double,
        val peakNeighborhoodPowerStd: Double,
        val rhythmicEnergyShareMean: Double,
        val rhythmicEnergyShareStd: Double,

        val targetAccelerationP95: Double?,
        val targetGyroscopeP95: Double?,
        val targetAccelerationVariation: Double?,
        val targetGyroscopeVariation: Double?,
        val targetBandAveragePower: Double?,
        val targetPeakNeighborhoodPower: Double?,
        val targetRhythmicEnergyShare: Double?,

        val distanceBefore: Double?,
        val distanceAfter: Double?,
        val distanceDidNotIncrease: Boolean?,

        val normalScoreBefore: Int?,
        val normalScoreAfter: Int?,
        val normalTremorBefore: Boolean?,
        val normalTremorAfter: Boolean?,

        val tremorStillDetected: Boolean?,
        val candidateReliable: Boolean,
        val candidateWindowCount: Int,
        val pooledStatisticsMatch: Boolean?,
        val profileUnchanged: Boolean
    )

    companion object {
        private const val SAMPLE_RATE_HZ = 100.0
        private const val SAMPLE_INTERVAL_NS = 10_000_000L
        private const val WINDOWS_PER_SESSION = 5
        private const val ACCEPTED_USE_COUNT = 500
        private const val DISTANCE_TOLERANCE = 1e-10

        private const val RECORD_UPDATE =
            "BASELINE_UPDATE"
        private const val RECORD_TREMOR_BLOCK =
            "TREMOR_LEARNING_BLOCK"
        private const val RECORD_EXTREME_BLOCK =
            "EXTREME_MOVEMENT_LEARNING_BLOCK"
        private const val RECORD_UNRELIABLE_BLOCK =
            "UNRELIABLE_LEARNING_BLOCK"
    }

    private fun participants(): List<Participant> {
        return listOf(
            Participant(
                id = "VIRTUAL_01",
                initialAccNoise = 0.004,
                initialGyroNoise = 0.002,
                stableAccNoise = 0.006,
                stableGyroNoise = 0.003,
                initialSeed = 11_001L,
                stableSeed = 11_501L,
                timestampOffset = 100_000_000L
            ),
            Participant(
                id = "VIRTUAL_02",
                initialAccNoise = 0.008,
                initialGyroNoise = 0.004,
                stableAccNoise = 0.011,
                stableGyroNoise = 0.0055,
                initialSeed = 22_001L,
                stableSeed = 22_501L,
                timestampOffset = 200_000_000L
            ),
            Participant(
                id = "VIRTUAL_03",
                initialAccNoise = 0.015,
                initialGyroNoise = 0.007,
                stableAccNoise = 0.019,
                stableGyroNoise = 0.009,
                initialSeed = 33_001L,
                stableSeed = 33_501L,
                timestampOffset = 300_000_000L
            ),
            Participant(
                id = "VIRTUAL_04",
                initialAccNoise = 0.025,
                initialGyroNoise = 0.012,
                stableAccNoise = 0.030,
                stableGyroNoise = 0.015,
                initialSeed = 44_001L,
                stableSeed = 44_501L,
                timestampOffset = 400_000_000L
            )
        )
    }
}