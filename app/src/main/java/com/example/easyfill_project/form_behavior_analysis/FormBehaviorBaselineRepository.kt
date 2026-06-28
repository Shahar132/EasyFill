package com.example.easyfill_project.form_behavior_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// This class saves and loads the user's personalized form behavior baseline from Firestore.
class FormBehaviorBaselineRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun saveBaseline(
        baseline: FormBehaviorBaseline,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId = auth.currentUser?.uid ?: return

        val data = hashMapOf(
            "sampleCount" to baseline.sampleCount,
            "calculatedAtMs" to baseline.calculatedAtMs,

            "avgDwellTimeMs" to baseline.avgDwellTimeMs,
            "stdDwellTimeMs" to baseline.stdDwellTimeMs,

            "avgThinkingTimeMs" to baseline.avgThinkingTimeMs,
            "stdThinkingTimeMs" to baseline.stdThinkingTimeMs,

            "avgTypingMsPerInsertedChar" to baseline.avgTypingMsPerInsertedChar,
            "stdTypingMsPerInsertedChar" to baseline.stdTypingMsPerInsertedChar,

            "avgReviewTimeMs" to baseline.avgReviewTimeMs,
            "stdReviewTimeMs" to baseline.stdReviewTimeMs,

            "avgMaxIdleTimeMs" to baseline.avgMaxIdleTimeMs,
            "stdMaxIdleTimeMs" to baseline.stdMaxIdleTimeMs,

            "avgIdleEvents" to baseline.avgIdleEvents,
            "stdIdleEvents" to baseline.stdIdleEvents,

            "avgDeleteRatio" to baseline.avgDeleteRatio,
            "stdDeleteRatio" to baseline.stdDeleteRatio,

            "avgLongPauses" to baseline.avgLongPauses,
            "stdLongPauses" to baseline.stdLongPauses,

            // Real timestamp for Firebase/debugging.
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("formBehaviorParameters")
            .document("baseline")
            .set(data)
            .addOnSuccessListener {
                Log.d("FORM_BASELINE", "Form behavior baseline saved")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("FORM_BASELINE", "Failed to save form behavior baseline", e)
                onFailure(e)
            }
    }

    fun getBaseline(
        onSuccess: (Map<String, Any>?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("formBehaviorParameters")
            .document("baseline")
            .get()
            .addOnSuccessListener { document ->
                onSuccess(document.data)
            }
            .addOnFailureListener { e ->
                Log.e("FORM_BASELINE", "Failed to load form behavior baseline", e)
                onFailure(e)
            }
    }
}