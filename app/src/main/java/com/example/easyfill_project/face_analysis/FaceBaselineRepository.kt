package com.example.easyfill_project.face_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Repository responsible for saving and loading
 * the user's facial baseline from Firebase Firestore.
 *
 * This class:
 *
 * 1. Finds the currently authenticated Firebase user.
 * 2. Saves a newly created raw facial baseline.
 * 3. Saves learned derived baseline metrics.
 * 4. Loads an existing compatible baseline.
 * 5. Converts Kotlin baseline objects into Firestore maps.
 * 6. Converts Firestore maps back into Kotlin baseline objects.
 *
 * This class does not:
 *
 * - collect camera frames
 * - create the initial baseline
 * - calculate distress
 */
class FaceBaselineRepository {

    /**
     * Firebase Authentication instance.
     *
     * It is used to identify which user owns the baseline.
     */
    private val auth =
        FirebaseAuth.getInstance()

    /**
     * Firebase Firestore instance.
     *
     * It is used to save and load the facial baseline document.
     */
    private val db =
        FirebaseFirestore.getInstance()

    /**
     * Saves a newly calibrated raw facial baseline.
     *
     * This method is normally called after the user's
     * initial calibration has completed successfully.
     *
     * It saves:
     *
     * - raw facial metrics
     * - number of collected frames
     * - number of valid calibration windows
     * - baseline version
     * - creation/update timestamps
     *
     * Existing derived metrics are cleared because they may
     * no longer match the newly created raw baseline.
     */
    fun saveRawBaseline(
        baseline: FaceBaseline,
        collectedFrameCount: Int,
        validWindowCount: Int,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {

        /*
         * Get the ID of the currently authenticated user.
         *
         * Every user's baseline is stored under their own UID.
         */
        val userId =
            auth.currentUser?.uid

        /*
         * A baseline cannot be saved if no user is logged in.
         */
        if (userId == null) {

            onFailure(
                IllegalStateException(
                    "Cannot save face baseline without an authenticated user."
                )
            )

            return
        }

        /*
         * Verify that the baseline contains every raw facial
         * feature currently required by the application.
         */
        if (!containsAllRawMetrics(baseline.rawMetrics)) {

            onFailure(
                IllegalArgumentException(
                    "Face baseline is missing required raw metrics."
                )
            )

            return
        }

        /*
         * Build the Firestore document reference.
         *
         * The data is stored in:
         *
         * users/{userId}/faceParameters/baseline
         */
        val reference =
            db.collection("users")
                .document(userId)
                .collection("faceParameters")
                .document(BASELINE_DOCUMENT_NAME)

        /*
         * Convert the FaceBaseline object into a map
         * that Firestore can store.
         */
        val data =
            hashMapOf<String, Any>(

                /*
                 * Save the personal raw facial measurements
                 * created during calibration.
                 */
                "rawMetrics" to
                        serializeMetrics(
                            baseline.rawMetrics
                        ),

                /*
                 * Clear old learned derived metrics.
                 *
                 * A new raw calibration means that previous
                 * derived values may no longer be compatible.
                 */
                "derivedMetrics" to
                        emptyMap<String, Any>(),

                /*
                 * Save the current data-format version.
                 *
                 * This helps prevent the application from loading
                 * an old baseline with an incompatible structure.
                 */
                "baselineVersion" to
                        CURRENT_BASELINE_VERSION,

                /*
                 * Save calibration statistics.
                 */
                "collectedFrameCount" to
                        collectedFrameCount,

                "validWindowCount" to
                        validWindowCount,

                /*
                 * Firebase generates the server timestamp.
                 *
                 * This is generally more reliable than using the
                 * local device time.
                 */
                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        /*
         * Use a transaction so that the document can be checked
         * and updated safely as one Firestore operation.
         */
        db.runTransaction { transaction ->

            val document =
                transaction.get(reference)

            /*
             * Add createdAt only when the document does not
             * already exist.
             *
             * This preserves the original creation time.
             */
            if (!document.exists()) {

                data["createdAt"] =
                    FieldValue.serverTimestamp()
            }

            /*
             * Merge the new fields with the existing document.
             *
             * SetOptions.merge() prevents unrelated fields
             * from being deleted.
             */
            transaction.set(
                reference,
                data,
                SetOptions.merge()
            )

            true
        }
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "Raw baseline saved | " +
                            "version=$CURRENT_BASELINE_VERSION | " +
                            "metrics=${baseline.rawMetrics.size} | " +
                            "frames=$collectedFrameCount | " +
                            "windows=$validWindowCount"
                )

                onSuccess()
            }
            .addOnFailureListener { exception ->

                Log.e(
                    TAG,
                    "Failed to save raw face baseline",
                    exception
                )

                onFailure(exception)
            }
    }

    /**
     * Saves only the learned derived baseline metrics.
     *
     * This method does not overwrite the original raw metrics.
     *
     * It is used when FaceDistressAnalyzer learns additional
     * stable information during normal face analysis.
     */
    fun saveDerivedBaseline(
        baseline: FaceBaseline,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {

        val userId =
            auth.currentUser?.uid

        /*
         * Saving requires an authenticated Firebase user.
         */
        if (userId == null) {

            onFailure(
                IllegalStateException(
                    "Cannot save derived baseline without an authenticated user."
                )
            )

            return
        }

        /*
         * If there are no learned derived metrics,
         * there is nothing to save.
         *
         * This is treated as success rather than an error.
         */
        if (baseline.derivedMetrics.isEmpty()) {

            onSuccess()

            return
        }

        /*
         * Only derived metrics are added to this map.
         *
         * Raw metrics are intentionally not included,
         * so they cannot be accidentally overwritten.
         */
        val data =
            mapOf(

                "derivedMetrics" to
                        serializeMetrics(
                            baseline.derivedMetrics
                        ),

                "baselineVersion" to
                        CURRENT_BASELINE_VERSION,

                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        /*
         * Update:
         *
         * users/{userId}/faceParameters/baseline
         */
        db.collection("users")
            .document(userId)
            .collection("faceParameters")
            .document(BASELINE_DOCUMENT_NAME)
            .set(
                data,
                SetOptions.merge()
            )
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "Derived baseline saved | " +
                            "version=$CURRENT_BASELINE_VERSION | " +
                            "metrics=${baseline.derivedMetrics.size}"
                )

                onSuccess()
            }
            .addOnFailureListener { exception ->

                Log.e(
                    TAG,
                    "Failed to save derived face baseline",
                    exception
                )

                onFailure(exception)
            }
    }

    /**
     * Loads the current user's facial baseline from Firebase.
     *
     * The callback receives:
     *
     * FaceBaseline:
     * - a valid saved baseline was found
     *
     * null:
     * - no baseline exists
     * - the saved version is incompatible
     * - required raw metrics are missing
     *
     * onFailure:
     * - Firebase itself failed to load the document
     * - no user is authenticated
     */
    fun getBaseline(
        onSuccess: (FaceBaseline?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {

        val userId =
            auth.currentUser?.uid

        /*
         * A baseline cannot be loaded without knowing
         * which Firebase user owns it.
         */
        if (userId == null) {

            onFailure(
                IllegalStateException(
                    "Cannot load face baseline without an authenticated user."
                )
            )

            return
        }

        /*
         * Read the baseline document from Firestore.
         */
        db.collection("users")
            .document(userId)
            .collection("faceParameters")
            .document(BASELINE_DOCUMENT_NAME)
            .get()
            .addOnSuccessListener { document ->

                /*
                 * No baseline document exists for this user.
                 *
                 * Returning null tells the controller to
                 * begin a new calibration.
                 */
                if (!document.exists()) {

                    Log.d(
                        TAG,
                        "No saved face baseline was found"
                    )

                    onSuccess(null)

                    return@addOnSuccessListener
                }

                /*
                 * Read the version stored with the baseline.
                 */
                val version =
                    document
                        .getLong("baselineVersion")
                        ?.toInt()

                /*
                 * Ignore an older or incompatible baseline.
                 *
                 * Returning null causes a fresh calibration.
                 */
                if (version != CURRENT_BASELINE_VERSION) {

                    Log.d(
                        TAG,
                        "Ignoring incompatible baseline | " +
                                "savedVersion=$version | " +
                                "requiredVersion=$CURRENT_BASELINE_VERSION"
                    )

                    onSuccess(null)

                    return@addOnSuccessListener
                }

                /*
                 * Convert the Firestore maps back into
                 * Kotlin baseline metrics.
                 */
                val rawMetrics =
                    deserializeMetrics(
                        document.get("rawMetrics")
                    )

                val derivedMetrics =
                    deserializeMetrics(
                        document.get("derivedMetrics")
                    )

                /*
                 * Check that the saved baseline contains
                 * all required raw facial features.
                 */
                if (!containsAllRawMetrics(rawMetrics)) {

                    val missingFeatures =
                        FaceStats.rawFeatures.filter { feature ->
                            feature !in rawMetrics
                        }

                    Log.e(
                        TAG,
                        "Saved baseline is missing raw metrics | " +
                                "missing=$missingFeatures"
                    )

                    /*
                     * Treat the incomplete baseline as unavailable.
                     *
                     * The controller will start calibration again.
                     */
                    onSuccess(null)

                    return@addOnSuccessListener
                }

                /*
                 * Reconstruct the complete FaceBaseline object.
                 */
                val baseline =
                    FaceBaseline(
                        rawMetrics = rawMetrics,
                        derivedMetrics = derivedMetrics
                    )

                Log.d(
                    TAG,
                    "Face baseline loaded | " +
                            "version=$version | " +
                            "raw=${rawMetrics.size} | " +
                            "derived=${derivedMetrics.size}"
                )

                onSuccess(baseline)
            }
            .addOnFailureListener { exception ->

                Log.e(
                    TAG,
                    "Failed to load face baseline",
                    exception
                )

                onFailure(exception)
            }
    }

    /**
     * Converts baseline metrics into a Firestore-safe map.
     *
     * Kotlin enum keys cannot be stored directly in the desired
     * document structure, so the enum names are converted to strings.
     */
    private fun serializeMetrics(
        metrics: Map<FaceBaselineFeature, BaselineMetric>
    ): Map<String, Any> {

        return metrics

            /*
             * Convert:
             *
             * FaceBaselineFeature.EYE_OPENNESS
             *
             * into:
             *
             * "EYE_OPENNESS"
             */
            .mapKeys { entry ->
                entry.key.name
            }

            /*
             * Convert each BaselineMetric object into a map.
             */
            .mapValues { entry ->

                mapOf(
                    "median" to
                            entry.value.median,

                    "mad" to
                            entry.value.mad,

                    "sampleCount" to
                            entry.value.sampleCount
                )
            }
    }

    /**
     * Converts Firestore data back into baseline metrics.
     *
     * Unknown or invalid values are ignored instead of causing
     * the entire baseline-loading operation to crash.
     */
    private fun deserializeMetrics(
        storedValue: Any?
    ): Map<FaceBaselineFeature, BaselineMetric> {

        /*
         * Firestore should return a map.
         *
         * If the value is missing or has another type,
         * return an empty map.
         */
        val storedMetrics =
            storedValue as? Map<*, *>
                ?: return emptyMap()

        val parsed =
            mutableMapOf<
                    FaceBaselineFeature,
                    BaselineMetric
                    >()

        /*
         * Read every stored feature separately.
         */
        storedMetrics.forEach { (key, value) ->

            /*
             * The feature name must be a string.
             */
            val featureName =
                key as? String
                    ?: return@forEach

            /*
             * Convert the stored name back into
             * the FaceBaselineFeature enum.
             *
             * Unknown names are ignored.
             */
            val feature =
                runCatching {

                    FaceBaselineFeature.valueOf(
                        featureName
                    )

                }.getOrNull()
                    ?: return@forEach

            /*
             * The feature value must contain another map
             * with median, MAD and sample count.
             */
            val metricData =
                value as? Map<*, *>
                    ?: return@forEach

            /*
             * Read and convert the median.
             *
             * Firestore numeric values may be returned using
             * different Number implementations.
             */
            val median =
                (metricData["median"] as? Number)
                    ?.toFloat()
                    ?: return@forEach

            /*
             * Read and convert MAD.
             */
            val mad =
                (metricData["mad"] as? Number)
                    ?.toFloat()
                    ?: return@forEach

            /*
             * Read and convert the sample count.
             */
            val sampleCount =
                (metricData["sampleCount"] as? Number)
                    ?.toInt()
                    ?: return@forEach

            /*
             * Reject invalid stored metric values.
             */
            if (
                !median.isFinite() ||
                !mad.isFinite() ||
                mad < 0f ||
                sampleCount <= 0
            ) {
                return@forEach
            }

            /*
             * Add the valid metric to the reconstructed map.
             */
            parsed[feature] =
                BaselineMetric(
                    median = median,
                    mad = mad,
                    sampleCount = sampleCount
                )
        }

        return parsed
    }

    /**
     * Checks whether every required raw facial feature
     * exists in the provided metrics map.
     */
    private fun containsAllRawMetrics(
        metrics: Map<FaceBaselineFeature, BaselineMetric>
    ): Boolean {

        return FaceStats.rawFeatures.all { feature ->
            feature in metrics
        }
    }

    companion object {

        private const val TAG =
            "FACE_BASELINE_FIREBASE"

        /**
         * Name of the Firestore document that contains
         * the user's facial baseline.
         */
        private const val BASELINE_DOCUMENT_NAME =
            "baseline"

        /**
         * Current expected structure of the saved baseline.
         *
         * Version 2 includes normalized eyebrow geometry.
         *
         * If the structure changes in the future,
         * this number should be increased.
         */
        private const val CURRENT_BASELINE_VERSION =
            2
    }
}