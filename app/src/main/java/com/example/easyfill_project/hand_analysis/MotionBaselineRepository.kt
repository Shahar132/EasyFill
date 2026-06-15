package com.example.easyfill_project.hand_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

//class to take the result and upload it to firestore.
class MotionBaselineRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun saveBaseline(
        result: MotionAnalysisResult,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId = auth.currentUser?.uid ?: return

        val data = hashMapOf(
            "averageAcceleration" to result.averageAcceleration,
            "accelerationVariation" to result.accelerationVariation,
            "averageGyroscope" to result.averageGyroscope,
            "gyroscopeVariation" to result.gyroscopeVariation,
            "shakeCount" to result.shakeCount,
            "durationSeconds" to result.durationSeconds,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("motionParameters")
            .document("baseline")
            .set(data)
            .addOnSuccessListener {
                Log.d("MOTION_BASELINE", "Baseline saved")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("MOTION_BASELINE", "Failed to save baseline", e)
                onFailure(e)
            }
    }
}