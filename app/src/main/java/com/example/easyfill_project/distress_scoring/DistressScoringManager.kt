package com.example.easyfill_project.distress_scoring

import android.util.Log

class DistressScoringManager {

    private var handScore = 0//form the controller can be between 0 to 4
    private var voiceScore = 0
    private var faceScore = 0

    fun updateHandScore(score: Int) {
        handScore = score
    }

    fun updateVoiceScore(score: Int) {
        voiceScore = score
    }

    fun updateFaceScore(score: Int) {
        faceScore = score
    }

    fun getTotalScore(): Int {
        return handScore + voiceScore + faceScore
    }

    fun isDistressDetected(): Boolean {
        return getTotalScore() >= 2
    }

    fun printStatus() {

        Log.d(
            "DISTRESS_SCORE",
            """
            Hand score = $handScore
            Voice score = $voiceScore
            Face score = $faceScore
            
            Total score = ${getTotalScore()}
            Distress = ${isDistressDetected()}
            """.trimIndent()
        )

    }
}