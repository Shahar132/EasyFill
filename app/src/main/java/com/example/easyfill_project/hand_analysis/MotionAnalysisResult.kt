//package com.example.easyfill_project.hand_analysis
//
//// Stores the final motion values after analyzing accelerometer + gyroscope data.
//data class MotionAnalysisResult(
//    val durationSeconds: Double,
//
//    val averageAcceleration: Float,
//    val maxAcceleration: Float,
//    val accelerationVariation: Float,
//
//    val averageGyroscope: Float,
//    val maxGyroscope: Float,
//    val gyroscopeVariation: Float,
//
//    // Changed: no fixed threshold like 15 anymore.
//    // This can stay for compatibility, but we will not rely on it.
//    val shakeCount: Int,
//
//    val isReliable: Boolean,
//
//    // Added: personalized 95th percentile values from the sample.
//    val accelerationP95: Float,
//    val gyroscopeP95: Float,
//
//    // Added: used during current 5-second windows.
//    val accelerationExceedCount: Int = 0,
//
//    // Added: raw acceleration magnitudes.
//    // Needed so we can compare current samples to baseline accelerationP95.
//    val accelerationValues: List<Float> = emptyList(),
//    val gyroscopeValues: List<Float> = emptyList()
//
//)

///////////////////////////////

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
 * Raw sensor samples are not stored in Firebase. Only these
 * compact summary values are persisted.
 */
data class MotionBaselineWindowSummary(
    val durationSeconds: Double,

    /*
     * Movement intensity features.
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
 * Median describes the user's typical natural holding level.
 *
 * MAD describes the normal variation between baseline windows
 * and will later be used to calculate personal tremor severity.
 */
data class MotionBaselineProfile(

    /*
     * Personal acceleration P95 distribution.
     */
    val accelerationP95Median: Double,
    val accelerationP95Mad: Double,

    /*
     * Personal gyroscope P95 distribution.
     */
    val gyroscopeP95Median: Double,
    val gyroscopeP95Mad: Double,

    /*
     * Personal acceleration-variation distribution.
     */
    val accelerationVariationMedian: Double,
    val accelerationVariationMad: Double,

    /*
     * Personal gyroscope-variation distribution.
     */
    val gyroscopeVariationMedian: Double,
    val gyroscopeVariationMad: Double,

    /*
     * Personal tremor-band power distribution during natural
     * phone holding.
     */
    val bandAveragePowerMedian: Double,
    val bandAveragePowerMad: Double,

    /*
     * Personal narrow peak-power distribution during natural
     * phone holding.
     */
    val peakNeighborhoodPowerMedian: Double,
    val peakNeighborhoodPowerMad: Double,

    /*
     * Normal rhythmic-energy share during natural phone
     * holding.
     */
    val rhythmicEnergyShareMedian: Double,
    val rhythmicEnergyShareMad: Double,

    /*
     * Accumulated baseline metadata.
     */
    val totalBaselineSeconds: Double,
    val validSessionCount: Int,
    val totalWindowCount: Int
)


/*
 * Complete baseline information loaded from Firebase.
 *
 * profile:
 * The current robust personal statistics used for detection
 * and severity calculation.
 *
 * windows:
 * Every valid historical two-second baseline summary.
 * New valid sessions are appended without deleting old data.
 */
data class MotionBaselineData(
    val profile: MotionBaselineProfile,
    val windows: List<MotionBaselineWindowSummary>
)