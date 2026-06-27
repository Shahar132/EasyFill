package com.example.easyfill_project.voiceanalysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
//this class update firestore with the user voice analysis from baseline screen
class VoiceBaselineRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun saveBaseline(
        analysis: SpeechAnalysisResult,
        validSpeechSeconds: Int,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onFailure(Exception("User not logged in"))
            return
        }

        val words = analysis.finalText.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        val validDuration = validSpeechSeconds.toDouble()

        val validSpeechRate =
            if (validDuration > 0) words.size / validDuration else 0.0

        val baselineData = hashMapOf(
            "validSpeechSeconds" to validDuration,
            "analysisDurationSeconds" to analysis.durationSeconds,

            "speechRateWordsPerSecond" to validSpeechRate,
            "analysisSpeechRateWordsPerSecond" to analysis.speechRateWordsPerSecond,

            "averageRms" to analysis.averageRms,
            "maxRms" to analysis.maxRms,
            "rmsVariation" to analysis.rmsVariation,
            "pauseCount" to analysis.pauseCount,
            "pauseDurationsMs" to analysis.pauseDurationsMs,
            "averagePauseMs" to analysis.averagePauseMs,
            "hesitationCount" to analysis.hesitationCount,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("voiceParameters")
            .document("baseline")
            .set(baselineData)
            .addOnSuccessListener {
                Log.d("BASELINE_FIRESTORE", "Baseline saved")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("BASELINE_FIRESTORE", "Failed saving baseline", e)
                onFailure(e)
            }
    }

//function to check if firestore contain the voice baseline parameters - if yes skip baseline voice screen
    fun hasBaseline(
        onResult: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onResult(false)
            return
        }

        db.collection("users")
            .document(userId)
            .collection("voiceParameters")
            .document("baseline")
            .get()
            .addOnSuccessListener { document ->
                onResult(document.exists())
            }
            .addOnFailureListener { e ->
                onFailure(e)
                onResult(false)
            }
    }
}