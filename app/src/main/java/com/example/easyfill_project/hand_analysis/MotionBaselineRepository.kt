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
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.UUID

/*
 * Saves and loads the user's accumulated personal motion
 * baseline from Firestore.
 *
 * Firestore structure:
 *
 * users/{userId}/motionParameters/baseline
 *     - calculated personal profile
 *     - accumulated metadata
 *
 * users/{userId}/motionParameters/baseline/windows/{windowId}
 *     - one valid two-second baseline window summary
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

        private const val WINDOWS_COLLECTION =
            "windows"
    }

    /*
     * Saves the first valid baseline.
     *
     * The profile and all initial window summaries are written
     * in one atomic batch.
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

        if (baselineData.windows.isEmpty()) {
            onFailure(
                IllegalArgumentException(
                    "Cannot save an initial baseline without windows"
                )
            )
            return
        }

        val baselineDocument =
            getBaselineDocument(
                userId = userId
            )

        val sessionId =
            UUID.randomUUID().toString()

        val batch =
            db.batch()

        /*
         * Initial save replaces the profile document.
         *
         * The user will manually delete the old Firebase
         * baseline once before testing the new structure.
         */
        batch.set(
            baselineDocument,
            profileToMap(
                profile = baselineData.profile,
                includeCreatedAt = true
            )
        )

        baselineData.windows.forEach { window ->

            val windowDocument =
                baselineDocument
                    .collection(WINDOWS_COLLECTION)
                    .document()

            batch.set(
                windowDocument,
                windowToMap(
                    window = window,
                    sessionId = sessionId
                )
            )
        }

        batch.commit()
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    """
                    Initial motion baseline saved.
                    sessions=${baselineData.profile.validSessionCount}
                    windows=${baselineData.profile.totalWindowCount}
                    seconds=${baselineData.profile.totalBaselineSeconds}
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
     * Appends one new valid ten-second baseline session.
     *
     * Only the new two-second windows are written.
     * Previously stored windows are not deleted or replaced.
     *
     * updatedProfile must already be recalculated from:
     *
     * existing windows + new windows
     */
    fun appendBaselineSession(
        updatedProfile: MotionBaselineProfile,
        newWindows: List<MotionBaselineWindowSummary>,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            getAuthenticatedUserId(
                onFailure = onFailure
            ) ?: return

        if (newWindows.isEmpty()) {
            onFailure(
                IllegalArgumentException(
                    "Cannot append an empty baseline session"
                )
            )
            return
        }

        val baselineDocument =
            getBaselineDocument(
                userId = userId
            )

        val sessionId =
            UUID.randomUUID().toString()

        val batch =
            db.batch()

        /*
         * Merge keeps the original createdAt value while
         * updating the calculated personal profile.
         */
        batch.set(
            baselineDocument,
            profileToMap(
                profile = updatedProfile,
                includeCreatedAt = false
            ),
            SetOptions.merge()
        )

        newWindows.forEach { window ->

            val windowDocument =
                baselineDocument
                    .collection(WINDOWS_COLLECTION)
                    .document()

            batch.set(
                windowDocument,
                windowToMap(
                    window = window,
                    sessionId = sessionId
                )
            )
        }

        batch.commit()
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    """
                    Motion baseline session appended.
                    addedWindows=${newWindows.size}
                    totalSessions=${updatedProfile.validSessionCount}
                    totalWindows=${updatedProfile.totalWindowCount}
                    totalSeconds=${updatedProfile.totalBaselineSeconds}
                    """.trimIndent()
                )

                onSuccess()
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Failed to append motion baseline session",
                    error
                )

                onFailure(error)
            }
    }

    /*
     * Loads the calculated profile and every historical valid
     * two-second baseline window.
     *
     * null means that no complete valid baseline currently
     * exists.
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
            .addOnSuccessListener { profileDocument ->

                if (!profileDocument.exists()) {

                    Log.d(
                        TAG,
                        "No saved motion baseline exists"
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                val profileData =
                    profileDocument.data

                if (profileData == null) {

                    Log.d(
                        TAG,
                        "Motion baseline profile document is empty"
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                val profile =
                    mapToProfile(
                        data = profileData
                    )

                if (profile == null) {

                    Log.d(
                        TAG,
                        "Motion baseline profile is invalid"
                    )

                    onSuccess(null)
                    return@addOnSuccessListener
                }

                baselineDocument
                    .collection(WINDOWS_COLLECTION)
                    .orderBy(
                        "createdAt",
                        Query.Direction.ASCENDING
                    )
                    .get()
                    .addOnSuccessListener { windowDocuments ->

                        val windows =
                            windowDocuments.documents
                                .mapNotNull { document ->

                                    val data =
                                        document.data
                                            ?: return@mapNotNull null

                                    mapToWindow(
                                        data = data
                                    )
                                }

                        /*
                         * The profile and stored history must
                         * describe the same accumulated data.
                         */
                        val historyIsComplete =
                            windows.isNotEmpty() &&
                                    windows.size ==
                                    profile.totalWindowCount

                        if (!historyIsComplete) {

                            Log.d(
                                TAG,
                                """
                                Motion baseline history is incomplete.
                                expectedWindows=${profile.totalWindowCount}
                                loadedWindows=${windows.size}
                                """.trimIndent()
                            )

                            onSuccess(null)
                            return@addOnSuccessListener
                        }

                        Log.d(
                            TAG,
                            """
                            Motion baseline loaded.
                            sessions=${profile.validSessionCount}
                            windows=${profile.totalWindowCount}
                            seconds=${profile.totalBaselineSeconds}
                            """.trimIndent()
                        )

                        onSuccess(
                            MotionBaselineData(
                                profile = profile,
                                windows = windows
                            )
                        )
                    }
                    .addOnFailureListener { error ->

                        Log.e(
                            TAG,
                            "Failed to load baseline windows",
                            error
                        )

                        onFailure(error)
                    }
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Failed to load motion baseline profile",
                    error
                )

                onFailure(error)
            }
    }

    /*
     * Returns the currently authenticated user ID.
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
        db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(PARAMETERS_COLLECTION)
            .document(BASELINE_DOCUMENT)

    /*
     * Converts the accumulated personal profile into
     * Firestore fields.
     */
    private fun profileToMap(
        profile: MotionBaselineProfile,
        includeCreatedAt: Boolean
    ): Map<String, Any> {
        val data =
            mutableMapOf<String, Any>(
                "accelerationP95Median" to
                        profile.accelerationP95Median,

                "accelerationP95Mad" to
                        profile.accelerationP95Mad,

                "gyroscopeP95Median" to
                        profile.gyroscopeP95Median,

                "gyroscopeP95Mad" to
                        profile.gyroscopeP95Mad,

                "accelerationVariationMedian" to
                        profile.accelerationVariationMedian,

                "accelerationVariationMad" to
                        profile.accelerationVariationMad,

                "gyroscopeVariationMedian" to
                        profile.gyroscopeVariationMedian,

                "gyroscopeVariationMad" to
                        profile.gyroscopeVariationMad,

                "bandAveragePowerMedian" to
                        profile.bandAveragePowerMedian,

                "bandAveragePowerMad" to
                        profile.bandAveragePowerMad,

                "peakNeighborhoodPowerMedian" to
                        profile.peakNeighborhoodPowerMedian,

                "peakNeighborhoodPowerMad" to
                        profile.peakNeighborhoodPowerMad,

                "rhythmicEnergyShareMedian" to
                        profile.rhythmicEnergyShareMedian,

                "rhythmicEnergyShareMad" to
                        profile.rhythmicEnergyShareMad,

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
     * Converts one two-second baseline window into Firestore
     * fields.
     */
    private fun windowToMap(
        window: MotionBaselineWindowSummary,
        sessionId: String
    ): Map<String, Any> {
        return hashMapOf(
            "sessionId" to
                    sessionId,

            "durationSeconds" to
                    window.durationSeconds,

            "accelerationP95" to
                    window.accelerationP95,

            "gyroscopeP95" to
                    window.gyroscopeP95,

            "accelerationVariation" to
                    window.accelerationVariation,

            "gyroscopeVariation" to
                    window.gyroscopeVariation,

            "peakFrequencyHz" to
                    window.peakFrequencyHz,

            "bandAveragePower" to
                    window.bandAveragePower,

            "peakNeighborhoodPower" to
                    window.peakNeighborhoodPower,

            "concentrationRatio" to
                    window.concentrationRatio,

            "narrowbandRatio" to
                    window.narrowbandRatio,

            "rhythmicEnergyShare" to
                    window.rhythmicEnergyShare,

            "createdAt" to
                    FieldValue.serverTimestamp()
        )
    }

    /*
     * Converts Firestore profile fields into the application
     * baseline model.
     */
    private fun mapToProfile(
        data: Map<String, Any>
    ): MotionBaselineProfile? {
        val accelerationP95Median =
            data.doubleValue(
                key = "accelerationP95Median"
            ) ?: return null

        val accelerationP95Mad =
            data.doubleValue(
                key = "accelerationP95Mad"
            ) ?: return null

        val gyroscopeP95Median =
            data.doubleValue(
                key = "gyroscopeP95Median"
            ) ?: return null

        val gyroscopeP95Mad =
            data.doubleValue(
                key = "gyroscopeP95Mad"
            ) ?: return null

        val accelerationVariationMedian =
            data.doubleValue(
                key = "accelerationVariationMedian"
            ) ?: return null

        val accelerationVariationMad =
            data.doubleValue(
                key = "accelerationVariationMad"
            ) ?: return null

        val gyroscopeVariationMedian =
            data.doubleValue(
                key = "gyroscopeVariationMedian"
            ) ?: return null

        val gyroscopeVariationMad =
            data.doubleValue(
                key = "gyroscopeVariationMad"
            ) ?: return null

        val bandAveragePowerMedian =
            data.doubleValue(
                key = "bandAveragePowerMedian"
            ) ?: return null

        val bandAveragePowerMad =
            data.doubleValue(
                key = "bandAveragePowerMad"
            ) ?: return null

        val peakNeighborhoodPowerMedian =
            data.doubleValue(
                key = "peakNeighborhoodPowerMedian"
            ) ?: return null

        val peakNeighborhoodPowerMad =
            data.doubleValue(
                key = "peakNeighborhoodPowerMad"
            ) ?: return null

        val rhythmicEnergyShareMedian =
            data.doubleValue(
                key = "rhythmicEnergyShareMedian"
            ) ?: return null

        val rhythmicEnergyShareMad =
            data.doubleValue(
                key = "rhythmicEnergyShareMad"
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

        val valuesAreValid =
            accelerationP95Median.isFinite() &&
                    accelerationP95Mad.isFinite() &&
                    gyroscopeP95Median.isFinite() &&
                    gyroscopeP95Mad.isFinite() &&
                    accelerationVariationMedian.isFinite() &&
                    accelerationVariationMad.isFinite() &&
                    gyroscopeVariationMedian.isFinite() &&
                    gyroscopeVariationMad.isFinite() &&
                    bandAveragePowerMedian.isFinite() &&
                    bandAveragePowerMad.isFinite() &&
                    peakNeighborhoodPowerMedian.isFinite() &&
                    peakNeighborhoodPowerMad.isFinite() &&
                    rhythmicEnergyShareMedian.isFinite() &&
                    rhythmicEnergyShareMad.isFinite() &&
                    totalBaselineSeconds.isFinite() &&
                    totalBaselineSeconds > 0.0 &&
                    validSessionCount > 0 &&
                    totalWindowCount > 0

        if (!valuesAreValid) {
            return null
        }

        return MotionBaselineProfile(
            accelerationP95Median =
                accelerationP95Median,

            accelerationP95Mad =
                accelerationP95Mad,

            gyroscopeP95Median =
                gyroscopeP95Median,

            gyroscopeP95Mad =
                gyroscopeP95Mad,

            accelerationVariationMedian =
                accelerationVariationMedian,

            accelerationVariationMad =
                accelerationVariationMad,

            gyroscopeVariationMedian =
                gyroscopeVariationMedian,

            gyroscopeVariationMad =
                gyroscopeVariationMad,

            bandAveragePowerMedian =
                bandAveragePowerMedian,

            bandAveragePowerMad =
                bandAveragePowerMad,

            peakNeighborhoodPowerMedian =
                peakNeighborhoodPowerMedian,

            peakNeighborhoodPowerMad =
                peakNeighborhoodPowerMad,

            rhythmicEnergyShareMedian =
                rhythmicEnergyShareMedian,

            rhythmicEnergyShareMad =
                rhythmicEnergyShareMad,

            totalBaselineSeconds =
                totalBaselineSeconds,

            validSessionCount =
                validSessionCount,

            totalWindowCount =
                totalWindowCount
        )
    }

    /*
     * Converts one Firestore window document into the
     * application baseline-window model.
     */
    private fun mapToWindow(
        data: Map<String, Any>
    ): MotionBaselineWindowSummary? {
        val durationSeconds =
            data.doubleValue(
                key = "durationSeconds"
            ) ?: return null

        val accelerationP95 =
            data.doubleValue(
                key = "accelerationP95"
            ) ?: return null

        val gyroscopeP95 =
            data.doubleValue(
                key = "gyroscopeP95"
            ) ?: return null

        val accelerationVariation =
            data.doubleValue(
                key = "accelerationVariation"
            ) ?: return null

        val gyroscopeVariation =
            data.doubleValue(
                key = "gyroscopeVariation"
            ) ?: return null

        val peakFrequencyHz =
            data.doubleValue(
                key = "peakFrequencyHz"
            ) ?: return null

        val bandAveragePower =
            data.doubleValue(
                key = "bandAveragePower"
            ) ?: return null

        val peakNeighborhoodPower =
            data.doubleValue(
                key = "peakNeighborhoodPower"
            ) ?: return null

        val concentrationRatio =
            data.doubleValue(
                key = "concentrationRatio"
            ) ?: return null

        val narrowbandRatio =
            data.doubleValue(
                key = "narrowbandRatio"
            ) ?: return null

        val rhythmicEnergyShare =
            data.doubleValue(
                key = "rhythmicEnergyShare"
            ) ?: return null

        val valuesAreValid =
            durationSeconds.isFinite() &&
                    durationSeconds > 0.0 &&
                    accelerationP95.isFinite() &&
                    accelerationP95 >= 0.0 &&
                    gyroscopeP95.isFinite() &&
                    gyroscopeP95 >= 0.0 &&
                    accelerationVariation.isFinite() &&
                    accelerationVariation >= 0.0 &&
                    gyroscopeVariation.isFinite() &&
                    gyroscopeVariation >= 0.0 &&
                    peakFrequencyHz.isFinite() &&
                    peakFrequencyHz >= 0.0 &&
                    bandAveragePower.isFinite() &&
                    bandAveragePower >= 0.0 &&
                    peakNeighborhoodPower.isFinite() &&
                    peakNeighborhoodPower >= 0.0 &&
                    concentrationRatio.isFinite() &&
                    concentrationRatio >= 0.0 &&
                    narrowbandRatio.isFinite() &&
                    narrowbandRatio >= 0.0 &&
                    rhythmicEnergyShare.isFinite() &&
                    rhythmicEnergyShare in 0.0..1.0

        if (!valuesAreValid) {
            return null
        }

        return MotionBaselineWindowSummary(
            durationSeconds =
                durationSeconds,

            accelerationP95 =
                accelerationP95,

            gyroscopeP95 =
                gyroscopeP95,

            accelerationVariation =
                accelerationVariation,

            gyroscopeVariation =
                gyroscopeVariation,

            peakFrequencyHz =
                peakFrequencyHz,

            bandAveragePower =
                bandAveragePower,

            peakNeighborhoodPower =
                peakNeighborhoodPower,

            concentrationRatio =
                concentrationRatio,

            narrowbandRatio =
                narrowbandRatio,

            rhythmicEnergyShare =
                rhythmicEnergyShare
        )
    }

    private fun Map<String, Any>.doubleValue(
        key: String
    ): Double? {
        return (this[key] as? Number)
            ?.toDouble()
    }

    private fun Map<String, Any>.intValue(
        key: String
    ): Int? {
        return (this[key] as? Number)
            ?.toInt()
    }
}