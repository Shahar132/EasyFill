package com.example.easyfill_project.hand_analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The accelerometer signal is filtered upstream between
 * approximately 2 Hz and 20 Hz.
 */
private const val ANALYSIS_MIN_HZ = 2.0
private const val ANALYSIS_MAX_HZ = 20.0

/*
 * Physiological tremor power is measured around the dominant
 * frequency using a narrow frequency neighborhood.
 */
private const val PEAK_NEIGHBORHOOD_HALF_WIDTH_HZ = 1.0

/*
 * The signed acceleration axes are resampled to a fixed rate
 * before FFT.
 *
 * This makes two-second, five-second and ten-second windows
 * comparable across devices with different sensor rates.
 */
private const val TARGET_SAMPLE_RATE_HZ = 100.0

/*
 * The original sensor rate must remain high enough to
 * represent frequencies up to 20 Hz.
 */
private const val MIN_SOURCE_SAMPLE_RATE_HZ = 45.0

/*
 * A large gap means that interpolation would invent too much
 * missing sensor information.
 */
private const val MAX_ALLOWED_SAMPLE_GAP_SECONDS = 0.10

/*
 * The shortest internal temporal window is two seconds.
 *
 * A tolerance is allowed for Android scheduling and sensor
 * timestamp boundaries.
 */
private const val MIN_ANALYSIS_DURATION_SECONDS = 1.50
private const val MIN_RESAMPLED_SAMPLE_COUNT = 128

/*
 * At 100 Hz:
 *
 * - 2 seconds produce about 200 samples and FFT size 256
 * - 5 seconds produce about 500 samples and FFT size 512
 * - 10 seconds produce about 1000 samples and FFT size 1024
 */
private const val MIN_FFT_SIZE = 128
private const val MAX_FFT_SIZE = 2048

private const val MIN_BACKGROUND_POWER = 1e-12


/*
 * Spectral result calculated from one motion window.
 */
data class TremorSpectrumResult(

    /*
     * Dominant frequency inside the requested tremor band.
     */
    val peakFrequencyHz: Double,

    /*
     * Power spectral density at the dominant frequency.
     */
    val peakPower: Double,

    /*
     * Dominant peak power divided by average power outside
     * the narrow peak neighborhood.
     *
     * The background is calculated inside 2-20 Hz.
     */
    val concentrationRatio: Double,

    /*
     * Average power across the complete requested tremor band.
     */
    val bandAveragePower: Double,

    /*
     * Average power around the dominant peak divided by
     * average power outside the peak neighborhood.
     */
    val narrowbandRatio: Double,

    /*
     * Average power inside the narrow neighborhood around the
     * dominant frequency.
     */
    val peakNeighborhoodPower: Double,

    /*
     * Portion of all 2-20 Hz power concentrated around the
     * dominant tremor frequency.
     *
     * A periodic tremor should generally have a larger share
     * than irregular phone movement.
     */
    val rhythmicEnergyShare: Double,

    /*
     * Total spectral power inside 2-20 Hz.
     */
    val totalAnalyzedPower: Double,

    /*
     * Number of uniformly resampled values used by the FFT.
     */
    val sampleCount: Int,

    /*
     * Fixed sampling rate used by the FFT.
     */
    val sampleRateHz: Double,

    /*
     * Average sensor sampling rate before resampling.
     */
    val sourceSampleRateHz: Double,

    /*
     * Actual duration represented by the analyzed samples.
     */
    val durationSeconds: Double,

    /*
     * Distance between neighboring FFT frequency bins.
     */
    val frequencyResolutionHz: Double
)


object TremorSpectrumAnalyzer {

    /*
     * Analyzes three filtered signed acceleration axes.
     *
     * The input lists and timestamps must be aligned:
     * the same index represents the same accelerometer event.
     */
    fun analyzeAxes(
        xValues: List<Float>,
        yValues: List<Float>,
        zValues: List<Float>,
        timestampsNs: List<Long>,
        minHz: Double,
        maxHz: Double
    ): TremorSpectrumResult {

        if (minHz >= maxHz) {
            return emptyResult()
        }

        val commonSampleCount =
            minOf(
                xValues.size,
                yValues.size,
                zValues.size,
                timestampsNs.size
            )

        if (commonSampleCount < 2) {
            return emptyResult()
        }

        /*
         * Use the latest common aligned portion if an
         * unexpected list-size difference exists.
         */
        val sourceX =
            xValues.takeLast(
                commonSampleCount
            )

        val sourceY =
            yValues.takeLast(
                commonSampleCount
            )

        val sourceZ =
            zValues.takeLast(
                commonSampleCount
            )

        val sourceTimestamps =
            timestampsNs.takeLast(
                commonSampleCount
            )

        if (
            !timestampsAreStrictlyIncreasing(
                timestampsNs =
                    sourceTimestamps
            )
        ) {
            return emptyResult()
        }

        val firstTimestampNs =
            sourceTimestamps.first()

        val lastTimestampNs =
            sourceTimestamps.last()

        val sourceDurationSeconds =
            (
                    lastTimestampNs -
                            firstTimestampNs
                    ) / 1_000_000_000.0

        if (
            !sourceDurationSeconds.isFinite() ||
            sourceDurationSeconds <
            MIN_ANALYSIS_DURATION_SECONDS
        ) {
            return emptyResult(
                sourceSampleRateHz =
                    calculateSourceSampleRate(
                        sampleCount =
                            commonSampleCount,

                        durationSeconds =
                            sourceDurationSeconds
                    ),

                durationSeconds =
                    sourceDurationSeconds
            )
        }

        val sourceSampleRateHz =
            calculateSourceSampleRate(
                sampleCount =
                    commonSampleCount,

                durationSeconds =
                    sourceDurationSeconds
            )

        if (
            !sourceSampleRateHz.isFinite() ||
            sourceSampleRateHz <
            MIN_SOURCE_SAMPLE_RATE_HZ
        ) {
            return emptyResult(
                sourceSampleRateHz =
                    sourceSampleRateHz,

                durationSeconds =
                    sourceDurationSeconds
            )
        }

        if (
            containsExcessiveTimestampGap(
                timestampsNs =
                    sourceTimestamps
            )
        ) {
            return emptyResult(
                sourceSampleRateHz =
                    sourceSampleRateHz,

                durationSeconds =
                    sourceDurationSeconds
            )
        }

        /*
         * Convert irregular Android sensor events into
         * uniformly spaced 100 Hz samples.
         */
        val resampled =
            resampleAxes(
                xValues =
                    sourceX,

                yValues =
                    sourceY,

                zValues =
                    sourceZ,

                timestampsNs =
                    sourceTimestamps
            )

        if (
            resampled.sampleCount <
            MIN_RESAMPLED_SAMPLE_COUNT
        ) {
            return emptyResult(
                sampleCount =
                    resampled.sampleCount,

                sampleRateHz =
                    TARGET_SAMPLE_RATE_HZ,

                sourceSampleRateHz =
                    sourceSampleRateHz,

                durationSeconds =
                    resampled.durationSeconds
            )
        }

        /*
         * Limit an unexpectedly long input while preserving
         * the most recent samples.
         */
        val usableSampleCount =
            min(
                resampled.sampleCount,
                MAX_FFT_SIZE
            )

        val usableX =
            resampled.xValues.takeLast(
                usableSampleCount
            )

        val usableY =
            resampled.yValues.takeLast(
                usableSampleCount
            )

        val usableZ =
            resampled.zValues.takeLast(
                usableSampleCount
            )

        val usableDurationSeconds =
            if (usableSampleCount >= 2) {
                (
                        usableSampleCount -
                                1
                        ) / TARGET_SAMPLE_RATE_HZ
            } else {
                0.0
            }

        val fftSize =
            nextPowerOfTwo(
                usableSampleCount
            ).coerceIn(
                minimumValue =
                    MIN_FFT_SIZE,

                maximumValue =
                    MAX_FFT_SIZE
            )

        /*
         * Includes DC and the Nyquist bin.
         */
        val combinedPowerSpectrum =
            DoubleArray(
                fftSize / 2 + 1
            )

        addAxisPowerSpectrum(
            values =
                usableX,

            fftSize =
                fftSize,

            combinedPowerSpectrum =
                combinedPowerSpectrum
        )

        addAxisPowerSpectrum(
            values =
                usableY,

            fftSize =
                fftSize,

            combinedPowerSpectrum =
                combinedPowerSpectrum
        )

        addAxisPowerSpectrum(
            values =
                usableZ,

            fftSize =
                fftSize,

            combinedPowerSpectrum =
                combinedPowerSpectrum
        )

        /*
         * Average the three independent axis spectra.
         */
        for (
        bin in
        combinedPowerSpectrum.indices
        ) {
            combinedPowerSpectrum[bin] /=
                3.0
        }

        val nyquistHz =
            TARGET_SAMPLE_RATE_HZ /
                    2.0

        val effectiveAnalysisMaxHz =
            min(
                ANALYSIS_MAX_HZ,
                nyquistHz
            )

        val effectiveBandMinHz =
            max(
                minHz,
                ANALYSIS_MIN_HZ
            )

        val effectiveBandMaxHz =
            min(
                maxHz,
                effectiveAnalysisMaxHz
            )

        val frequencyResolutionHz =
            TARGET_SAMPLE_RATE_HZ /
                    fftSize

        if (
            effectiveAnalysisMaxHz <=
            ANALYSIS_MIN_HZ ||
            effectiveBandMinHz >=
            effectiveBandMaxHz
        ) {
            return emptyResult(
                sampleCount =
                    usableSampleCount,

                sampleRateHz =
                    TARGET_SAMPLE_RATE_HZ,

                sourceSampleRateHz =
                    sourceSampleRateHz,

                durationSeconds =
                    usableDurationSeconds,

                frequencyResolutionHz =
                    frequencyResolutionHz
            )
        }

        /*
         * First pass:
         *
         * Find the dominant peak inside the requested tremor
         * band.
         */
        var peakFrequencyHz = 0.0
        var peakPower = 0.0
        var peakBin = -1

        for (
        bin in 1 until
                combinedPowerSpectrum.size
        ) {
            val frequencyHz =
                bin *
                        TARGET_SAMPLE_RATE_HZ /
                        fftSize

            if (frequencyHz < effectiveBandMinHz) {
                continue
            }

            if (frequencyHz > effectiveBandMaxHz) {
                break
            }

            val power =
                combinedPowerSpectrum[bin]

            if (power > peakPower) {
                peakPower = power
                peakFrequencyHz =
                    frequencyHz
                peakBin = bin
            }
        }

        if (peakBin < 0) {
            return emptyResult(
                sampleCount =
                    usableSampleCount,

                sampleRateHz =
                    TARGET_SAMPLE_RATE_HZ,

                sourceSampleRateHz =
                    sourceSampleRateHz,

                durationSeconds =
                    usableDurationSeconds,

                frequencyResolutionHz =
                    frequencyResolutionHz
            )
        }

        val neighborhoodMinHz =
            max(
                ANALYSIS_MIN_HZ,
                peakFrequencyHz -
                        PEAK_NEIGHBORHOOD_HALF_WIDTH_HZ
            )

        val neighborhoodMaxHz =
            min(
                effectiveAnalysisMaxHz,
                peakFrequencyHz +
                        PEAK_NEIGHBORHOOD_HALF_WIDTH_HZ
            )

        /*
         * Second pass:
         *
         * Calculate:
         *
         * - total 2-20 Hz power
         * - average tremor-band power
         * - power around the dominant frequency
         * - background outside the peak neighborhood
         */
        var totalAnalyzedPower = 0.0

        var bandPowerSum = 0.0
        var bandBinCount = 0

        var neighborhoodPowerSum = 0.0
        var neighborhoodBinCount = 0

        var backgroundPowerSum = 0.0
        var backgroundBinCount = 0

        for (
        bin in 1 until
                combinedPowerSpectrum.size
        ) {
            val frequencyHz =
                bin *
                        TARGET_SAMPLE_RATE_HZ /
                        fftSize

            if (frequencyHz < ANALYSIS_MIN_HZ) {
                continue
            }

            if (frequencyHz > effectiveAnalysisMaxHz) {
                break
            }

            val power =
                combinedPowerSpectrum[bin]

            totalAnalyzedPower +=
                power

            if (
                frequencyHz in
                effectiveBandMinHz..
                effectiveBandMaxHz
            ) {
                bandPowerSum +=
                    power

                bandBinCount += 1
            }

            if (
                frequencyHz in
                neighborhoodMinHz..
                neighborhoodMaxHz
            ) {
                neighborhoodPowerSum +=
                    power

                neighborhoodBinCount += 1

            } else {
                /*
                 * Background includes all other frequencies
                 * inside 2-20 Hz, including frequencies in the
                 * broad tremor band that are not close to the
                 * dominant peak.
                 *
                 * This makes broad irregular movement less
                 * likely to appear narrowly rhythmic.
                 */
                backgroundPowerSum +=
                    power

                backgroundBinCount += 1
            }
        }

        if (
            totalAnalyzedPower <= 0.0 ||
            bandBinCount == 0 ||
            neighborhoodBinCount == 0
        ) {
            return emptyResult(
                sampleCount =
                    usableSampleCount,

                sampleRateHz =
                    TARGET_SAMPLE_RATE_HZ,

                sourceSampleRateHz =
                    sourceSampleRateHz,

                durationSeconds =
                    usableDurationSeconds,

                frequencyResolutionHz =
                    frequencyResolutionHz
            )
        }

        val bandAveragePower =
            bandPowerSum /
                    bandBinCount

        val peakNeighborhoodPower =
            neighborhoodPowerSum /
                    neighborhoodBinCount

        val averageBackgroundPower =
            if (backgroundBinCount > 0) {
                backgroundPowerSum /
                        backgroundBinCount
            } else {
                0.0
            }

        val hasValidBackground =
            averageBackgroundPower >
                    MIN_BACKGROUND_POWER

        val concentrationRatio =
            if (hasValidBackground) {
                peakPower /
                        averageBackgroundPower
            } else {
                0.0
            }

        val narrowbandRatio =
            if (hasValidBackground) {
                peakNeighborhoodPower /
                        averageBackgroundPower
            } else {
                0.0
            }

        val rhythmicEnergyShare =
            (
                    neighborhoodPowerSum /
                            totalAnalyzedPower
                    ).coerceIn(
                    minimumValue = 0.0,
                    maximumValue = 1.0
                )

        return TremorSpectrumResult(
            peakFrequencyHz =
                peakFrequencyHz,

            peakPower =
                peakPower,

            concentrationRatio =
                concentrationRatio,

            bandAveragePower =
                bandAveragePower,

            narrowbandRatio =
                narrowbandRatio,

            peakNeighborhoodPower =
                peakNeighborhoodPower,

            rhythmicEnergyShare =
                rhythmicEnergyShare,

            totalAnalyzedPower =
                totalAnalyzedPower,

            sampleCount =
                usableSampleCount,

            sampleRateHz =
                TARGET_SAMPLE_RATE_HZ,

            sourceSampleRateHz =
                sourceSampleRateHz,

            durationSeconds =
                usableDurationSeconds,

            frequencyResolutionHz =
                frequencyResolutionHz
        )
    }

    /*
     * Resamples all three axes onto the same fixed timeline
     * using linear interpolation.
     */
    private fun resampleAxes(
        xValues: List<Float>,
        yValues: List<Float>,
        zValues: List<Float>,
        timestampsNs: List<Long>
    ): ResampledAxes {
        val startTimestampNs =
            timestampsNs.first()

        val endTimestampNs =
            timestampsNs.last()

        val targetIntervalNs =
            (
                    1_000_000_000.0 /
                            TARGET_SAMPLE_RATE_HZ
                    ).toLong()

        val durationNs =
            endTimestampNs -
                    startTimestampNs

        val targetSampleCount =
            floor(
                durationNs.toDouble() /
                        targetIntervalNs
            ).toInt() + 1

        if (targetSampleCount < 2) {
            return ResampledAxes()
        }

        val resampledX =
            ArrayList<Float>(
                targetSampleCount
            )

        val resampledY =
            ArrayList<Float>(
                targetSampleCount
            )

        val resampledZ =
            ArrayList<Float>(
                targetSampleCount
            )

        var sourceIndex = 0

        for (
        targetIndex in
        0 until targetSampleCount
        ) {
            val targetTimestampNs =
                startTimestampNs +
                        targetIndex *
                        targetIntervalNs

            while (
                sourceIndex + 1 <
                timestampsNs.size &&
                timestampsNs[
                    sourceIndex + 1
                ] <
                targetTimestampNs
            ) {
                sourceIndex += 1
            }

            if (
                sourceIndex + 1 >=
                timestampsNs.size
            ) {
                resampledX.add(
                    xValues.last()
                )

                resampledY.add(
                    yValues.last()
                )

                resampledZ.add(
                    zValues.last()
                )

                continue
            }

            val leftTimestampNs =
                timestampsNs[sourceIndex]

            val rightTimestampNs =
                timestampsNs[
                    sourceIndex + 1
                ]

            val intervalNs =
                rightTimestampNs -
                        leftTimestampNs

            val interpolationRatio =
                if (intervalNs > 0L) {
                    (
                            targetTimestampNs -
                                    leftTimestampNs
                            ).toDouble() /
                            intervalNs.toDouble()
                } else {
                    0.0
                }.coerceIn(
                    minimumValue = 0.0,
                    maximumValue = 1.0
                )

            resampledX.add(
                interpolate(
                    left =
                        xValues[sourceIndex],

                    right =
                        xValues[
                            sourceIndex + 1
                        ],

                    ratio =
                        interpolationRatio
                )
            )

            resampledY.add(
                interpolate(
                    left =
                        yValues[sourceIndex],

                    right =
                        yValues[
                            sourceIndex + 1
                        ],

                    ratio =
                        interpolationRatio
                )
            )

            resampledZ.add(
                interpolate(
                    left =
                        zValues[sourceIndex],

                    right =
                        zValues[
                            sourceIndex + 1
                        ],

                    ratio =
                        interpolationRatio
                )
            )
        }

        return ResampledAxes(
            xValues =
                resampledX,

            yValues =
                resampledY,

            zValues =
                resampledZ,

            sampleCount =
                targetSampleCount,

            durationSeconds =
                (
                        targetSampleCount -
                                1
                        ) / TARGET_SAMPLE_RATE_HZ
        )
    }

    private fun interpolate(
        left: Float,
        right: Float,
        ratio: Double
    ): Float {
        return (
                left +
                        (
                                right -
                                        left
                                ) * ratio
                ).toFloat()
    }

    /*
     * Adds one axis's normalized one-sided power spectrum to
     * the combined spectrum.
     */
    private fun addAxisPowerSpectrum(
        values: List<Float>,
        fftSize: Int,
        combinedPowerSpectrum: DoubleArray
    ) {
        if (
            values.size <
            MIN_RESAMPLED_SAMPLE_COUNT
        ) {
            return
        }

        val usableCount =
            min(
                values.size,
                fftSize
            )

        val usableValues =
            values.takeLast(
                usableCount
            )

        /*
         * Remove residual axis offset before FFT.
         */
        val mean =
            usableValues.average()

        val real =
            DoubleArray(
                fftSize
            )

        val imaginary =
            DoubleArray(
                fftSize
            )

        var windowEnergy = 0.0

        /*
         * Apply a Hamming window to reduce spectral leakage.
         */
        for (
        index in
        0 until usableCount
        ) {
            val hamming =
                if (usableCount > 1) {
                    0.54 -
                            0.46 *
                            cos(
                                2.0 *
                                        PI *
                                        index /
                                        (
                                                usableCount -
                                                        1
                                                )
                            )
                } else {
                    1.0
                }

            val centeredValue =
                usableValues[index] -
                        mean

            real[index] =
                centeredValue *
                        hamming

            windowEnergy +=
                hamming *
                        hamming
        }

        if (windowEnergy <= 0.0) {
            return
        }

        fft(
            real =
                real,

            imaginary =
                imaginary
        )

        /*
         * One-sided periodogram normalization.
         */
        val normalization =
            TARGET_SAMPLE_RATE_HZ *
                    windowEnergy

        val halfSize =
            fftSize /
                    2

        for (
        bin in 1..halfSize
        ) {
            val squaredMagnitude =
                real[bin] *
                        real[bin] +
                        imaginary[bin] *
                        imaginary[bin]

            var power =
                squaredMagnitude /
                        normalization

            /*
             * Positive-frequency power is doubled except at
             * the Nyquist frequency.
             */
            if (bin != halfSize) {
                power *= 2.0
            }

            combinedPowerSpectrum[bin] +=
                power
        }
    }

    private fun timestampsAreStrictlyIncreasing(
        timestampsNs: List<Long>
    ): Boolean {
        for (
        index in 1 until
                timestampsNs.size
        ) {
            if (
                timestampsNs[index] <=
                timestampsNs[index - 1]
            ) {
                return false
            }
        }

        return true
    }

    private fun containsExcessiveTimestampGap(
        timestampsNs: List<Long>
    ): Boolean {
        val maximumAllowedGapNs =
            (
                    MAX_ALLOWED_SAMPLE_GAP_SECONDS *
                            1_000_000_000.0
                    ).toLong()

        for (
        index in 1 until
                timestampsNs.size
        ) {
            val gapNs =
                timestampsNs[index] -
                        timestampsNs[index - 1]

            if (gapNs > maximumAllowedGapNs) {
                return true
            }
        }

        return false
    }

    private fun calculateSourceSampleRate(
        sampleCount: Int,
        durationSeconds: Double
    ): Double {
        if (
            sampleCount < 2 ||
            durationSeconds <= 0.0
        ) {
            return 0.0
        }

        return (
                sampleCount -
                        1
                ) / durationSeconds
    }

    private fun emptyResult(
        sampleCount: Int = 0,
        sampleRateHz: Double = 0.0,
        sourceSampleRateHz: Double = 0.0,
        durationSeconds: Double = 0.0,
        frequencyResolutionHz: Double = 0.0
    ): TremorSpectrumResult {
        return TremorSpectrumResult(
            peakFrequencyHz = 0.0,
            peakPower = 0.0,

            concentrationRatio = 0.0,
            bandAveragePower = 0.0,
            narrowbandRatio = 0.0,

            peakNeighborhoodPower = 0.0,
            rhythmicEnergyShare = 0.0,
            totalAnalyzedPower = 0.0,

            sampleCount = sampleCount,
            sampleRateHz = sampleRateHz,
            sourceSampleRateHz = sourceSampleRateHz,

            durationSeconds = durationSeconds,

            frequencyResolutionHz =
                frequencyResolutionHz
        )
    }

    private fun nextPowerOfTwo(
        value: Int
    ): Int {
        var power = 1

        while (power < value) {
            power =
                power shl 1
        }

        return power
    }

    /*
     * In-place iterative radix-2 Cooley-Tukey FFT.
     */
    private fun fft(
        real: DoubleArray,
        imaginary: DoubleArray
    ) {
        val size =
            real.size

        /*
         * Bit-reversal permutation.
         */
        var reversedIndex = 0

        for (
        currentIndex in 1 until size
        ) {
            var bit =
                size shr 1

            while (
                reversedIndex and bit != 0
            ) {
                reversedIndex =
                    reversedIndex xor bit

                bit =
                    bit shr 1
            }

            reversedIndex =
                reversedIndex or bit

            if (
                currentIndex <
                reversedIndex
            ) {
                val temporaryReal =
                    real[currentIndex]

                real[currentIndex] =
                    real[reversedIndex]

                real[reversedIndex] =
                    temporaryReal

                val temporaryImaginary =
                    imaginary[currentIndex]

                imaginary[currentIndex] =
                    imaginary[reversedIndex]

                imaginary[reversedIndex] =
                    temporaryImaginary
            }
        }

        var sectionLength = 2

        while (
            sectionLength <= size
        ) {
            val angle =
                -2.0 *
                        PI /
                        sectionLength

            val rotationReal =
                cos(angle)

            val rotationImaginary =
                sin(angle)

            var sectionStart = 0

            while (
                sectionStart < size
            ) {
                var currentRotationReal =
                    1.0

                var currentRotationImaginary =
                    0.0

                for (
                offset in
                0 until
                        sectionLength / 2
                ) {
                    val evenIndex =
                        sectionStart +
                                offset

                    val oddIndex =
                        evenIndex +
                                sectionLength / 2

                    val evenReal =
                        real[evenIndex]

                    val evenImaginary =
                        imaginary[evenIndex]

                    val rotatedOddReal =
                        real[oddIndex] *
                                currentRotationReal -
                                imaginary[oddIndex] *
                                currentRotationImaginary

                    val rotatedOddImaginary =
                        real[oddIndex] *
                                currentRotationImaginary +
                                imaginary[oddIndex] *
                                currentRotationReal

                    real[evenIndex] =
                        evenReal +
                                rotatedOddReal

                    imaginary[evenIndex] =
                        evenImaginary +
                                rotatedOddImaginary

                    real[oddIndex] =
                        evenReal -
                                rotatedOddReal

                    imaginary[oddIndex] =
                        evenImaginary -
                                rotatedOddImaginary

                    val nextRotationReal =
                        currentRotationReal *
                                rotationReal -
                                currentRotationImaginary *
                                rotationImaginary

                    val nextRotationImaginary =
                        currentRotationReal *
                                rotationImaginary +
                                currentRotationImaginary *
                                rotationReal

                    currentRotationReal =
                        nextRotationReal

                    currentRotationImaginary =
                        nextRotationImaginary
                }

                sectionStart +=
                    sectionLength
            }

            sectionLength =
                sectionLength shl 1
        }
    }
}


/*
 * Internal uniformly sampled axis data.
 */
private data class ResampledAxes(
    val xValues: List<Float> = emptyList(),
    val yValues: List<Float> = emptyList(),
    val zValues: List<Float> = emptyList(),

    val sampleCount: Int = 0,
    val durationSeconds: Double = 0.0
)