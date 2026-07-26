//package com.example.easyfill_project.form_behavior_analysis
//
//import android.util.Log
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//
//// Repository responsible ONLY for communicating with Firebase.
//// It saves and loads the user's personalized form behavior baseline.
//
//class FormBehaviorBaselineRepository {
//
//    // Firebase Authentication instance.
//    // Used to know which user is currently logged in.
//    private val auth = FirebaseAuth.getInstance()
//
//    // Firestore database instance.
//    // Used to read and write data.
//    private val db = FirebaseFirestore.getInstance()
//
//    // Save the calculated baseline to Firestore
//    fun saveBaseline(
//        baseline: FormBehaviorBaseline,
//
//        // Optional callback if save succeeds.
//        onSuccess: () -> Unit = {},
//
//        // Optional callback if save fails.
//        onFailure: (Exception) -> Unit = {}
//    ) {
//
//        // Get the current user's ID.
//        // If nobody is logged in, simply stop.
//        val userId = auth.currentUser?.uid ?: return
//
//        // Convert the baseline object into a Firestore document.
//        // Firestore stores key-value pairs (Map<String, Any>).
//        val data = hashMapOf(
//
//            // Number of samples used to build the baseline.
//            "sampleCount" to baseline.sampleCount,
//
//            // Time when the baseline was calculated.
//            "calculatedAtMs" to baseline.calculatedAtMs,
//
//            // Dwell Time statistics
//            "avgDwellTimeMs" to baseline.avgDwellTimeMs,
//            "stdDwellTimeMs" to baseline.stdDwellTimeMs,
//
//            // Thinking Time statistics
//            "avgThinkingTimeMs" to baseline.avgThinkingTimeMs,
//            "stdThinkingTimeMs" to baseline.stdThinkingTimeMs,
//
//            // Typing Speed statistics
//            "avgTypingMsPerInsertedChar" to baseline.avgTypingMsPerInsertedChar,
//            "stdTypingMsPerInsertedChar" to baseline.stdTypingMsPerInsertedChar,
//
//            // Review Time statistics
//            "avgReviewTimeMs" to baseline.avgReviewTimeMs,
//            "stdReviewTimeMs" to baseline.stdReviewTimeMs,
//
//            // Idle Time statistics
//            "avgMaxIdleTimeMs" to baseline.avgMaxIdleTimeMs,
//            "stdMaxIdleTimeMs" to baseline.stdMaxIdleTimeMs,
//
//            // Idle Events statistics
//            "avgIdleEvents" to baseline.avgIdleEvents,
//            "stdIdleEvents" to baseline.stdIdleEvents,
//
//            // Delete Ratio statistics
//            "avgDeleteRatio" to baseline.avgDeleteRatio,
//            "stdDeleteRatio" to baseline.stdDeleteRatio,
//
//            // Long Pause statistics
//            "avgLongPauses" to baseline.avgLongPauses,
//            "stdLongPauses" to baseline.stdLongPauses,
//
//            // Actual timestamp when this document was saved.
//            // Useful for debugging and checking if an older baseline
//            // was overwritten by a newer one.
//            "createdAt" to System.currentTimeMillis()
//        )
//
//        // Save everything to:
//        // users/{userId}/formBehaviorParameters/baseline
//        db.collection("users")
//            .document(userId)
//            .collection("formBehaviorParameters")
//            .document("baseline")
//            .set(data)
//
//            // Called if saving succeeded.
//            .addOnSuccessListener {
//
//                Log.d("FORM_BASELINE", "Form behavior baseline saved")
//
//                onSuccess()
//            }
//
//            // Called if saving failed.
//            .addOnFailureListener { e ->
//
//                Log.e(
//                    "FORM_BASELINE",
//                    "Failed to save form behavior baseline",
//                    e
//                )
//
//                onFailure(e)
//            }
//    }
//
//    // Load an existing baseline from Firestore
//    fun getBaseline(
//
//        // Returns the document as a Map if found.
//        onSuccess: (Map<String, Any>?) -> Unit,
//
//        // Optional failure callback.
//        onFailure: (Exception) -> Unit = {}
//    ) {
//
//        // Get current logged-in user.
//        val userId = auth.currentUser?.uid ?: return
//
//        // Read:
//        // users/{userId}/formBehaviorParameters/baseline
//        db.collection("users")
//            .document(userId)
//            .collection("formBehaviorParameters")
//            .document("baseline")
//            .get()
//
//            // If the document exists,
//            // return all its data.
//            .addOnSuccessListener { document ->
//
//                onSuccess(document.data)
//            }
//
//            // If reading fails,
//            // report the error.
//            .addOnFailureListener { e ->
//
//                Log.e(
//                    "FORM_BASELINE",
//                    "Failed to load form behavior baseline",
//                    e
//                )
//
//                onFailure(e)
//            }
//    }
//}





package com.example.easyfill_project.form_behavior_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/*
 * Saves and loads the user's fixed-size accumulated
 * form-behavior baseline.
 *
 * Firestore structure:
 *
 * users/{userId}/formBehaviorParameters/baseline
 *
 * Only one document is stored. Individual field samples are
 * not saved in Firestore.
 */
class FormBehaviorBaselineRepository {

    private val auth =
        FirebaseAuth.getInstance()

    private val db =
        FirebaseFirestore.getInstance()

    companion object {
        private const val TAG =
            "FORM_BASELINE"

        private const val USERS_COLLECTION =
            "users"

        private const val PARAMETERS_COLLECTION =
            "formBehaviorParameters"

        private const val BASELINE_DOCUMENT =
            "baseline"
    }

    /*
     * Saves either the initial baseline or an updated
     * accumulated baseline.
     *
     * set() writes to the same baseline document every time.
     * It does not create a document for each form session or
     * for each FieldBehaviorSample.
     */
    fun saveBaseline(
        baseline: FormBehaviorBaseline,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            auth.currentUser?.uid

        if (userId == null) {
            onFailure(
                IllegalStateException(
                    "Cannot save form behavior baseline: no authenticated user"
                )
            )
            return
        }

        if (!isBaselineValid(baseline)) {
            onFailure(
                IllegalArgumentException(
                    "Cannot save an invalid form behavior baseline"
                )
            )
            return
        }

        val data =
            hashMapOf<String, Any>(

                /*
                 * Accumulated metadata.
                 */
                "sampleCount" to
                        baseline.sampleCount,

                "validSessionCount" to
                        baseline.validSessionCount,

                "calculatedAtMs" to
                        baseline.calculatedAtMs,


                /*
                 * Dwell-time statistics.
                 */
                "avgDwellTimeMs" to
                        baseline.avgDwellTimeMs,

                "stdDwellTimeMs" to
                        baseline.stdDwellTimeMs,

                "dwellTimeM2" to
                        baseline.dwellTimeM2,


                /*
                 * Thinking-time statistics.
                 */
                "avgThinkingTimeMs" to
                        baseline.avgThinkingTimeMs,

                "stdThinkingTimeMs" to
                        baseline.stdThinkingTimeMs,

                "thinkingTimeM2" to
                        baseline.thinkingTimeM2,


                /*
                 * Typing-speed statistics.
                 */
                "avgTypingMsPerInsertedChar" to
                        baseline.avgTypingMsPerInsertedChar,

                "stdTypingMsPerInsertedChar" to
                        baseline.stdTypingMsPerInsertedChar,

                "typingMsPerInsertedCharM2" to
                        baseline.typingMsPerInsertedCharM2,


                /*
                 * Review-time statistics.
                 */
                "avgReviewTimeMs" to
                        baseline.avgReviewTimeMs,

                "stdReviewTimeMs" to
                        baseline.stdReviewTimeMs,

                "reviewTimeM2" to
                        baseline.reviewTimeM2,


                /*
                 * Maximum-idle-time statistics.
                 */
                "avgMaxIdleTimeMs" to
                        baseline.avgMaxIdleTimeMs,

                "stdMaxIdleTimeMs" to
                        baseline.stdMaxIdleTimeMs,

                "maxIdleTimeM2" to
                        baseline.maxIdleTimeM2,


                /*
                 * Idle-event statistics.
                 */
                "avgIdleEvents" to
                        baseline.avgIdleEvents,

                "stdIdleEvents" to
                        baseline.stdIdleEvents,

                "idleEventsM2" to
                        baseline.idleEventsM2,


                /*
                 * Delete-ratio statistics.
                 */
                "avgDeleteRatio" to
                        baseline.avgDeleteRatio,

                "stdDeleteRatio" to
                        baseline.stdDeleteRatio,

                "deleteRatioM2" to
                        baseline.deleteRatioM2,


                /*
                 * Long-pause statistics.
                 */
                "avgLongPauses" to
                        baseline.avgLongPauses,

                "stdLongPauses" to
                        baseline.stdLongPauses,

                "longPausesM2" to
                        baseline.longPausesM2,


                /*
                 * Server-side save time.
                 */
                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        getBaselineDocument(
            userId = userId
        )
            .set(data)
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    """
                    Form behavior baseline saved.
                    sessions=${baseline.validSessionCount}
                    samples=${baseline.sampleCount}
                    """.trimIndent()
                )

                onSuccess()
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Failed to save form behavior baseline",
                    error
                )

                onFailure(error)
            }
    }

    /*
     * Loads the existing fixed-size baseline document.
     *
     * null means no saved baseline currently exists.
     */
    fun getBaseline(
        onSuccess: (Map<String, Any>?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            auth.currentUser?.uid

        if (userId == null) {
            onFailure(
                IllegalStateException(
                    "Cannot load form behavior baseline: no authenticated user"
                )
            )
            return
        }

        getBaselineDocument(
            userId = userId
        )
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    Log.d(
                        TAG,
                        "No saved form behavior baseline exists"
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                val data =
                    document.data

                if (data == null) {
                    Log.d(
                        TAG,
                        "Form behavior baseline document is empty"
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                Log.d(
                    TAG,
                    """
                    Form behavior baseline loaded.
                    sessions=${data["validSessionCount"]}
                    samples=${data["sampleCount"]}
                    """.trimIndent()
                )

                onSuccess(data)
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Failed to load form behavior baseline",
                    error
                )

                onFailure(error)
            }
    }

    private fun getBaselineDocument(
        userId: String
    ) =
        db.collection(
            USERS_COLLECTION
        )
            .document(
                userId
            )
            .collection(
                PARAMETERS_COLLECTION
            )
            .document(
                BASELINE_DOCUMENT
            )

    /*
     * Prevents an invalid calculated value from replacing a
     * valid accumulated baseline.
     */
    private fun isBaselineValid(
        baseline: FormBehaviorBaseline
    ): Boolean {

        return baseline.sampleCount > 0 &&
                baseline.validSessionCount > 0 &&
                baseline.calculatedAtMs > 0L &&

                baseline.avgDwellTimeMs.isValidNonNegative() &&
                baseline.stdDwellTimeMs.isValidNonNegative() &&
                baseline.dwellTimeM2.isValidNonNegative() &&

                baseline.avgThinkingTimeMs.isValidNonNegative() &&
                baseline.stdThinkingTimeMs.isValidNonNegative() &&
                baseline.thinkingTimeM2.isValidNonNegative() &&

                baseline.avgTypingMsPerInsertedChar.isValidNonNegative() &&
                baseline.stdTypingMsPerInsertedChar.isValidNonNegative() &&
                baseline.typingMsPerInsertedCharM2.isValidNonNegative() &&

                baseline.avgReviewTimeMs.isValidNonNegative() &&
                baseline.stdReviewTimeMs.isValidNonNegative() &&
                baseline.reviewTimeM2.isValidNonNegative() &&

                baseline.avgMaxIdleTimeMs.isValidNonNegative() &&
                baseline.stdMaxIdleTimeMs.isValidNonNegative() &&
                baseline.maxIdleTimeM2.isValidNonNegative() &&

                baseline.avgIdleEvents.isValidNonNegative() &&
                baseline.stdIdleEvents.isValidNonNegative() &&
                baseline.idleEventsM2.isValidNonNegative() &&

                baseline.avgDeleteRatio.isFinite() &&
                baseline.avgDeleteRatio in 0.0..1.0 &&
                baseline.stdDeleteRatio.isValidNonNegative() &&
                baseline.deleteRatioM2.isValidNonNegative() &&

                baseline.avgLongPauses.isValidNonNegative() &&
                baseline.stdLongPauses.isValidNonNegative() &&
                baseline.longPausesM2.isValidNonNegative()
    }

    private fun Double.isValidNonNegative():
            Boolean {

        return isFinite() &&
                this >= 0.0
    }
}