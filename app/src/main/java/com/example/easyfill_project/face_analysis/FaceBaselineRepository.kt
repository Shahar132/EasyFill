package com.example.easyfill_project.face_analysis

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Saves and loads the user's facial baseline.
 */
class FaceBaselineRepository {

    private val auth =
        FirebaseAuth.getInstance()

    private val db =
        FirebaseFirestore.getInstance()

    /**
     * Saves a newly calibrated raw baseline.
     *
     * Previously learned derived metrics are cleared because
     * they may no longer match the new raw calibration.
     */
    fun saveRawBaseline(
        baseline: FaceBaseline,
        collectedFrameCount: Int,
        validWindowCount: Int,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            auth.currentUser?.uid

        if (userId == null) {
            onFailure(
                IllegalStateException(
                    "Cannot save face baseline without an authenticated user."
                )
            )

            return
        }

        if (!containsAllRawMetrics(baseline.rawMetrics)) {
            onFailure(
                IllegalArgumentException(
                    "Face baseline is missing required raw metrics."
                )
            )

            return
        }

        val reference =
            db.collection("users")
                .document(userId)
                .collection("faceParameters")
                .document(BASELINE_DOCUMENT_NAME)

        val data =
            hashMapOf<String, Any>(
                "rawMetrics" to
                        serializeMetrics(
                            baseline.rawMetrics
                        ),

                /*
                 * Derived metrics from the previous calibration
                 * must not remain after raw calibration changes.
                 */
                "derivedMetrics" to
                        emptyMap<String, Any>(),

                "baselineVersion" to
                        CURRENT_BASELINE_VERSION,

                "collectedFrameCount" to
                        collectedFrameCount,

                "validWindowCount" to
                        validWindowCount,

                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        db.runTransaction { transaction ->
            val document =
                transaction.get(reference)

            if (!document.exists()) {
                data["createdAt"] =
                    FieldValue.serverTimestamp()
            }

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
     * Saves only learned derived metrics.
     *
     * Raw metrics are not overwritten.
     */
    fun saveDerivedBaseline(
        baseline: FaceBaseline,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            auth.currentUser?.uid

        if (userId == null) {
            onFailure(
                IllegalStateException(
                    "Cannot save derived baseline without an authenticated user."
                )
            )

            return
        }

        if (baseline.derivedMetrics.isEmpty()) {
            onSuccess()
            return
        }

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
     * Loads a compatible personal baseline.
     *
     * Old versions and baselines that are missing any
     * required raw feature are ignored automatically.
     */
    fun getBaseline(
        onSuccess: (FaceBaseline?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId =
            auth.currentUser?.uid

        if (userId == null) {
            onFailure(
                IllegalStateException(
                    "Cannot load face baseline without an authenticated user."
                )
            )

            return
        }

        db.collection("users")
            .document(userId)
            .collection("faceParameters")
            .document(BASELINE_DOCUMENT_NAME)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.d(
                        TAG,
                        "No saved face baseline was found"
                    )

                    onSuccess(null)

                    return@addOnSuccessListener
                }

                val version =
                    document
                        .getLong("baselineVersion")
                        ?.toInt()

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

                val rawMetrics =
                    deserializeMetrics(
                        document.get("rawMetrics")
                    )

                val derivedMetrics =
                    deserializeMetrics(
                        document.get("derivedMetrics")
                    )

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

                    onSuccess(null)

                    return@addOnSuccessListener
                }

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
     * Converts feature metrics into a Firestore-safe map.
     */
    private fun serializeMetrics(
        metrics: Map<FaceBaselineFeature, BaselineMetric>
    ): Map<String, Any> {
        return metrics
            .mapKeys { entry ->
                entry.key.name
            }
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
     * Converts stored Firestore values into baseline metrics.
     *
     * Unknown feature names are ignored so future enum changes
     * do not cause the entire document to fail loading.
     */
    private fun deserializeMetrics(
        storedValue: Any?
    ): Map<FaceBaselineFeature, BaselineMetric> {
        val storedMetrics =
            storedValue as? Map<*, *>
                ?: return emptyMap()

        val parsed =
            mutableMapOf<
                    FaceBaselineFeature,
                    BaselineMetric
                    >()

        storedMetrics.forEach { (key, value) ->
            val featureName =
                key as? String
                    ?: return@forEach

            val feature =
                runCatching {
                    FaceBaselineFeature.valueOf(
                        featureName
                    )
                }.getOrNull()
                    ?: return@forEach

            val metricData =
                value as? Map<*, *>
                    ?: return@forEach

            val median =
                (metricData["median"] as? Number)
                    ?.toFloat()
                    ?: return@forEach

            val mad =
                (metricData["mad"] as? Number)
                    ?.toFloat()
                    ?: return@forEach

            val sampleCount =
                (metricData["sampleCount"] as? Number)
                    ?.toInt()
                    ?: return@forEach

            if (
                !median.isFinite() ||
                !mad.isFinite() ||
                mad < 0f ||
                sampleCount <= 0
            ) {
                return@forEach
            }

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
     * Checks that every currently required raw feature exists.
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

        private const val BASELINE_DOCUMENT_NAME =
            "baseline"

        /*
         * Version 2 adds normalized eyebrow geometry.
         */
        private const val CURRENT_BASELINE_VERSION =
            2
    }
}