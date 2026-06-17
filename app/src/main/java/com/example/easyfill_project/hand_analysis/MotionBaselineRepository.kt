package com.example.easyfill_project.hand_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// This class saves and loads the user's personalized motion baseline from Firestore.
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
            "maxAcceleration" to result.maxAcceleration,
            "accelerationVariation" to result.accelerationVariation,

            "averageGyroscope" to result.averageGyroscope,
            "maxGyroscope" to result.maxGyroscope,
            "gyroscopeVariation" to result.gyroscopeVariation,

            // Added: personalized 95th percentile thresholds
            "accelerationP95" to result.accelerationP95,
            "gyroscopeP95" to result.gyroscopeP95,

            "durationSeconds" to result.durationSeconds,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("motionParameters")
            .document("baseline")
            .set(data)
            .addOnSuccessListener {
                Log.d("MOTION_BASELINE", "Baseline saved with P95 values")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("MOTION_BASELINE", "Failed to save baseline", e)
                onFailure(e)
            }
    }

    // Added: load baseline later so current 5-second samples can compare to it.
    fun getBaseline(
        onSuccess: (Map<String, Any>?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("motionParameters")
            .document("baseline")
            .get()
            .addOnSuccessListener { document ->
                onSuccess(document.data)
            }
            .addOnFailureListener { e ->
                Log.e("MOTION_BASELINE", "Failed to load baseline", e)
                onFailure(e)
            }
    }
}