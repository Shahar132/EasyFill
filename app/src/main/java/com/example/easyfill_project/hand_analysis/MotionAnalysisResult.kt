package com.example.easyfill_project.hand_analysis

/*
 * Contains the filtered sensor information collected during
 * one measurement window.
 *
 * This model represents sensor data only. It does not contain
 * a distress score or a baseline decision.
 */
data class MotionAnalysisResult(
    val durationSeconds: Double,

    /*
     * Acceleration magnitude statistics.
     */
    val averageAcceleration: Float,
    val maxAcceleration: Float,
    val accelerationVariation: Float,
    val accelerationP95: Float,

    /*
     * Gyroscope magnitude statistics.
     */
    val averageGyroscope: Float,
    val maxGyroscope: Float,
    val gyroscopeVariation: Float,
    val gyroscopeP95: Float,

    /*
     * Indicates whether enough valid sensor information was
     * collected for the requested measurement duration.
     */
    val isReliable: Boolean,

    /*
     * Filtered vector magnitudes.
     *
     * These values describe overall movement intensity.
     */
    val accelerationValues: List<Float> = emptyList(),
    val gyroscopeValues: List<Float> = emptyList(),

    /*
     * Filtered signed acceleration axes.
     *
     * Signed values preserve the repeated direction changes
     * required for tremor-frequency analysis.
     */
    val accelerationXValues: List<Float> = emptyList(),
    val accelerationYValues: List<Float> = emptyList(),
    val accelerationZValues: List<Float> = emptyList(),

    /*
     * Original sensor timestamps aligned with the acceleration
     * and gyroscope sample lists.
     *
     * They allow internal windows to be created according to
     * real time instead of assuming perfectly uniform sampling.
     */
    val accelerationTimestampsNs: List<Long> = emptyList(),
    val gyroscopeTimestampsNs: List<Long> = emptyList()
)


/*
 * Stores the calculated features of one valid two-second
 * baseline window.
 *
 * These summaries exist only temporarily in application
 * memory while a ten-second baseline session is evaluated.
 *
 * They are not stored as separate Firestore documents.
 */
data class MotionBaselineWindowSummary(
    val durationSeconds: Double,

    /*
     * Movement-intensity features.
     */
    val accelerationP95: Double,
    val gyroscopeP95: Double,

    val accelerationVariation: Double,
    val gyroscopeVariation: Double,

    /*
     * Spectral features.
     */
    val peakFrequencyHz: Double,
    val bandAveragePower: Double,
    val peakNeighborhoodPower: Double,

    val concentrationRatio: Double,
    val narrowbandRatio: Double,
    val rhythmicEnergyShare: Double
)


/*
 * Represents the user's accumulated personal motion baseline.
 *
 * The profile has a fixed size. It does not contain historical
 * window lists.
 *
 * For every learned feature, the profile stores:
 *
 * mean:
 * The accumulated average across every accepted two-second
 * baseline window.
 *
 * m2:
 * The accumulated sum of squared differences from the mean.
 * Together with totalWindowCount, it allows the application
 * to calculate variance and standard deviation and to merge
 * future baseline sessions without storing old windows.
 */
data class MotionBaselineProfile(

    /*
     * Acceleration P95 distribution.
     */
    val accelerationP95Mean: Double,
    val accelerationP95M2: Double,

    /*
     * Gyroscope P95 distribution.
     */
    val gyroscopeP95Mean: Double,
    val gyroscopeP95M2: Double,

    /*
     * Acceleration-variation distribution.
     */
    val accelerationVariationMean: Double,
    val accelerationVariationM2: Double,

    /*
     * Gyroscope-variation distribution.
     */
    val gyroscopeVariationMean: Double,
    val gyroscopeVariationM2: Double,

    /*
     * Tremor-band power measured during natural phone holding.
     */
    val bandAveragePowerMean: Double,
    val bandAveragePowerM2: Double,

    /*
     * Power around the dominant spectral peak during natural
     * phone holding.
     */
    val peakNeighborhoodPowerMean: Double,
    val peakNeighborhoodPowerM2: Double,

    /*
     * Rhythmic-energy share measured during natural phone
     * holding.
     */
    val rhythmicEnergyShareMean: Double,
    val rhythmicEnergyShareM2: Double,

    /*
     * Accumulated baseline metadata.
     */
    val totalBaselineSeconds: Double,
    val validSessionCount: Int,
    val totalWindowCount: Int
)


/*
 * Complete baseline information loaded from Firestore.
 *
 * Only the fixed-size accumulated profile is persisted.
 * Historical two-second windows are not stored.
 */
data class MotionBaselineData(
    val profile: MotionBaselineProfile
)