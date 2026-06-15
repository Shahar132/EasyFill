package com.example.easyfill_project.hand_analysis

//Stores the final motion values: average movement, max movement, shake count, duration, reliability.
data class MotionAnalysisResult(
    val durationSeconds: Double,
    val averageAcceleration: Float,
    val maxAcceleration: Float,
    val accelerationVariation: Float,
    val averageGyroscope: Float,

    val maxGyroscope: Float,
    val gyroscopeVariation: Float,
    val shakeCount: Int,
    val isReliable: Boolean
)