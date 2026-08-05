package com.example.easyfill_project.hand_analysis

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * Stores completed hand-analysis windows for later evaluation.
 *
 * The logger does not change the hand algorithm. It only saves
 * values that were already calculated by the algorithm.
 *
 * Each line in the output file is one independent JSON object
 * representing one five-second analysis window.
 */
class HandEvaluationLogger(
    context: Context
) {

    companion object {

        private const val TAG =
            "HAND_EVALUATION"
    }

    private val stateLock =
        Any()

    private val storageRoot =
        context.getExternalFilesDir(
            Environment.DIRECTORY_DOCUMENTS
        ) ?: context.filesDir

    private val evaluationDirectory =
        File(
            storageRoot,
            "hand_evaluation"
        )

    private var currentFile: File? =
        null

    private var sessionId: String? =
        null

    private var participantId: String? =
        null

    private var scenario: String? =
        null

    private var expectedTremor: Boolean? =
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
     * Starts one controlled evaluation session.
     *
     * Starting a new session closes any session that is still
     * active so records from different scenarios are not mixed.
     */
    fun startSession(
        participantId: String,
        scenario: String,
        expectedTremor: Boolean,
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
            "Evaluation scenario must not be blank"
        }

        require(
            expectedLevel in 0..4
        ) {
            "Expected hand level must be between 0 and 4"
        }

        require(
            expectedTremor ==
                    (expectedLevel > 0)
        ) {
            "Level 0 means no tremor, and levels 1-4 mean tremor"
        }

        stopSession()

        if (
            !evaluationDirectory.exists() &&
            !evaluationDirectory.mkdirs()
        ) {
            throw IllegalStateException(
                "Could not create the hand evaluation directory"
            )
        }

        val newSessionId =
            "hand_${cleanParticipantId}_${System.currentTimeMillis()}"

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

            this.expectedTremor =
                expectedTremor

            this.expectedLevel =
                expectedLevel

            windowIndex =
                0
        }

        Log.d(
            TAG,
            """
            Hand evaluation session started.
            sessionId=$newSessionId
            participantId=$cleanParticipantId
            scenario=$cleanScenario
            expectedTremor=$expectedTremor
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
                    StoppedSession(
                        sessionId =
                            sessionId,

                        participantId =
                            participantId,

                        windowCount =
                            windowIndex,

                        filePath =
                            currentFile?.absolutePath
                    )

                currentFile =
                    null

                sessionId =
                    null

                participantId =
                    null

                scenario =
                    null

                expectedTremor =
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
                Hand evaluation session stopped.
                sessionId=${stoppedSession.sessionId}
                participantId=${stoppedSession.participantId}
                windows=${stoppedSession.windowCount}
                file=${stoppedSession.filePath}
                """.trimIndent()
            )
        }
    }

    /*
     * Appends one completed reliable five-second window.
     */
    suspend fun appendWindow(
        record: HandEvaluationRecord
    ) {

        val activeWindow =
            nextActiveWindow()
                ?: return

        val json =
            record.toJson(
                sessionId =
                    activeWindow.sessionId,

                participantId =
                    activeWindow.participantId,

                windowIndex =
                    activeWindow.windowIndex,

                scenario =
                    activeWindow.scenario,

                expectedTremor =
                    activeWindow.expectedTremor,

                expectedLevel =
                    activeWindow.expectedLevel
            )

        val writeResult =
            appendJsonLine(
                outputFile =
                    activeWindow.outputFile,

                json =
                    json
            )

        if (writeResult.isFailure) {

            Log.e(
                TAG,
                "Failed to save a hand evaluation window",
                writeResult.exceptionOrNull()
            )

            return
        }

        Log.d(
            TAG,
            """
            Hand evaluation window saved.
            sessionId=${activeWindow.sessionId}
            participantId=${activeWindow.participantId}
            windowIndex=${activeWindow.windowIndex}
            score=${record.score}
            tremorConfirmed=${record.tremorConfirmed}
            """.trimIndent()
        )
    }

    /*
     * Saves an unavailable window separately.
     *
     * The score is null rather than 0 because score 0 means
     * that valid data was analyzed and no tremor was detected.
     */
    suspend fun appendUnreliableWindow(
        durationSeconds: Double
    ) {

        val activeWindow =
            nextActiveWindow()
                ?: return

        val json =
            JSONObject()
                .put(
                    "sessionId",
                    activeWindow.sessionId
                )
                .put(
                    "participantId",
                    activeWindow.participantId
                )
                .put(
                    "windowIndex",
                    activeWindow.windowIndex
                )
                .put(
                    "timestampMs",
                    System.currentTimeMillis()
                )
                .put(
                    "modality",
                    "HAND"
                )
                .put(
                    "scenario",
                    activeWindow.scenario
                )
                .put(
                    "expectedTremor",
                    activeWindow.expectedTremor
                )
                .put(
                    "expectedLevel",
                    activeWindow.expectedLevel
                )
                .put(
                    "durationSeconds",
                    finiteValueOrNull(
                        durationSeconds
                    )
                )
                .put(
                    "isReliable",
                    false
                )
                .put(
                    "tremorConfirmed",
                    JSONObject.NULL
                )
                .put(
                    "severityIndex",
                    JSONObject.NULL
                )
                .put(
                    "score",
                    JSONObject.NULL
                )

        val writeResult =
            appendJsonLine(
                outputFile =
                    activeWindow.outputFile,

                json =
                    json
            )

        if (writeResult.isFailure) {

            Log.e(
                TAG,
                "Failed to save an unreliable hand window",
                writeResult.exceptionOrNull()
            )

            return
        }

        Log.d(
            TAG,
            """
            Unreliable hand evaluation window saved.
            sessionId=${activeWindow.sessionId}
            participantId=${activeWindow.participantId}
            windowIndex=${activeWindow.windowIndex}
            duration=$durationSeconds
            """.trimIndent()
        )
    }

    private fun nextActiveWindow():
            ActiveEvaluationWindow? {

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

            val activeExpectedTremor =
                expectedTremor
                    ?: return@synchronized null

            val activeExpectedLevel =
                expectedLevel
                    ?: return@synchronized null

            windowIndex +=
                1

            ActiveEvaluationWindow(
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

                expectedTremor =
                    activeExpectedTremor,

                expectedLevel =
                    activeExpectedLevel
            )
        }
    }

    private suspend fun appendJsonLine(
        outputFile: File,
        json: JSONObject
    ): Result<Unit> {

        return withContext(
            Dispatchers.IO
        ) {

            runCatching {

                outputFile.appendText(
                    text =
                        json.toString() + "\n",

                    charset =
                        Charsets.UTF_8
                )
            }
        }
    }

    private fun finiteValueOrNull(
        value: Double
    ): Any {

        return if (value.isFinite()) {
            value
        } else {
            JSONObject.NULL
        }
    }
}

/*
 * Contains the values calculated for one completed reliable
 * five-second hand-analysis window.
 */
data class HandEvaluationRecord(

    val timestampMs: Long =
        System.currentTimeMillis(),

    val durationSeconds: Double,
    val isReliable: Boolean,

    val accelerationP95: Double,
    val accelerationVariation: Double,

    val gyroscopeP95: Double,
    val gyroscopeVariation: Double,

    val peakFrequencyHz: Double,
    val concentrationRatio: Double,
    val narrowbandRatio: Double,
    val rhythmicEnergyShare: Double,

    val bandAveragePower: Double,
    val peakNeighborhoodPower: Double,

    val wholePeakInBand: Boolean,
    val wholeConcentrated: Boolean,
    val wholeNarrowband: Boolean,
    val wholeRhythmic: Boolean,
    val wholePowerHigh: Boolean,

    val personalRhythmicThreshold: Double,

    val candidateWindowCount: Int,
    val hasTemporalCoverage: Boolean,
    val frequencyStable: Boolean,
    val powerStable: Boolean,
    val isBurstDominated: Boolean,

    val candidateFrequencySpreadHz: Double,
    val candidatePowerRatio: Double,
    val candidatePowerCoefficientOfVariation: Double,

    val temporalPeakFrequenciesHz: List<Double>,
    val temporalConcentrations: List<Double>,
    val temporalNarrowbandRatios: List<Double>,
    val temporalRhythmicShares: List<Double>,
    val temporalBandPowers: List<Double>,
    val temporalPeakNeighborhoodPowers: List<Double>,

    val upperAccelerationP95: Double,
    val upperAccelerationVariation: Double,

    val upperGyroscopeP95: Double,
    val upperGyroscopeVariation: Double,

    val upperBandPower: Double,
    val upperPeakPower: Double,

    val accelerationLevel: Double,
    val gyroscopeLevel: Double,
    val spectralLevel: Double,

    val tremorConfirmed: Boolean,
    val severityIndex: Double,
    val score: Int
) {

    fun toJson(
        sessionId: String,
        participantId: String,
        windowIndex: Int,
        scenario: String,
        expectedTremor: Boolean,
        expectedLevel: Int
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
                timestampMs
            )
            .put(
                "modality",
                "HAND"
            )
            .put(
                "scenario",
                scenario
            )
            .put(
                "expectedTremor",
                expectedTremor
            )
            .put(
                "expectedLevel",
                expectedLevel
            )
            .put(
                "durationSeconds",
                finiteValueOrNull(
                    durationSeconds
                )
            )
            .put(
                "isReliable",
                isReliable
            )
            .put(
                "accelerationP95",
                finiteValueOrNull(
                    accelerationP95
                )
            )
            .put(
                "accelerationVariation",
                finiteValueOrNull(
                    accelerationVariation
                )
            )
            .put(
                "gyroscopeP95",
                finiteValueOrNull(
                    gyroscopeP95
                )
            )
            .put(
                "gyroscopeVariation",
                finiteValueOrNull(
                    gyroscopeVariation
                )
            )
            .put(
                "peakFrequencyHz",
                finiteValueOrNull(
                    peakFrequencyHz
                )
            )
            .put(
                "concentrationRatio",
                finiteValueOrNull(
                    concentrationRatio
                )
            )
            .put(
                "narrowbandRatio",
                finiteValueOrNull(
                    narrowbandRatio
                )
            )
            .put(
                "rhythmicEnergyShare",
                finiteValueOrNull(
                    rhythmicEnergyShare
                )
            )
            .put(
                "bandAveragePower",
                finiteValueOrNull(
                    bandAveragePower
                )
            )
            .put(
                "peakNeighborhoodPower",
                finiteValueOrNull(
                    peakNeighborhoodPower
                )
            )
            .put(
                "wholePeakInBand",
                wholePeakInBand
            )
            .put(
                "wholeConcentrated",
                wholeConcentrated
            )
            .put(
                "wholeNarrowband",
                wholeNarrowband
            )
            .put(
                "wholeRhythmic",
                wholeRhythmic
            )
            .put(
                "wholePowerHigh",
                wholePowerHigh
            )
            .put(
                "personalRhythmicThreshold",
                finiteValueOrNull(
                    personalRhythmicThreshold
                )
            )
            .put(
                "candidateWindowCount",
                candidateWindowCount
            )
            .put(
                "hasTemporalCoverage",
                hasTemporalCoverage
            )
            .put(
                "frequencyStable",
                frequencyStable
            )
            .put(
                "powerStable",
                powerStable
            )
            .put(
                "isBurstDominated",
                isBurstDominated
            )
            .put(
                "candidateFrequencySpreadHz",
                finiteValueOrNull(
                    candidateFrequencySpreadHz
                )
            )
            .put(
                "candidatePowerRatio",
                finiteValueOrNull(
                    candidatePowerRatio
                )
            )
            .put(
                "candidatePowerCoefficientOfVariation",
                finiteValueOrNull(
                    candidatePowerCoefficientOfVariation
                )
            )
            .put(
                "temporalPeakFrequenciesHz",
                doubleListToJsonArray(
                    temporalPeakFrequenciesHz
                )
            )
            .put(
                "temporalConcentrations",
                doubleListToJsonArray(
                    temporalConcentrations
                )
            )
            .put(
                "temporalNarrowbandRatios",
                doubleListToJsonArray(
                    temporalNarrowbandRatios
                )
            )
            .put(
                "temporalRhythmicShares",
                doubleListToJsonArray(
                    temporalRhythmicShares
                )
            )
            .put(
                "temporalBandPowers",
                doubleListToJsonArray(
                    temporalBandPowers
                )
            )
            .put(
                "temporalPeakNeighborhoodPowers",
                doubleListToJsonArray(
                    temporalPeakNeighborhoodPowers
                )
            )
            .put(
                "upperAccelerationP95",
                finiteValueOrNull(
                    upperAccelerationP95
                )
            )
            .put(
                "upperAccelerationVariation",
                finiteValueOrNull(
                    upperAccelerationVariation
                )
            )
            .put(
                "upperGyroscopeP95",
                finiteValueOrNull(
                    upperGyroscopeP95
                )
            )
            .put(
                "upperGyroscopeVariation",
                finiteValueOrNull(
                    upperGyroscopeVariation
                )
            )
            .put(
                "upperBandPower",
                finiteValueOrNull(
                    upperBandPower
                )
            )
            .put(
                "upperPeakPower",
                finiteValueOrNull(
                    upperPeakPower
                )
            )
            .put(
                "accelerationLevel",
                finiteValueOrNull(
                    accelerationLevel
                )
            )
            .put(
                "gyroscopeLevel",
                finiteValueOrNull(
                    gyroscopeLevel
                )
            )
            .put(
                "spectralLevel",
                finiteValueOrNull(
                    spectralLevel
                )
            )
            .put(
                "tremorConfirmed",
                tremorConfirmed
            )
            .put(
                "severityIndex",
                finiteValueOrNull(
                    severityIndex
                )
            )
            .put(
                "score",
                score
            )
    }

    private fun doubleListToJsonArray(
        values: List<Double>
    ): JSONArray {

        val array =
            JSONArray()

        values.forEach { value ->

            array.put(
                finiteValueOrNull(
                    value
                )
            )
        }

        return array
    }

    /*
     * JSON does not support Infinity or NaN.
     */
    private fun finiteValueOrNull(
        value: Double
    ): Any {

        return if (value.isFinite()) {
            value
        } else {
            JSONObject.NULL
        }
    }
}

private data class ActiveEvaluationWindow(

    val outputFile: File,
    val sessionId: String,
    val participantId: String,
    val windowIndex: Int,
    val scenario: String,
    val expectedTremor: Boolean,
    val expectedLevel: Int
)

private data class StoppedSession(

    val sessionId: String?,
    val participantId: String?,
    val windowCount: Int,
    val filePath: String?
)