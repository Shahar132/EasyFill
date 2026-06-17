package com.example.easyfill_project.hand_analysis

// Stores the final motion values after analyzing accelerometer + gyroscope data.
data class MotionAnalysisResult(
    val durationSeconds: Double,

    val averageAcceleration: Float,
    val maxAcceleration: Float,
    val accelerationVariation: Float,

    val averageGyroscope: Float,
    val maxGyroscope: Float,
    val gyroscopeVariation: Float,

    // Changed: no fixed threshold like 15 anymore.
    // This can stay for compatibility, but we will not rely on it.
    val shakeCount: Int,

    val isReliable: Boolean,

    // Added: personalized 95th percentile values from the sample.
    val accelerationP95: Float,
    val gyroscopeP95: Float,

    // Added: used during current 5-second windows.
    val accelerationExceedCount: Int = 0,

    // Added: raw acceleration magnitudes.
    // Needed so we can compare current samples to baseline accelerationP95.
    val accelerationValues: List<Float> = emptyList(),
    val gyroscopeValues: List<Float> = emptyList()

)