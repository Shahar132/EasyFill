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
                .document("baseline")

        val data =
            hashMapOf<String, Any>(
                "rawMetrics" to
                        serializeMetrics(
                            baseline.rawMetrics
                        ),
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
     * Saves only derived metrics.
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
                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        db.collection("users")
            .document(userId)
            .collection("faceParameters")
            .document("baseline")
            .set(
                data,
                SetOptions.merge()
            )
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "Derived baseline saved | " +
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
            .document("baseline")
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
                        "Ignoring incompatible baseline version: $version"
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

                    Log.e(
                        TAG,
                        "Saved baseline is missing raw metrics"
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

    private fun containsAllRawMetrics(
        metrics: Map<FaceBaselineFeature, BaselineMetric>
    ): Boolean {

        return FaceStats.rawFeatures.all { feature ->
            feature in metrics
        }
    }

    companion object {
        private const val TAG = "FACE_BASELINE_FIREBASE"
        private const val CURRENT_BASELINE_VERSION = 1
    }
}