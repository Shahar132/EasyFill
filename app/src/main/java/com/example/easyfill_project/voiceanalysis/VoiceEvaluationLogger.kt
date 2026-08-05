package com.example.easyfill_project.voiceanalysis

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Writes controlled voice-evaluation recordings as JSONL.
 *
 * One completed voice recording produces one JSON object and one line.
 * The transcript itself is intentionally not stored. Only the word count
 * and acoustic/statistical measurements are written to the evaluation file.
 */
object VoiceEvaluationLogger {

    private const val TAG = "VOICE_EVALUATION"
    private const val MODALITY = "VOICE"

    /*
     * This mirrors the constant currently used by SpeechRateScorer.
     * It is stored only for evaluation diagnostics and does not change
     * the production score.
     */
    private const val HEBREW_NORMAL_SPEECH_RATE = 2.57
    private const val RMS_MILD_FACTOR = 1.5
    private const val RMS_HIGH_FACTOR = 2.0

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private val writeMutex = Mutex()
    private val sessionLock = Any()
    private val recordingIndex = AtomicInteger(0)

    @Volatile
    private var activeSession: VoiceEvaluationSession? = null

    data class BaselineSnapshot(
        val validSpeechSeconds: Double?,
        val analysisDurationSeconds: Double?,
        val speechRateWordsPerSecond: Double,
        val analysisSpeechRateWordsPerSecond: Double?,
        val averageRms: Double?,
        val maxRms: Double?,
        val rmsVariation: Double,
        val pauseCount: Long?,
        val pauseDurationsMs: List<Long>,
        val averagePauseMs: Double?,
        val hesitationCount: Long?,
        val createdAt: Long?
    )

    private data class VoiceEvaluationSession(
        val sessionId: String,
        val participantId: String,
        val scenario: String,
        val expectedLevel: Int,
        val expectedDistress: Boolean,
        val startedAtMs: Long,
        val outputFile: File
    )

    /**
     * Opens one controlled evaluation session.
     *
     * A session may contain one or more completed recordings. Each recording
     * is written as a separate JSONL row.
     */
    fun startSession(
        context: Context,
        participantId: String,
        scenario: String,
        expectedLevel: Int
    ): File {

        val safeParticipantId =
            sanitizeIdentifier(
                participantId
            )

        require(
            safeParticipantId.isNotBlank()
        ) {
            "Participant ID must not be blank"
        }

        require(
            expectedLevel in 0..4
        ) {
            "Expected voice level must be between 0 and 4"
        }

        val safeScenario =
            sanitizeIdentifier(
                scenario
            ).ifBlank {
                "unspecified"
            }

        synchronized(sessionLock) {

            check(activeSession == null) {
                "A voice evaluation session is already active"
            }

            val startedAtMs =
                System.currentTimeMillis()

            val documentsDirectory =
                context.getExternalFilesDir(
                    Environment.DIRECTORY_DOCUMENTS
                ) ?: context.filesDir

            val evaluationDirectory =
                File(
                    documentsDirectory,
                    "voice_evaluation"
                )

            if (
                !evaluationDirectory.exists() &&
                !evaluationDirectory.mkdirs()
            ) {
                throw IllegalStateException(
                    "Could not create voice evaluation directory"
                )
            }

            val outputFile =
                File(
                    evaluationDirectory,
                    "voice_${safeParticipantId}_${startedAtMs}.jsonl"
                )

            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }

            activeSession =
                VoiceEvaluationSession(
                    sessionId =
                        "voice_${safeParticipantId}_${startedAtMs}",
                    participantId =
                        safeParticipantId,
                    scenario =
                        safeScenario,
                    expectedLevel =
                        expectedLevel,
                    expectedDistress =
                        expectedLevel > 0,
                    startedAtMs =
                        startedAtMs,
                    outputFile =
                        outputFile
                )

            recordingIndex.set(0)

            Log.d(
                TAG,
                "Voice evaluation started | " +
                        "file=${outputFile.absolutePath}"
            )

            return outputFile
        }
    }

    /**
     * Closes the current session. Pending background writes keep their
     * own immutable session snapshot and can still finish safely.
     */
    fun stopSession(): File? {

        val stoppedSession =
            synchronized(sessionLock) {

                val session =
                    activeSession

                activeSession = null

                session
            }

        if (stoppedSession != null) {
            Log.d(
                TAG,
                "Voice evaluation stopped | " +
                        "file=${stoppedSession.outputFile.absolutePath}"
            )
        }

        return stoppedSession
            ?.outputFile
    }

    fun isSessionActive(): Boolean =
        activeSession != null

    /**
     * Writes a reliable recording for which both voice scorers completed.
     */
    fun appendScoredRecording(
        fieldId: String,
        recordingStartedAtMs: Long?,
        analysis: SpeechAnalysisResult,
        baseline: BaselineSnapshot,
        speechRateScore: Int,
        rmsScore: Int,
        voiceScore: Int
    ) {

        val session =
            activeSession ?: return

        val currentRecordingIndex =
            recordingIndex.incrementAndGet()

        val timestampMs =
            System.currentTimeMillis()

        val currentSpeechRate =
            analysis.speechRateWordsPerSecond

        val baselineSpeechRate =
            baseline.speechRateWordsPerSecond

        val baselineSpeechRateDeviation =
            relativeDeviation(
                currentValue =
                    currentSpeechRate,
                baselineValue =
                    baselineSpeechRate
            )

        val normalHebrewRateDeviation =
            relativeDeviation(
                currentValue =
                    currentSpeechRate,
                baselineValue =
                    HEBREW_NORMAL_SPEECH_RATE
            )

        val weightedSpeechRateDeviation =
            if (
                baselineSpeechRateDeviation != null &&
                normalHebrewRateDeviation != null
            ) {
                0.7 * baselineSpeechRateDeviation +
                        0.3 * normalHebrewRateDeviation
            } else {
                null
            }

        val rmsVariationRatio =
            if (baseline.rmsVariation > 0.0) {
                analysis.rmsVariation.toDouble() /
                        baseline.rmsVariation
            } else {
                null
            }

        val json =
            createBaseJson(
                session =
                    session,
                recordingIndex =
                    currentRecordingIndex,
                timestampMs =
                    timestampMs,
                fieldId =
                    fieldId,
                recordingStartedAtMs =
                    recordingStartedAtMs,
                resultType =
                    "SCORED"
            )

        putAnalysis(
            target =
                json,
            analysis =
                analysis
        )

        json.put(
            "baseline",
            baselineToJson(
                baseline
            )
        )

        json.put(
            "calculations",
            JSONObject().apply {
                putNullable(
                    "baselineSpeechRateDeviation",
                    baselineSpeechRateDeviation
                )

                put(
                    "normalHebrewSpeechRate",
                    HEBREW_NORMAL_SPEECH_RATE
                )

                putNullable(
                    "normalHebrewRateDeviation",
                    normalHebrewRateDeviation
                )

                putNullable(
                    "weightedSpeechRateDeviation",
                    weightedSpeechRateDeviation
                )

                put(
                    "speechRateDirection",
                    speechRateDirection(
                        currentSpeechRate =
                            currentSpeechRate,
                        baselineSpeechRate =
                            baselineSpeechRate
                    )
                )

                putNullable(
                    "rmsVariationRatio",
                    rmsVariationRatio
                )

                put(
                    "rmsMildThreshold",
                    baseline.rmsVariation *
                            RMS_MILD_FACTOR
                )

                put(
                    "rmsHighThreshold",
                    baseline.rmsVariation *
                            RMS_HIGH_FACTOR
                )
            }
        )

        json.put(
            "scores",
            JSONObject().apply {
                put(
                    "speechRateScore",
                    speechRateScore
                )

                put(
                    "rmsScore",
                    rmsScore
                )

                put(
                    "voiceScore",
                    voiceScore
                )

                put(
                    "absoluteLevelError",
                    abs(
                        voiceScore -
                                session.expectedLevel
                    )
                )

                put(
                    "matchesExpectedLevel",
                    voiceScore ==
                            session.expectedLevel
                )
            }
        )

        json.put(
            "failureReason",
            JSONObject.NULL
        )

        appendJsonLine(
            session =
                session,
            json =
                json
        )
    }

    /**
     * Writes an unavailable or unreliable recording so failed attempts do not
     * silently disappear from the evaluation dataset.
     */
    fun appendUnavailableRecording(
        fieldId: String,
        recordingStartedAtMs: Long?,
        analysis: SpeechAnalysisResult?,
        failureReason: String,
        baseline: BaselineSnapshot? = null
    ) {

        val session =
            activeSession ?: return

        val currentRecordingIndex =
            recordingIndex.incrementAndGet()

        val timestampMs =
            System.currentTimeMillis()

        val json =
            createBaseJson(
                session =
                    session,
                recordingIndex =
                    currentRecordingIndex,
                timestampMs =
                    timestampMs,
                fieldId =
                    fieldId,
                recordingStartedAtMs =
                    recordingStartedAtMs,
                resultType =
                    "UNAVAILABLE"
            )

        if (analysis != null) {
            putAnalysis(
                target =
                    json,
                analysis =
                    analysis
            )
        } else {
            json.put(
                "analysis",
                JSONObject.NULL
            )
        }

        json.put(
            "baseline",
            baseline?.let {
                baselineToJson(
                    it
                )
            } ?: JSONObject.NULL
        )

        json.put(
            "calculations",
            JSONObject.NULL
        )

        json.put(
            "scores",
            JSONObject().apply {
                put(
                    "speechRateScore",
                    JSONObject.NULL
                )

                put(
                    "rmsScore",
                    JSONObject.NULL
                )

                put(
                    "voiceScore",
                    JSONObject.NULL
                )
            }
        )

        json.put(
            "failureReason",
            failureReason
        )

        appendJsonLine(
            session =
                session,
            json =
                json
        )
    }

    private fun createBaseJson(
        session: VoiceEvaluationSession,
        recordingIndex: Int,
        timestampMs: Long,
        fieldId: String,
        recordingStartedAtMs: Long?,
        resultType: String
    ): JSONObject =
        JSONObject().apply {

            put(
                "sessionId",
                session.sessionId
            )

            put(
                "participantId",
                session.participantId
            )

            put(
                "recordingIndex",
                recordingIndex
            )

            put(
                "timestampMs",
                timestampMs
            )

            put(
                "modality",
                MODALITY
            )

            put(
                "scenario",
                session.scenario
            )

            put(
                "expectedDistress",
                session.expectedDistress
            )

            put(
                "expectedLevel",
                session.expectedLevel
            )

            put(
                "resultType",
                resultType
            )

            put(
                "fieldId",
                fieldId
            )

            put(
                "evaluationSessionStartedAtMs",
                session.startedAtMs
            )

            putNullable(
                "recordingStartedAtMs",
                recordingStartedAtMs
            )

            putNullable(
                "evaluationRecordingDurationMs",
                recordingStartedAtMs?.let {
                    (timestampMs - it)
                        .coerceAtLeast(0L)
                }
            )
        }

    private fun putAnalysis(
        target: JSONObject,
        analysis: SpeechAnalysisResult
    ) {

        val words =
            analysis.finalText
                .trim()
                .split(
                    "\\s+".toRegex()
                )
                .filter {
                    it.isNotBlank()
                }

        target.put(
            "analysis",
            JSONObject().apply {
                put(
                    "durationSeconds",
                    analysis.durationSeconds
                )

                put(
                    "isReliable",
                    analysis.isReliable
                )

                put(
                    "recognizedWordCount",
                    words.size
                )

                put(
                    "recognizedCharacterCount",
                    analysis.finalText.length
                )

                put(
                    "transcriptStored",
                    false
                )

                put(
                    "speechRateWordsPerSecond",
                    analysis.speechRateWordsPerSecond
                )

                put(
                    "averageRms",
                    analysis.averageRms.toDouble()
                )

                put(
                    "maxRms",
                    analysis.maxRms.toDouble()
                )

                put(
                    "rmsVariation",
                    analysis.rmsVariation.toDouble()
                )

                put(
                    "pauseCount",
                    analysis.pauseCount
                )

                put(
                    "pauseDurationsMs",
                    JSONArray(
                        analysis.pauseDurationsMs
                    )
                )

                put(
                    "averagePauseMs",
                    analysis.averagePauseMs
                )

                put(
                    "hesitationCount",
                    analysis.hesitationCount
                )
            }
        )
    }

    private fun baselineToJson(
        baseline: BaselineSnapshot
    ): JSONObject =
        JSONObject().apply {

            putNullable(
                "validSpeechSeconds",
                baseline.validSpeechSeconds
            )

            putNullable(
                "analysisDurationSeconds",
                baseline.analysisDurationSeconds
            )

            put(
                "speechRateWordsPerSecond",
                baseline.speechRateWordsPerSecond
            )

            putNullable(
                "analysisSpeechRateWordsPerSecond",
                baseline.analysisSpeechRateWordsPerSecond
            )

            putNullable(
                "averageRms",
                baseline.averageRms
            )

            putNullable(
                "maxRms",
                baseline.maxRms
            )

            put(
                "rmsVariation",
                baseline.rmsVariation
            )

            putNullable(
                "pauseCount",
                baseline.pauseCount
            )

            put(
                "pauseDurationsMs",
                JSONArray(
                    baseline.pauseDurationsMs
                )
            )

            putNullable(
                "averagePauseMs",
                baseline.averagePauseMs
            )

            putNullable(
                "hesitationCount",
                baseline.hesitationCount
            )

            putNullable(
                "createdAt",
                baseline.createdAt
            )
        }

    private fun appendJsonLine(
        session: VoiceEvaluationSession,
        json: JSONObject
    ) {

        scope.launch {

            writeMutex.withLock {

                runCatching {
                    session.outputFile.appendText(
                        json.toString() +
                                System.lineSeparator()
                    )
                }
                    .onSuccess {
                        Log.d(
                            TAG,
                            "Voice evaluation row saved | " +
                                    "recordingIndex=${json.optInt("recordingIndex")}"
                        )
                    }
                    .onFailure { error ->
                        Log.e(
                            TAG,
                            "Failed writing voice evaluation row",
                            error
                        )
                    }
            }
        }
    }

    private fun relativeDeviation(
        currentValue: Double,
        baselineValue: Double
    ): Double? {

        if (
            currentValue <= 0.0 ||
            baselineValue <= 0.0
        ) {
            return null
        }

        return abs(
            currentValue -
                    baselineValue
        ) / baselineValue
    }

    private fun speechRateDirection(
        currentSpeechRate: Double,
        baselineSpeechRate: Double
    ): String =
        when {
            currentSpeechRate >
                    baselineSpeechRate ->
                "FASTER"

            currentSpeechRate <
                    baselineSpeechRate ->
                "SLOWER"

            else ->
                "UNCHANGED"
        }

    private fun sanitizeIdentifier(
        value: String
    ): String =
        value
            .trim()
            .replace(
                Regex(
                    "[^A-Za-z0-9_-]"
                ),
                "_"
            )
            .take(40)

    private fun JSONObject.putNullable(
        key: String,
        value: Any?
    ) {
        put(
            key,
            value ?: JSONObject.NULL
        )
    }
}