//package com.example.easyfill_project.hand_analysis
//
//import android.util.Log
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//
//// This class saves and loads the user's personalized motion baseline from Firestore.
//class MotionBaselineRepository {
//
//    private val auth = FirebaseAuth.getInstance()
//    private val db = FirebaseFirestore.getInstance()
//
//    fun saveBaseline(
//        result: MotionAnalysisResult,
//        onSuccess: () -> Unit = {},
//        onFailure: (Exception) -> Unit = {}
//    ) {
//        val userId = auth.currentUser?.uid ?: return
//
//        val data = hashMapOf(
//            "averageAcceleration" to result.averageAcceleration,
//            "maxAcceleration" to result.maxAcceleration,
//            "accelerationVariation" to result.accelerationVariation,
//
//            "averageGyroscope" to result.averageGyroscope,
//            "maxGyroscope" to result.maxGyroscope,
//            "gyroscopeVariation" to result.gyroscopeVariation,
//
//            // Added: personalized 95th percentile thresholds
//            "accelerationP95" to result.accelerationP95,
//            "gyroscopeP95" to result.gyroscopeP95,
//
//            "durationSeconds" to result.durationSeconds,
//            "createdAt" to System.currentTimeMillis()
//        )
//
//        db.collection("users")
//            .document(userId)
//            .collection("motionParameters")
//            .document("baseline")
//            .set(data)
//            .addOnSuccessListener {
//                Log.d("MOTION_BASELINE", "Baseline saved with P95 values")
//                onSuccess()
//            }
//            .addOnFailureListener { e ->
//                Log.e("MOTION_BASELINE", "Failed to save baseline", e)
//                onFailure(e)
//            }
//    }
//
//    // Added: load baseline later so current 5-second samples can compare to it.
//    fun getBaseline(
//        onSuccess: (Map<String, Any>?) -> Unit,
//        onFailure: (Exception) -> Unit = {}
//    ) {
//        val userId = auth.currentUser?.uid ?: return
//
//        db.collection("users")
//            .document(userId)
//            .collection("motionParameters")
//            .document("baseline")
//            .get()
//            .addOnSuccessListener { document ->
//                onSuccess(document.data)
//            }
//            .addOnFailureListener { e ->
//                Log.e("MOTION_BASELINE", "Failed to load baseline", e)
//                onFailure(e)
//            }
//    }
//}

///////////////

package com.example.easyfill_project.hand_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/*
 * Saves and loads the user's fixed-size accumulated motion
 * baseline from Firestore.
 *
 * Firestore structure:
 *
 * users/{userId}/motionParameters/baseline
 *
 * Only one document is stored. Historical two-second windows
 * are not saved in Firestore.
 */
class MotionBaselineRepository {

    private val auth =
        FirebaseAuth.getInstance()

    private val db =
        FirebaseFirestore.getInstance()

    companion object {
        private const val TAG =
            "MOTION_BASELINE"

        private const val USERS_COLLECTION =
            "users"

        private const val PARAMETERS_COLLECTION =
            "motionParameters"

        private const val BASELINE_DOCUMENT =
            "baseline"
    }

    /*
     * Saves the first valid accumulated baseline.
     *
     * set() replaces the previous baseline document, so fields
     * from an older baseline structure are not retained.
     */
    fun saveInitialBaseline(
        baselineData: MotionBaselineData,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            getAuthenticatedUserId(
                onFailure = onFailure
            ) ?: return

        val profile =
            baselineData.profile

        if (!isProfileValid(profile)) {
            onFailure(
                IllegalArgumentException(
                    "Cannot save an invalid initial motion baseline"
                )
            )
            return
        }

        val baselineDocument =
            getBaselineDocument(
                userId = userId
            )

        baselineDocument
            .set(
                profileToMap(
                    profile = profile,
                    includeCreatedAt = true
                )
            )
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    """
                    Initial fixed-size motion baseline saved.
                    sessions=${profile.validSessionCount}
                    windows=${profile.totalWindowCount}
                    seconds=${profile.totalBaselineSeconds}
                    """.trimIndent()
                )

                onSuccess()
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Failed to save initial motion baseline",
                    error
                )

                onFailure(error)
            }
    }

    /*
     * Updates the existing accumulated baseline document.
     *
     * No new Firestore document is created and no historical
     * window summaries are stored.
     */
    fun updateBaseline(
        updatedProfile: MotionBaselineProfile,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            getAuthenticatedUserId(
                onFailure = onFailure
            ) ?: return

        if (!isProfileValid(updatedProfile)) {
            onFailure(
                IllegalArgumentException(
                    "Cannot save an invalid updated motion baseline"
                )
            )
            return
        }

        val baselineDocument =
            getBaselineDocument(
                userId = userId
            )

        /*
         * update() modifies the existing baseline document
         * while preserving its original createdAt value.
         */
        baselineDocument
            .update(
                profileToMap(
                    profile = updatedProfile,
                    includeCreatedAt = false
                )
            )
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    """
                    Existing motion baseline updated.
                    sessions=${updatedProfile.validSessionCount}
                    windows=${updatedProfile.totalWindowCount}
                    seconds=${updatedProfile.totalBaselineSeconds}
                    """.trimIndent()
                )

                onSuccess()
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Failed to update the existing motion baseline",
                    error
                )

                onFailure(error)
            }
    }

    /*
     * Loads the single accumulated baseline document.
     *
     * null means that no valid baseline currently exists.
     */
    fun getBaseline(
        onSuccess: (MotionBaselineData?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            getAuthenticatedUserId(
                onFailure = onFailure
            ) ?: return

        val baselineDocument =
            getBaselineDocument(
                userId = userId
            )

        baselineDocument
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    Log.d(
                        TAG,
                        "No saved motion baseline exists"
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                val data =
                    document.data

                if (data == null) {
                    Log.d(
                        TAG,
                        "Motion baseline document is empty"
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                val profile =
                    mapToProfile(
                        data = data
                    )

                if (profile == null) {
                    Log.d(
                        TAG,
                        """
                        Saved motion baseline is invalid or uses
                        an unsupported previous structure.
                        """.trimIndent()
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                Log.d(
                    TAG,
                    """
                    Fixed-size motion baseline loaded.
                    sessions=${profile.validSessionCount}
                    windows=${profile.totalWindowCount}
                    seconds=${profile.totalBaselineSeconds}
                    """.trimIndent()
                )

                onSuccess(
                    MotionBaselineData(
                        profile = profile
                    )
                )
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Failed to load motion baseline",
                    error
                )

                onFailure(error)
            }
    }

    /*
     * Returns the authenticated Firebase user ID.
     */
    private fun getAuthenticatedUserId(
        onFailure: (Exception) -> Unit
    ): String? {
        val userId =
            auth.currentUser?.uid

        if (userId == null) {
            onFailure(
                IllegalStateException(
                    "Cannot access motion baseline: no authenticated user"
                )
            )

            return null
        }

        return userId
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
     * Converts the fixed-size accumulated profile into
     * Firestore fields.
     */
    private fun profileToMap(
        profile: MotionBaselineProfile,
        includeCreatedAt: Boolean
    ): Map<String, Any> {

        val data =
            mutableMapOf<String, Any>(
                "accelerationP95Mean" to
                        profile.accelerationP95Mean,

                "accelerationP95M2" to
                        profile.accelerationP95M2,

                "gyroscopeP95Mean" to
                        profile.gyroscopeP95Mean,

                "gyroscopeP95M2" to
                        profile.gyroscopeP95M2,

                "accelerationVariationMean" to
                        profile.accelerationVariationMean,

                "accelerationVariationM2" to
                        profile.accelerationVariationM2,

                "gyroscopeVariationMean" to
                        profile.gyroscopeVariationMean,

                "gyroscopeVariationM2" to
                        profile.gyroscopeVariationM2,

                "bandAveragePowerMean" to
                        profile.bandAveragePowerMean,

                "bandAveragePowerM2" to
                        profile.bandAveragePowerM2,

                "peakNeighborhoodPowerMean" to
                        profile.peakNeighborhoodPowerMean,

                "peakNeighborhoodPowerM2" to
                        profile.peakNeighborhoodPowerM2,

                "rhythmicEnergyShareMean" to
                        profile.rhythmicEnergyShareMean,

                "rhythmicEnergyShareM2" to
                        profile.rhythmicEnergyShareM2,

                "totalBaselineSeconds" to
                        profile.totalBaselineSeconds,

                "validSessionCount" to
                        profile.validSessionCount,

                "totalWindowCount" to
                        profile.totalWindowCount,

                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        if (includeCreatedAt) {
            data["createdAt"] =
                FieldValue.serverTimestamp()
        }

        return data
    }

    /*
     * Converts Firestore fields into the accumulated baseline
     * profile used by the application.
     */
    private fun mapToProfile(
        data: Map<String, Any>
    ): MotionBaselineProfile? {

        val accelerationP95Mean =
            data.doubleValue(
                key = "accelerationP95Mean"
            ) ?: return null

        val accelerationP95M2 =
            data.doubleValue(
                key = "accelerationP95M2"
            ) ?: return null

        val gyroscopeP95Mean =
            data.doubleValue(
                key = "gyroscopeP95Mean"
            ) ?: return null

        val gyroscopeP95M2 =
            data.doubleValue(
                key = "gyroscopeP95M2"
            ) ?: return null

        val accelerationVariationMean =
            data.doubleValue(
                key = "accelerationVariationMean"
            ) ?: return null

        val accelerationVariationM2 =
            data.doubleValue(
                key = "accelerationVariationM2"
            ) ?: return null

        val gyroscopeVariationMean =
            data.doubleValue(
                key = "gyroscopeVariationMean"
            ) ?: return null

        val gyroscopeVariationM2 =
            data.doubleValue(
                key = "gyroscopeVariationM2"
            ) ?: return null

        val bandAveragePowerMean =
            data.doubleValue(
                key = "bandAveragePowerMean"
            ) ?: return null

        val bandAveragePowerM2 =
            data.doubleValue(
                key = "bandAveragePowerM2"
            ) ?: return null

        val peakNeighborhoodPowerMean =
            data.doubleValue(
                key = "peakNeighborhoodPowerMean"
            ) ?: return null

        val peakNeighborhoodPowerM2 =
            data.doubleValue(
                key = "peakNeighborhoodPowerM2"
            ) ?: return null

        val rhythmicEnergyShareMean =
            data.doubleValue(
                key = "rhythmicEnergyShareMean"
            ) ?: return null

        val rhythmicEnergyShareM2 =
            data.doubleValue(
                key = "rhythmicEnergyShareM2"
            ) ?: return null

        val totalBaselineSeconds =
            data.doubleValue(
                key = "totalBaselineSeconds"
            ) ?: return null

        val validSessionCount =
            data.intValue(
                key = "validSessionCount"
            ) ?: return null

        val totalWindowCount =
            data.intValue(
                key = "totalWindowCount"
            ) ?: return null

        val profile =
            MotionBaselineProfile(
                accelerationP95Mean =
                    accelerationP95Mean,

                accelerationP95M2 =
                    accelerationP95M2,

                gyroscopeP95Mean =
                    gyroscopeP95Mean,

                gyroscopeP95M2 =
                    gyroscopeP95M2,

                accelerationVariationMean =
                    accelerationVariationMean,

                accelerationVariationM2 =
                    accelerationVariationM2,

                gyroscopeVariationMean =
                    gyroscopeVariationMean,

                gyroscopeVariationM2 =
                    gyroscopeVariationM2,

                bandAveragePowerMean =
                    bandAveragePowerMean,

                bandAveragePowerM2 =
                    bandAveragePowerM2,

                peakNeighborhoodPowerMean =
                    peakNeighborhoodPowerMean,

                peakNeighborhoodPowerM2 =
                    peakNeighborhoodPowerM2,

                rhythmicEnergyShareMean =
                    rhythmicEnergyShareMean,

                rhythmicEnergyShareM2 =
                    rhythmicEnergyShareM2,

                totalBaselineSeconds =
                    totalBaselineSeconds,

                validSessionCount =
                    validSessionCount,

                totalWindowCount =
                    totalWindowCount
            )

        return profile.takeIf {
            isProfileValid(
                profile = it
            )
        }
    }

    /*
     * Validates the fixed-size accumulated statistics before
     * loading or saving them.
     */
    private fun isProfileValid(
        profile: MotionBaselineProfile
    ): Boolean {

        return profile.accelerationP95Mean.isFinite() &&
                profile.accelerationP95Mean >= 0.0 &&
                profile.accelerationP95M2.isFinite() &&
                profile.accelerationP95M2 >= 0.0 &&

                profile.gyroscopeP95Mean.isFinite() &&
                profile.gyroscopeP95Mean >= 0.0 &&
                profile.gyroscopeP95M2.isFinite() &&
                profile.gyroscopeP95M2 >= 0.0 &&

                profile.accelerationVariationMean.isFinite() &&
                profile.accelerationVariationMean >= 0.0 &&
                profile.accelerationVariationM2.isFinite() &&
                profile.accelerationVariationM2 >= 0.0 &&

                profile.gyroscopeVariationMean.isFinite() &&
                profile.gyroscopeVariationMean >= 0.0 &&
                profile.gyroscopeVariationM2.isFinite() &&
                profile.gyroscopeVariationM2 >= 0.0 &&

                profile.bandAveragePowerMean.isFinite() &&
                profile.bandAveragePowerMean >= 0.0 &&
                profile.bandAveragePowerM2.isFinite() &&
                profile.bandAveragePowerM2 >= 0.0 &&

                profile.peakNeighborhoodPowerMean.isFinite() &&
                profile.peakNeighborhoodPowerMean >= 0.0 &&
                profile.peakNeighborhoodPowerM2.isFinite() &&
                profile.peakNeighborhoodPowerM2 >= 0.0 &&

                profile.rhythmicEnergyShareMean.isFinite() &&
                profile.rhythmicEnergyShareMean in 0.0..1.0 &&
                profile.rhythmicEnergyShareM2.isFinite() &&
                profile.rhythmicEnergyShareM2 >= 0.0 &&

                profile.totalBaselineSeconds.isFinite() &&
                profile.totalBaselineSeconds > 0.0 &&

                profile.validSessionCount > 0 &&
                profile.totalWindowCount > 0
    }

    private fun Map<String, Any>.doubleValue(
        key: String
    ): Double? {

        return (
                this[key] as? Number
                )?.toDouble()
    }

    private fun Map<String, Any>.intValue(
        key: String
    ): Int? {

        return (
                this[key] as? Number
                )?.toInt()
    }
}