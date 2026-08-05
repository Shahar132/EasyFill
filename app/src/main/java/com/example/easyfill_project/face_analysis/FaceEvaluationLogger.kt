package com.example.easyfill_project.face_analysis

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/*
 * Stores completed facial-analysis windows for later evaluation.
 *
 * The logger does not change the facial-analysis algorithm.
 * It only saves results that were already calculated.
 *
 * Each JSONL line represents one completed 500 ms face window.
 */
class FaceEvaluationLogger(
    context: Context
) {

    companion object {

        private const val TAG =
            "FACE_EVALUATION"
    }

    private val stateLock =
        Any()

    /*
     * Serializes file writes so two facial windows
     * cannot write to the same file simultaneously.
     */
    private val writeMutex =
        Mutex()

    private val ioScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private val storageRoot =
        context.getExternalFilesDir(
            Environment.DIRECTORY_DOCUMENTS
        ) ?: context.filesDir

    private val evaluationDirectory =
        File(
            storageRoot,
            "face_evaluation"
        )

    private var currentFile: File? =
        null

    private var sessionId: String? =
        null

    private var participantId: String? =
        null

    private var scenario: String? =
        null

    private var expectedContributor:
            FaceDistressContributor? =
        null

    private var expectedLevel: Int? =
        null

    private var windowIndex =
        0

    val isSessionActive: Boolean
        get() =
            synchronized(stateLock) {
                currentFile != null
            }

    /*
     * Starts one controlled face-evaluation session.
     */
    fun startSession(
        participantId: String,
        scenario: String,
        expectedContributor: FaceDistressContributor,
        expectedLevel: Int
    ): File {

        val cleanParticipantId =
            participantId.trim()

        val cleanScenario =
            scenario.trim()

        require(
            cleanParticipantId.isNotBlank()
        ) {
            "Participant ID must not be blank"
        }

        require(
            cleanParticipantId.matches(
                Regex("[A-Za-z0-9_-]+")
            )
        ) {
            "Participant ID may contain only letters, numbers, _ and -"
        }

        require(
            cleanScenario.isNotBlank()
        ) {
            "Face evaluation scenario must not be blank"
        }

        require(
            expectedLevel in 0..4
        ) {
            "Expected face level must be between 0 and 4"
        }

        require(
            expectedLevel > 0 ||
                    expectedContributor ==
                    FaceDistressContributor.NONE
        ) {
            "Level 0 must use the NONE contributor"
        }

        require(
            expectedLevel == 0 ||
                    expectedContributor !=
                    FaceDistressContributor.NONE
        ) {
            "Levels 1-4 must use a facial contributor"
        }

        stopSession()

        if (
            !evaluationDirectory.exists() &&
            !evaluationDirectory.mkdirs()
        ) {
            throw IllegalStateException(
                "Could not create the face evaluation directory"
            )
        }

        val newSessionId =
            "face_${cleanParticipantId}_${System.currentTimeMillis()}"

        val outputFile =
            File(
                evaluationDirectory,
                "$newSessionId.jsonl"
            )

        synchronized(stateLock) {

            currentFile =
                outputFile

            sessionId =
                newSessionId

            this.participantId =
                cleanParticipantId

            this.scenario =
                cleanScenario

            this.expectedContributor =
                expectedContributor

            this.expectedLevel =
                expectedLevel

            windowIndex =
                0
        }

        Log.d(
            TAG,
            """
            Face evaluation session started.
            sessionId=$newSessionId
            participantId=$cleanParticipantId
            scenario=$cleanScenario
            expectedContributor=${expectedContributor.name}
            expectedLevel=$expectedLevel
            file=${outputFile.absolutePath}
            """.trimIndent()
        )

        return outputFile
    }

    /*
     * Stops the current evaluation session.
     */
    fun stopSession() {

        val stoppedSession =
            synchronized(stateLock) {

                val snapshot =
                    StoppedFaceSession(
                        sessionId =
                            sessionId,

                        participantId =
                            participantId,

                        windowCount =
                            windowIndex,

                        filePath =
                            currentFile
                                ?.absolutePath
                    )

                currentFile =
                    null

                sessionId =
                    null

                participantId =
                    null

                scenario =
                    null

                expectedContributor =
                    null

                expectedLevel =
                    null

                windowIndex =
                    0

                snapshot
            }

        if (stoppedSession.sessionId != null) {

            Log.d(
                TAG,
                """
                Face evaluation session stopped.
                sessionId=${stoppedSession.sessionId}
                participantId=${stoppedSession.participantId}
                windows=${stoppedSession.windowCount}
                file=${stoppedSession.filePath}
                """.trimIndent()
            )
        }
    }

    /*
     * Saves one completed facial-analysis result.
     *
     * baseline is the personal baseline currently used
     * by the analyzer for this result.
     */
    fun appendResult(
        result: FaceDistressResult,
        baseline: FaceBaseline
    ) {

        val activeWindow =
            nextActiveWindow()
                ?: return

        val json =
            result.toEvaluationJson(
                sessionId =
                    activeWindow.sessionId,

                participantId =
                    activeWindow.participantId,

                windowIndex =
                    activeWindow.windowIndex,

                scenario =
                    activeWindow.scenario,

                expectedContributor =
                    activeWindow.expectedContributor,

                expectedLevel =
                    activeWindow.expectedLevel,

                baseline =
                    baseline
            )

        ioScope.launch {

            val writeResult =
                runCatching {

                    writeMutex.withLock {

                        activeWindow.outputFile.appendText(
                            text =
                                json.toString() + "\n",

                            charset =
                                Charsets.UTF_8
                        )
                    }
                }

            if (writeResult.isFailure) {

                Log.e(
                    TAG,
                    "Failed to save a face evaluation window",
                    writeResult.exceptionOrNull()
                )

                return@launch
            }

            Log.d(
                TAG,
                """
                Face evaluation window saved.
                sessionId=${activeWindow.sessionId}
                participantId=${activeWindow.participantId}
                windowIndex=${activeWindow.windowIndex}
                score=${result.score}
                level=${result.level}
                contributor=${result.topContributor}
                reliable=${result.isReliable}
                """.trimIndent()
            )
        }
    }

    private fun nextActiveWindow():
            ActiveFaceEvaluationWindow? {

        return synchronized(stateLock) {

            val outputFile =
                currentFile
                    ?: return@synchronized null

            val activeSessionId =
                sessionId
                    ?: return@synchronized null

            val activeParticipantId =
                participantId
                    ?: return@synchronized null

            val activeScenario =
                scenario
                    ?: return@synchronized null

            val activeExpectedContributor =
                expectedContributor
                    ?: return@synchronized null

            val activeExpectedLevel =
                expectedLevel
                    ?: return@synchronized null

            windowIndex +=
                1

            ActiveFaceEvaluationWindow(
                outputFile =
                    outputFile,

                sessionId =
                    activeSessionId,

                participantId =
                    activeParticipantId,

                windowIndex =
                    windowIndex,

                scenario =
                    activeScenario,

                expectedContributor =
                    activeExpectedContributor,

                expectedLevel =
                    activeExpectedLevel
            )
        }
    }
}

/*
 * Converts one facial result into one JSONL record.
 */
private fun FaceDistressResult.toEvaluationJson(
    sessionId: String,
    participantId: String,
    windowIndex: Int,
    scenario: String,
    expectedContributor: FaceDistressContributor,
    expectedLevel: Int,
    baseline: FaceBaseline
): JSONObject {

    return JSONObject()
        .put(
            "sessionId",
            sessionId
        )
        .put(
            "participantId",
            participantId
        )
        .put(
            "windowIndex",
            windowIndex
        )
        .put(
            "timestampMs",
            System.currentTimeMillis()
        )
        .put(
            "modality",
            "FACE"
        )
        .put(
            "scenario",
            scenario
        )
        .put(
            "expectedDistress",
            expectedLevel > 0
        )
        .put(
            "expectedContributor",
            expectedContributor.name
        )
        .put(
            "expectedLevel",
            expectedLevel
        )
        .put(
            "score",
            finiteFloatOrNull(
                score
            )
        )
        .put(
            "level",
            level
        )
        .put(
            "isReliable",
            isReliable
        )
        .put(
            "eyesScore",
            finiteFloatOrNull(
                eyesScore
            )
        )
        .put(
            "browsScore",
            finiteFloatOrNull(
                browsScore
            )
        )
        .put(
            "activityScore",
            finiteFloatOrNull(
                activityScore
            )
        )
        .put(
            "peakFeatureScore",
            finiteFloatOrNull(
                peakFeatureScore
            )
        )
        .put(
            "topContributor",
            topContributor.name
        )
        .put(
            "windowStartTimestampMs",
            windowStartTimestampMs
        )
        .put(
            "windowEndTimestampMs",
            windowEndTimestampMs
        )
        .put(
            "rawFeatureResults",
            rawFeatureResultsToJson(
                rawFeatureResults
            )
        )
        .put(
            "derivedMetrics",
            derivedMetricsToJson(
                derivedMetrics
            )
        )
        .put(
            "baselineMetrics",
            baselineMetricsToJson(
                baseline.metrics
            )
        )
}

/*
 * Saves the raw comparison result of every facial feature.
 */
private fun rawFeatureResultsToJson(
    results:
    Map<FaceBaselineFeature, FaceWindowFeatureResult>
): JSONObject {

    val json =
        JSONObject()

    results.forEach { (feature, result) ->

        json.put(
            feature.name,

            JSONObject()
                .put(
                    "currentMedian",
                    finiteFloatOrNull(
                        result.currentMedian
                    )
                )
                .put(
                    "baselineMedian",
                    finiteFloatOrNull(
                        result.baselineMedian
                    )
                )
                .put(
                    "baselineMad",
                    finiteFloatOrNull(
                        result.baselineMad
                    )
                )
                .put(
                    "effectiveMad",
                    finiteFloatOrNull(
                        result.effectiveMad
                    )
                )
                .put(
                    "modifiedZ",
                    finiteFloatOrNull(
                        result.modifiedZ
                    )
                )
                .put(
                    "score",
                    finiteFloatOrNull(
                        result.score
                    )
                )
        )
    }

    return json
}

/*
 * Saves the current derived metrics calculated from
 * recent facial windows.
 */
private fun derivedMetricsToJson(
    metrics:
    Map<FaceBaselineFeature, Float>
): JSONObject {

    val json =
        JSONObject()

    metrics.forEach { (feature, value) ->

        json.put(
            feature.name,
            finiteFloatOrNull(
                value
            )
        )
    }

    return json
}

/*
 * Saves the personal baseline values currently used
 * by the facial analyzer.
 */
private fun baselineMetricsToJson(
    metrics:
    Map<FaceBaselineFeature, BaselineMetric>
): JSONObject {

    val json =
        JSONObject()

    metrics.forEach { (feature, metric) ->

        json.put(
            feature.name,

            JSONObject()
                .put(
                    "median",
                    finiteFloatOrNull(
                        metric.median
                    )
                )
                .put(
                    "mad",
                    finiteFloatOrNull(
                        metric.mad
                    )
                )
                .put(
                    "sampleCount",
                    metric.sampleCount
                )
        )
    }

    return json
}

/*
 * JSON does not support NaN or Infinity.
 */
private fun finiteFloatOrNull(
    value: Float
): Any {

    return if (value.isFinite()) {
        value.toDouble()
    } else {
        JSONObject.NULL
    }
}

private data class ActiveFaceEvaluationWindow(

    val outputFile: File,
    val sessionId: String,
    val participantId: String,
    val windowIndex: Int,
    val scenario: String,
    val expectedContributor: FaceDistressContributor,
    val expectedLevel: Int
)

private data class StoppedFaceSession(

    val sessionId: String?,
    val participantId: String?,
    val windowCount: Int,
    val filePath: String?
)