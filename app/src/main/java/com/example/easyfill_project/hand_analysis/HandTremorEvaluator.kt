package com.example.easyfill_project.hand_analysis

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/*
 * Pure Kotlin hand-tremor decision logic.
 *
 * This object contains no Android, Firebase, sensor-manager,
 * UI, coroutine or logging dependency.
 *
 * The production controller and the synthetic evaluation
 * runner can therefore execute the same decision logic.
 */

private const val HAND_TREMOR_MIN_HZ = 6.0
private const val HAND_TREMOR_MAX_HZ = 13.0

private const val HAND_MIN_WHOLE_CONCENTRATION = 4.0
private const val HAND_MIN_WHOLE_NARROWBAND_RATIO = 3.0
private const val HAND_MIN_WHOLE_RHYTHMIC_SHARE = 0.35

private const val HAND_TEMPORAL_WINDOW_SECONDS = 2.0
private const val HAND_TEMPORAL_WINDOW_STEP_SECONDS = 1.0
private const val HAND_TEMPORAL_WINDOW_COUNT = 4

private const val HAND_MIN_TEMPORAL_CANDIDATE_WINDOWS = 3

private const val HAND_MIN_TEMPORAL_CONCENTRATION = 3.0
private const val HAND_MIN_TEMPORAL_NARROWBAND_RATIO = 2.0
private const val HAND_MIN_TEMPORAL_RHYTHMIC_SHARE = 0.30

private const val HAND_TEMPORAL_PEAK_TOLERANCE_HZ = 1.0
private const val HAND_MAX_TEMPORAL_FREQUENCY_SPREAD_HZ = 1.0

private const val HAND_MAX_TEMPORAL_POWER_RATIO = 6.0
private const val HAND_MAX_TEMPORAL_POWER_CV = 0.70

private const val HAND_MAX_PERSONAL_RHYTHMIC_THRESHOLD = 0.75

private const val HAND_BURST_SEGMENT_COUNT = 5
private const val HAND_BURST_DOMINANCE_FACTOR = 4.0
private const val HAND_BURST_DOMINANCE_ABSOLUTE_MARGIN = 0.03

private const val HAND_PERSONAL_NORMAL_STANDARD_DEVIATION_MULTIPLIER = 2.5
private const val HAND_MIN_RELATIVE_NORMAL_SCALE = 0.15
private const val HAND_MIN_ABSOLUTE_NORMAL_SCALE = 1e-9

private const val HAND_ACCELERATION_SEVERITY_WEIGHT = 0.55
private const val HAND_GYROSCOPE_SEVERITY_WEIGHT = 0.35
private const val HAND_SPECTRAL_SEVERITY_WEIGHT = 0.10

private const val HAND_MAX_SPECTRAL_SEVERITY_LEVEL = 2.5

private const val HAND_SCORE_TWO_THRESHOLD = 0.90
private const val HAND_SCORE_THREE_THRESHOLD = 1.70
private const val HAND_SCORE_FOUR_THRESHOLD = 2.70


object HandTremorEvaluator {

    /*
     * Evaluates one reliable five-second motion window against
     * one fixed personal baseline profile.
     */
    fun evaluate(
        profile: MotionBaselineProfile,
        current: MotionAnalysisResult
    ): HandTremorEvaluation {

        require(current.isReliable) {
            "Hand tremor evaluation requires a reliable motion window"
        }

        require(profile.totalWindowCount > 0) {
            "Hand tremor evaluation requires a non-empty baseline profile"
        }

        val spectrum =
            analyzeSpectrum(
                result = current
            )

        val upperBandPower =
            personalUpperNormal(
                mean = profile.bandAveragePowerMean,
                m2 = profile.bandAveragePowerM2,
                sampleCount = profile.totalWindowCount
            )

        val upperPeakPower =
            personalUpperNormal(
                mean = profile.peakNeighborhoodPowerMean,
                m2 = profile.peakNeighborhoodPowerM2,
                sampleCount = profile.totalWindowCount
            )

        val wholeRhythmicThreshold =
            personalRhythmicThreshold(
                profile = profile,
                genericMinimum =
                    HAND_MIN_WHOLE_RHYTHMIC_SHARE
            )

        val wholePeakInBand =
            spectrum.peakFrequencyHz in
                    HAND_TREMOR_MIN_HZ..
                    HAND_TREMOR_MAX_HZ

        val wholeConcentrated =
            spectrum.concentrationRatio >=
                    HAND_MIN_WHOLE_CONCENTRATION

        val wholeNarrowband =
            spectrum.narrowbandRatio >=
                    HAND_MIN_WHOLE_NARROWBAND_RATIO

        val wholeRhythmic =
            spectrum.rhythmicEnergyShare >=
                    wholeRhythmicThreshold

        val wholePowerHigh =
            spectrum.bandAveragePower >
                    upperBandPower ||
                    spectrum.peakNeighborhoodPower >
                    upperPeakPower

        val temporal =
            analyzeTemporalConsistency(
                profile = profile,
                current = current,
                wholeWindowPeakHz =
                    spectrum.peakFrequencyHz,
                upperBandPower = upperBandPower,
                upperPeakPower = upperPeakPower
            )

        val tremorConfirmed =
            wholePeakInBand &&
                    wholeConcentrated &&
                    wholeNarrowband &&
                    wholeRhythmic &&
                    wholePowerHigh &&
                    temporal.hasTemporalCoverage &&
                    temporal.frequencyStable &&
                    temporal.powerStable &&
                    !temporal.isBurstDominated

        val severity =
            if (tremorConfirmed) {
                calculatePersonalSeverity(
                    profile = profile,
                    current = current,
                    spectrum = spectrum
                )
            } else {
                HandPersonalSeverityResult()
            }

        return HandTremorEvaluation(
            tremorConfirmed = tremorConfirmed,
            spectrum = spectrum,
            temporal = temporal,
            severity = severity,
            wholePeakInBand = wholePeakInBand,
            wholeConcentrated = wholeConcentrated,
            wholeNarrowband = wholeNarrowband,
            wholeRhythmic = wholeRhythmic,
            wholePowerHigh = wholePowerHigh,
            wholeRhythmicThreshold =
                wholeRhythmicThreshold,
            upperBandPower = upperBandPower,
            upperPeakPower = upperPeakPower
        )
    }


    private fun analyzeTemporalConsistency(
        profile: MotionBaselineProfile,
        current: MotionAnalysisResult,
        wholeWindowPeakHz: Double,
        upperBandPower: Double,
        upperPeakPower: Double
    ): HandTemporalTremorResult {

        val windowPeakFrequencies =
            mutableListOf<Double>()

        val windowConcentrations =
            mutableListOf<Double>()

        val windowNarrowbandRatios =
            mutableListOf<Double>()

        val windowRhythmicShares =
            mutableListOf<Double>()

        val windowBandPowers =
            mutableListOf<Double>()

        val windowPeakPowers =
            mutableListOf<Double>()

        val candidateFrequencies =
            mutableListOf<Double>()

        val candidatePowers =
            mutableListOf<Double>()

        val temporalRhythmicThreshold =
            personalRhythmicThreshold(
                profile = profile,
                genericMinimum =
                    HAND_MIN_TEMPORAL_RHYTHMIC_SHARE
            )

        var candidateWindowCount = 0

        for (
        windowIndex in
        0 until HAND_TEMPORAL_WINDOW_COUNT
        ) {
            val startSeconds =
                windowIndex *
                        HAND_TEMPORAL_WINDOW_STEP_SECONDS

            val window =
                sliceMotionWindow(
                    source = current,
                    startSeconds = startSeconds,
                    requestedDurationSeconds =
                        HAND_TEMPORAL_WINDOW_SECONDS
                )

            if (window == null) {
                windowPeakFrequencies.add(0.0)
                windowConcentrations.add(0.0)
                windowNarrowbandRatios.add(0.0)
                windowRhythmicShares.add(0.0)
                windowBandPowers.add(0.0)
                windowPeakPowers.add(0.0)
                continue
            }

            val spectrum =
                analyzeSpectrum(
                    result = window
                )

            windowPeakFrequencies.add(
                spectrum.peakFrequencyHz
            )

            windowConcentrations.add(
                spectrum.concentrationRatio
            )

            windowNarrowbandRatios.add(
                spectrum.narrowbandRatio
            )

            windowRhythmicShares.add(
                spectrum.rhythmicEnergyShare
            )

            windowBandPowers.add(
                spectrum.bandAveragePower
            )

            windowPeakPowers.add(
                spectrum.peakNeighborhoodPower
            )

            val peakInBand =
                spectrum.peakFrequencyHz in
                        HAND_TREMOR_MIN_HZ..
                        HAND_TREMOR_MAX_HZ

            val peakCloseToWholeWindow =
                wholeWindowPeakHz > 0.0 &&
                        abs(
                            spectrum.peakFrequencyHz -
                                    wholeWindowPeakHz
                        ) <=
                        HAND_TEMPORAL_PEAK_TOLERANCE_HZ

            val concentrated =
                spectrum.concentrationRatio >=
                        HAND_MIN_TEMPORAL_CONCENTRATION

            val narrowband =
                spectrum.narrowbandRatio >=
                        HAND_MIN_TEMPORAL_NARROWBAND_RATIO

            val rhythmic =
                spectrum.rhythmicEnergyShare >=
                        temporalRhythmicThreshold

            val powerHigh =
                spectrum.bandAveragePower >
                        upperBandPower ||
                        spectrum.peakNeighborhoodPower >
                        upperPeakPower

            val isCandidate =
                peakInBand &&
                        peakCloseToWholeWindow &&
                        concentrated &&
                        narrowband &&
                        rhythmic &&
                        powerHigh

            if (isCandidate) {
                candidateWindowCount += 1

                candidateFrequencies.add(
                    spectrum.peakFrequencyHz
                )

                candidatePowers.add(
                    spectrum.peakNeighborhoodPower
                )
            }
        }

        val hasTemporalCoverage =
            candidateWindowCount >=
                    HAND_MIN_TEMPORAL_CANDIDATE_WINDOWS

        val frequencySpread =
            calculateFrequencySpread(
                frequencies = candidateFrequencies
            )

        val frequencyStable =
            hasTemporalCoverage &&
                    frequencySpread <=
                    HAND_MAX_TEMPORAL_FREQUENCY_SPREAD_HZ

        val powerRatio =
            calculatePowerRatio(
                values = candidatePowers
            )

        val powerCoefficientOfVariation =
            calculateCoefficientOfVariation(
                values = candidatePowers
            )

        val powerStable =
            hasTemporalCoverage &&
                    powerRatio <=
                    HAND_MAX_TEMPORAL_POWER_RATIO &&
                    powerCoefficientOfVariation <=
                    HAND_MAX_TEMPORAL_POWER_CV

        return HandTemporalTremorResult(
            candidateWindowCount =
                candidateWindowCount,
            hasTemporalCoverage =
                hasTemporalCoverage,
            frequencyStable =
                frequencyStable,
            powerStable =
                powerStable,
            isBurstDominated =
                current.accelerationValues
                    .isBurstDominated(),
            candidateFrequencySpreadHz =
                frequencySpread,
            candidatePowerRatio =
                powerRatio,
            candidatePowerCoefficientOfVariation =
                powerCoefficientOfVariation,
            windowPeakFrequenciesHz =
                windowPeakFrequencies,
            windowConcentrations =
                windowConcentrations,
            windowNarrowbandRatios =
                windowNarrowbandRatios,
            windowRhythmicShares =
                windowRhythmicShares,
            windowBandPowers =
                windowBandPowers,
            windowPeakNeighborhoodPowers =
                windowPeakPowers
        )
    }


    private fun calculatePersonalSeverity(
        profile: MotionBaselineProfile,
        current: MotionAnalysisResult,
        spectrum: TremorSpectrumResult
    ): HandPersonalSeverityResult {

        val upperAccelerationP95 =
            personalUpperNormal(
                mean = profile.accelerationP95Mean,
                m2 = profile.accelerationP95M2,
                sampleCount = profile.totalWindowCount
            )

        val upperAccelerationVariation =
            personalUpperNormal(
                mean =
                    profile.accelerationVariationMean,
                m2 =
                    profile.accelerationVariationM2,
                sampleCount =
                    profile.totalWindowCount
            )

        val upperGyroscopeP95 =
            personalUpperNormal(
                mean = profile.gyroscopeP95Mean,
                m2 = profile.gyroscopeP95M2,
                sampleCount = profile.totalWindowCount
            )

        val upperGyroscopeVariation =
            personalUpperNormal(
                mean =
                    profile.gyroscopeVariationMean,
                m2 =
                    profile.gyroscopeVariationM2,
                sampleCount =
                    profile.totalWindowCount
            )

        val upperBandPower =
            personalUpperNormal(
                mean = profile.bandAveragePowerMean,
                m2 = profile.bandAveragePowerM2,
                sampleCount = profile.totalWindowCount
            )

        val upperPeakPower =
            personalUpperNormal(
                mean =
                    profile.peakNeighborhoodPowerMean,
                m2 =
                    profile.peakNeighborhoodPowerM2,
                sampleCount =
                    profile.totalWindowCount
            )

        val accelerationLevel =
            max(
                logarithmicExcessLevel(
                    currentValue =
                        current.accelerationP95.toDouble(),
                    upperNormalValue =
                        upperAccelerationP95
                ),
                logarithmicExcessLevel(
                    currentValue =
                        current.accelerationVariation.toDouble(),
                    upperNormalValue =
                        upperAccelerationVariation
                )
            )

        val gyroscopeLevel =
            max(
                logarithmicExcessLevel(
                    currentValue =
                        current.gyroscopeP95.toDouble(),
                    upperNormalValue =
                        upperGyroscopeP95
                ),
                logarithmicExcessLevel(
                    currentValue =
                        current.gyroscopeVariation.toDouble(),
                    upperNormalValue =
                        upperGyroscopeVariation
                )
            )

        val spectralLevel =
            (
                    max(
                        logarithmicExcessLevel(
                            currentValue =
                                spectrum.bandAveragePower,
                            upperNormalValue =
                                upperBandPower
                        ),
                        logarithmicExcessLevel(
                            currentValue =
                                spectrum.peakNeighborhoodPower,
                            upperNormalValue =
                                upperPeakPower
                        )
                    ) / 2.0
                    ).coerceAtMost(
                    HAND_MAX_SPECTRAL_SEVERITY_LEVEL
                )

        val severityIndex =
            accelerationLevel *
                    HAND_ACCELERATION_SEVERITY_WEIGHT +
                    gyroscopeLevel *
                    HAND_GYROSCOPE_SEVERITY_WEIGHT +
                    spectralLevel *
                    HAND_SPECTRAL_SEVERITY_WEIGHT

        val score =
            when {
                severityIndex <
                        HAND_SCORE_TWO_THRESHOLD -> 1

                severityIndex <
                        HAND_SCORE_THREE_THRESHOLD -> 2

                severityIndex <
                        HAND_SCORE_FOUR_THRESHOLD -> 3

                else -> 4
            }

        return HandPersonalSeverityResult(
            score = score,
            severityIndex = severityIndex,
            accelerationLevel = accelerationLevel,
            gyroscopeLevel = gyroscopeLevel,
            spectralLevel = spectralLevel,
            upperAccelerationP95 =
                upperAccelerationP95,
            upperAccelerationVariation =
                upperAccelerationVariation,
            upperGyroscopeP95 =
                upperGyroscopeP95,
            upperGyroscopeVariation =
                upperGyroscopeVariation,
            upperBandPower = upperBandPower,
            upperPeakPower = upperPeakPower
        )
    }


    private fun analyzeSpectrum(
        result: MotionAnalysisResult
    ): TremorSpectrumResult {

        return TremorSpectrumAnalyzer
            .analyzeAxes(
                xValues =
                    result.accelerationXValues,
                yValues =
                    result.accelerationYValues,
                zValues =
                    result.accelerationZValues,
                timestampsNs =
                    result.accelerationTimestampsNs,
                minHz =
                    HAND_TREMOR_MIN_HZ,
                maxHz =
                    HAND_TREMOR_MAX_HZ
            )
    }


    private fun sliceMotionWindow(
        source: MotionAnalysisResult,
        startSeconds: Double,
        requestedDurationSeconds: Double
    ): MotionAnalysisResult? {

        if (
            startSeconds < 0.0 ||
            requestedDurationSeconds <= 0.0 ||
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

        val actualRequestedDuration =
            (
                    actualEndTimestampNs -
                            requestedStartTimestampNs
                    ) / 1_000_000_000.0

        if (
            requestedStartTimestampNs >=
            commonEndTimestampNs ||
            actualRequestedDuration <
            requestedDurationSeconds * 0.85
        ) {
            return null
        }

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

        val accelerationDuration =
            accelerationTimestamps
                .durationSeconds()

        val gyroscopeDuration =
            gyroscopeTimestamps
                .durationSeconds()

        val durationSeconds =
            minOf(
                accelerationDuration,
                gyroscopeDuration
            )

        val isReliable =
            accelerationValues.isNotEmpty() &&
                    gyroscopeValues.isNotEmpty() &&
                    durationSeconds >=
                    requestedDurationSeconds * 0.85

        return MotionAnalysisResult(
            durationSeconds = durationSeconds,
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
            isReliable = isReliable,
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
            timestampsNs.indexOfFirst { timestamp ->
                timestamp >= startTimestampNs
            }

        val lastIndex =
            timestampsNs.indexOfLast { timestamp ->
                timestamp <= endTimestampNs
            }

        if (
            firstIndex < 0 ||
            lastIndex < firstIndex
        ) {
            return null
        }

        return firstIndex..lastIndex
    }


    private fun personalUpperNormal(
        mean: Double,
        m2: Double,
        sampleCount: Int
    ): Double {

        val standardDeviation =
            calculateStandardDeviation(
                m2 = m2,
                sampleCount = sampleCount
            )

        val relativeScaleFloor =
            abs(mean) *
                    HAND_MIN_RELATIVE_NORMAL_SCALE

        val scale =
            max(
                standardDeviation,
                max(
                    relativeScaleFloor,
                    HAND_MIN_ABSOLUTE_NORMAL_SCALE
                )
            )

        return mean +
                HAND_PERSONAL_NORMAL_STANDARD_DEVIATION_MULTIPLIER *
                scale
    }


    private fun personalRhythmicThreshold(
        profile: MotionBaselineProfile,
        genericMinimum: Double
    ): Double {

        val personalUpper =
            personalUpperNormal(
                mean =
                    profile.rhythmicEnergyShareMean,
                m2 =
                    profile.rhythmicEnergyShareM2,
                sampleCount =
                    profile.totalWindowCount
            )

        return max(
            genericMinimum,
            personalUpper
        ).coerceAtMost(
            HAND_MAX_PERSONAL_RHYTHMIC_THRESHOLD
        )
    }


    private fun logarithmicExcessLevel(
        currentValue: Double,
        upperNormalValue: Double
    ): Double {

        if (
            !currentValue.isFinite() ||
            !upperNormalValue.isFinite() ||
            currentValue <= upperNormalValue ||
            upperNormalValue <= 0.0
        ) {
            return 0.0
        }

        val ratio =
            currentValue /
                    upperNormalValue

        return ln(ratio) /
                ln(2.0)
    }


    private fun calculateFrequencySpread(
        frequencies: List<Double>
    ): Double {

        val validFrequencies =
            frequencies.filter { frequency ->
                frequency.isFinite() &&
                        frequency > 0.0
            }

        if (validFrequencies.size < 2) {
            return Double.POSITIVE_INFINITY
        }

        return validFrequencies.maxOrNull()!! -
                validFrequencies.minOrNull()!!
    }


    private fun calculatePowerRatio(
        values: List<Double>
    ): Double {

        val positiveValues =
            values.filter { value ->
                value.isFinite() &&
                        value > 0.0
            }

        if (positiveValues.size < 2) {
            return Double.POSITIVE_INFINITY
        }

        val minimum =
            positiveValues.minOrNull()
                ?: return Double.POSITIVE_INFINITY

        val maximum =
            positiveValues.maxOrNull()
                ?: return Double.POSITIVE_INFINITY

        return maximum / minimum
    }


    private fun calculateCoefficientOfVariation(
        values: List<Double>
    ): Double {

        val validValues =
            values.filter { value ->
                value.isFinite() &&
                        value >= 0.0
            }

        if (validValues.size < 2) {
            return Double.POSITIVE_INFINITY
        }

        val mean =
            validValues.average()

        if (mean <= 0.0) {
            return Double.POSITIVE_INFINITY
        }

        val variance =
            validValues
                .map { value ->
                    val difference =
                        value - mean

                    difference * difference
                }
                .average()

        return sqrt(variance) / mean
    }


    private fun calculateStandardDeviation(
        m2: Double,
        sampleCount: Int
    ): Double {

        if (
            sampleCount < 2 ||
            !m2.isFinite() ||
            m2 <= 0.0
        ) {
            return 0.0
        }

        return sqrt(
            m2 /
                    (sampleCount - 1)
        )
    }


    private fun List<Float>.isBurstDominated():
            Boolean {

        if (
            size <
            HAND_BURST_SEGMENT_COUNT * 2
        ) {
            return false
        }

        val segmentSize =
            size /
                    HAND_BURST_SEGMENT_COUNT

        val segmentAverages =
            (
                    0 until
                            HAND_BURST_SEGMENT_COUNT
                    ).map { segmentIndex ->

                    val startIndex =
                        segmentIndex *
                                segmentSize

                    val endIndex =
                        if (
                            segmentIndex ==
                            HAND_BURST_SEGMENT_COUNT - 1
                        ) {
                            size
                        } else {
                            startIndex + segmentSize
                        }

                    subList(
                        startIndex,
                        endIndex
                    ).averageOrZero()
                }

        val sorted =
            segmentAverages.sorted()

        val median =
            sorted[
                sorted.size / 2
            ]

        val largest =
            sorted.last()

        return largest >
                median *
                HAND_BURST_DOMINANCE_FACTOR +
                HAND_BURST_DOMINANCE_ABSOLUTE_MARGIN
    }


    private fun List<Long>.durationSeconds():
            Double {

        if (size < 2) {
            return 0.0
        }

        return (
                last() - first()
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
                value - average
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
                            sorted.size - 1
                            ) * 0.95
                    ).toInt()

        return sorted[index]
    }
}


data class HandTremorEvaluation(
    val tremorConfirmed: Boolean,

    val spectrum: TremorSpectrumResult,
    val temporal: HandTemporalTremorResult,
    val severity: HandPersonalSeverityResult,

    val wholePeakInBand: Boolean,
    val wholeConcentrated: Boolean,
    val wholeNarrowband: Boolean,
    val wholeRhythmic: Boolean,
    val wholePowerHigh: Boolean,

    val wholeRhythmicThreshold: Double,

    val upperBandPower: Double,
    val upperPeakPower: Double
)


data class HandPersonalSeverityResult(
    val score: Int = 0,

    val severityIndex: Double = 0.0,

    val accelerationLevel: Double = 0.0,
    val gyroscopeLevel: Double = 0.0,
    val spectralLevel: Double = 0.0,

    val upperAccelerationP95: Double = 0.0,
    val upperAccelerationVariation: Double = 0.0,

    val upperGyroscopeP95: Double = 0.0,
    val upperGyroscopeVariation: Double = 0.0,

    val upperBandPower: Double = 0.0,
    val upperPeakPower: Double = 0.0
)


data class HandTemporalTremorResult(
    val candidateWindowCount: Int = 0,

    val hasTemporalCoverage: Boolean = false,
    val frequencyStable: Boolean = false,
    val powerStable: Boolean = false,
    val isBurstDominated: Boolean = false,

    val candidateFrequencySpreadHz: Double =
        Double.POSITIVE_INFINITY,

    val candidatePowerRatio: Double =
        Double.POSITIVE_INFINITY,

    val candidatePowerCoefficientOfVariation: Double =
        Double.POSITIVE_INFINITY,

    val windowPeakFrequenciesHz: List<Double> =
        emptyList(),

    val windowConcentrations: List<Double> =
        emptyList(),

    val windowNarrowbandRatios: List<Double> =
        emptyList(),

    val windowRhythmicShares: List<Double> =
        emptyList(),

    val windowBandPowers: List<Double> =
        emptyList(),

    val windowPeakNeighborhoodPowers: List<Double> =
        emptyList()
)