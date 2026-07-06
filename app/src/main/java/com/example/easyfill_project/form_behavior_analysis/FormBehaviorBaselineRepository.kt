package com.example.easyfill_project.form_behavior_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Repository responsible ONLY for communicating with Firebase.
// It saves and loads the user's personalized form behavior baseline.

class FormBehaviorBaselineRepository {

    // Firebase Authentication instance.
    // Used to know which user is currently logged in.
    private val auth = FirebaseAuth.getInstance()

    // Firestore database instance.
    // Used to read and write data.
    private val db = FirebaseFirestore.getInstance()

    // Save the calculated baseline to Firestore
    fun saveBaseline(
        baseline: FormBehaviorBaseline,

        // Optional callback if save succeeds.
        onSuccess: () -> Unit = {},

        // Optional callback if save fails.
        onFailure: (Exception) -> Unit = {}
    ) {

        // Get the current user's ID.
        // If nobody is logged in, simply stop.
        val userId = auth.currentUser?.uid ?: return

        // Convert the baseline object into a Firestore document.
        // Firestore stores key-value pairs (Map<String, Any>).
        val data = hashMapOf(

            // Number of samples used to build the baseline.
            "sampleCount" to baseline.sampleCount,

            // Time when the baseline was calculated.
            "calculatedAtMs" to baseline.calculatedAtMs,

            // Dwell Time statistics
            "avgDwellTimeMs" to baseline.avgDwellTimeMs,
            "stdDwellTimeMs" to baseline.stdDwellTimeMs,

            // Thinking Time statistics
            "avgThinkingTimeMs" to baseline.avgThinkingTimeMs,
            "stdThinkingTimeMs" to baseline.stdThinkingTimeMs,

            // Typing Speed statistics
            "avgTypingMsPerInsertedChar" to baseline.avgTypingMsPerInsertedChar,
            "stdTypingMsPerInsertedChar" to baseline.stdTypingMsPerInsertedChar,

            // Review Time statistics
            "avgReviewTimeMs" to baseline.avgReviewTimeMs,
            "stdReviewTimeMs" to baseline.stdReviewTimeMs,

            // Idle Time statistics
            "avgMaxIdleTimeMs" to baseline.avgMaxIdleTimeMs,
            "stdMaxIdleTimeMs" to baseline.stdMaxIdleTimeMs,

            // Idle Events statistics
            "avgIdleEvents" to baseline.avgIdleEvents,
            "stdIdleEvents" to baseline.stdIdleEvents,

            // Delete Ratio statistics
            "avgDeleteRatio" to baseline.avgDeleteRatio,
            "stdDeleteRatio" to baseline.stdDeleteRatio,

            // Long Pause statistics
            "avgLongPauses" to baseline.avgLongPauses,
            "stdLongPauses" to baseline.stdLongPauses,

            // Actual timestamp when this document was saved.
            // Useful for debugging and checking if an older baseline
            // was overwritten by a newer one.
            "createdAt" to System.currentTimeMillis()
        )

        // Save everything to:
        // users/{userId}/formBehaviorParameters/baseline
        db.collection("users")
            .document(userId)
            .collection("formBehaviorParameters")
            .document("baseline")
            .set(data)

            // Called if saving succeeded.
            .addOnSuccessListener {

                Log.d("FORM_BASELINE", "Form behavior baseline saved")

                onSuccess()
            }

            // Called if saving failed.
            .addOnFailureListener { e ->

                Log.e(
                    "FORM_BASELINE",
                    "Failed to save form behavior baseline",
                    e
                )

                onFailure(e)
            }
    }

    // Load an existing baseline from Firestore
    fun getBaseline(

        // Returns the document as a Map if found.
        onSuccess: (Map<String, Any>?) -> Unit,

        // Optional failure callback.
        onFailure: (Exception) -> Unit = {}
    ) {

        // Get current logged-in user.
        val userId = auth.currentUser?.uid ?: return

        // Read:
        // users/{userId}/formBehaviorParameters/baseline
        db.collection("users")
            .document(userId)
            .collection("formBehaviorParameters")
            .document("baseline")
            .get()

            // If the document exists,
            // return all its data.
            .addOnSuccessListener { document ->

                onSuccess(document.data)
            }

            // If reading fails,
            // report the error.
            .addOnFailureListener { e ->

                Log.e(
                    "FORM_BASELINE",
                    "Failed to load form behavior baseline",
                    e
                )

                onFailure(e)
            }
    }
}