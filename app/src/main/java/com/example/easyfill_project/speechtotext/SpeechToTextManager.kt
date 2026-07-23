package com.example.easyfill_project.speechtotext

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import com.example.easyfill_project.voiceanalysis.SpeechAnalysisResult
import com.example.easyfill_project.voiceanalysis.SpeechAudioAnalyzer

class SpeechToTextManager(
    private val context: Context
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var analyzer: SpeechAudioAnalyzer? = null

    private var analysisSent = false

    /*
 * Callbacks belonging to the currently active recording.
 *
 * They allow stopAndAnalyze() to finish safely even when
 * SpeechRecognizer cannot deliver a final callback.
 */
    private var activeSendAnalysis: (() -> Unit)? =
        null

    private var activeFinish: (() -> Unit)? =
        null

    private var activeFailure: (() -> Unit)? =
        null

    fun startSpeechRecognition(
        onResult: (String) -> Unit,
        onSpeechStarted: () -> Unit,
        onAnalysisResult: (SpeechAnalysisResult) -> Unit,
        onFailure: () -> Unit,
        onFinished: () -> Unit
    ){
        Log.d("STT", "startSpeechRecognition called")

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(
                context,
                "זיהוי דיבור לא זמין במכשיר הזה",
                Toast.LENGTH_LONG
            ).show()

            onFailure()
            onFinished()
            return
        }

//        speechRecognizer?.destroy()
//        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        analyzer = SpeechAudioAnalyzer()
        analysisSent = false

        var finished = false

        fun finishOnce() {

            if (finished) {
                return
            }

            finished = true

            Log.d(
                "STT",
                "Finished once"
            )

            onFinished()

            /*
             * The recording has completed, so these callbacks must
             * not be reused by a later manual-stop request.
             */
            activeSendAnalysis = null
            activeFinish = null
            activeFailure = null
        }

        fun sendAnalysisOnce() {
            if (analysisSent) {
                Log.d(
                    "STT_ANALYSIS",
                    "Analysis already sent, skipping"
                )
                return
            }

            val currentAnalyzer = analyzer ?: run {

                /*
                 * Voice analysis cannot be produced because the analyzer
                 * is unexpectedly unavailable.
                 *
                 * Notify the caller so SmartTextField can submit a null
                 * voice result and allow face and hand analysis to continue.
                 */
                Log.e(
                    "STT_ANALYSIS",
                    "Analyzer is null. Voice analysis cannot be produced."
                )

                /*
                 * Mark this attempt as handled so another callback does
                 * not try to send the same analysis again.
                 */
                analysisSent = true

                onFailure()

                return
            }

            analysisSent = true

            val result = currentAnalyzer.analyze()

            Log.d(
                "STT_ANALYSIS",
                result.toString()
            )

            Log.d(
                "STT_PAUSE",
                "Pause count = ${result.pauseCount}"
            )

            Log.d(
                "STT_PAUSE",
                "Pause durations ms = ${result.pauseDurationsMs}"
            )

            Log.d(
                "STT_PAUSE",
                "Average pause ms = ${result.averagePauseMs}"
            )

            Log.d(
                "STT_RELIABILITY",
                "Duration = ${result.durationSeconds}, " +
                        "isReliable = ${result.isReliable}"
            )

            onAnalysisResult(result)
        }


        /*
 * Save the current recording callbacks so manual stopping
 * can safely complete the session if Android does not return
 * onResults() or onError().
 */
        activeSendAnalysis = {
            sendAnalysisOnce()
        }

        activeFinish = {
            finishOnce()
        }

        activeFailure = {
            onFailure()
        }

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "he-IL"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1500
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1000
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    3000
                )
            }

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    Log.d(
                        "STT",
                        "Ready for speech"
                    )
                }

                /*
                 * Android detected actual speech.
                 *
                 * SmartTextField should call:
                 *
                 * speechToTextManager.markReliableSpeechStart()
                 *
                 * from the onSpeechStarted callback.
                 */
                override fun onBeginningOfSpeech() {
                    Log.d(
                        "STT",
                        "Beginning of speech"
                    )

                    onSpeechStarted()
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                    Log.d(
                        "STT",
                        "RMS changed: $rmsdB"
                    )

                    analyzer?.addRms(rmsdB)
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                    Log.d(
                        "STT",
                        "Buffer received"
                    )
                }

                /*
                 * Do not send the analysis here.
                 *
                 * Android often calls onEndOfSpeech before
                 * onResults. We wait for onResults so the final
                 * recognized text is included in the analysis.
                 */
                override fun onEndOfSpeech() {
                    Log.d(
                        "STT",
                        "End of speech"
                    )

                    analyzer?.stopSpeech()
                }

                override fun onError(error: Int) {

                    Log.d(
                        "STT",
                        "Error code: $error"
                    )

                    analyzer?.stopSpeech()
                    sendAnalysisOnce()
                    finishOnce()
                }

                override fun onResults(
                    results: Bundle?
                ) {
                    val spokenText =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                    Log.d(
                        "STT",
                        "Final text = $spokenText"
                    )

                    if (!spokenText.isNullOrBlank()) {
                        val normalizedText =
                            normalizeHebrewNumbers(
                                spokenText
                            )

                        analyzer?.updateFinalText(
                            normalizedText
                        )

                        onResult(
                            normalizedText
                        )
                    }

                    /*
                     * Make sure the end time is available even if
                     * Android did not call onEndOfSpeech.
                     */
                    analyzer?.stopSpeech()

                    sendAnalysisOnce()
                    finishOnce()
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    val partialText =
                        partialResults
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                    Log.d(
                        "STT",
                        "Partial text = $partialText"
                    )

                    if (!partialText.isNullOrBlank()) {
                        val normalizedText =
                            normalizeHebrewNumbers(
                                partialText
                            )

                        analyzer?.updatePartialText(
                            normalizedText
                        )

                        onResult(
                            normalizedText
                        )
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                    Log.d(
                        "STT",
                        "Event type: $eventType"
                    )
                }
            }
        )

        Log.d(
            "STT",
            "Calling startListening"
        )

        try {

            speechRecognizer?.startListening(
                intent
            )

        } catch (error: Exception) {

            Log.e(
                "STT",
                "Failed to start speech recognition.",
                error
            )

            /*
             * Voice recording could not begin.
             *
             * SmartTextField will submit a null voice result.
             */
            onFailure()

            /*
             * Close the recording UI and request completion of
             * the hand and face recording components.
             */
            finishOnce()
        }
    }

    fun stopSpeechRecognition() {
        Log.d(
            "STT",
            "stopSpeechRecognition called"
        )

        analyzer?.stopSpeech()

        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        activeSendAnalysis = null
        activeFinish = null
        activeFailure = null
    }

    fun stopAndAnalyze() {

        Log.d(
            "STT",
            "Manual stop requested"
        )

        analyzer?.stopSpeech()

        val currentRecognizer =
            speechRecognizer

        if (currentRecognizer != null) {

            /*
             * Android should now invoke onResults() or onError().
             *
             * Those callbacks will send analysis and call finishOnce().
             */
            currentRecognizer.stopListening()

        } else {

            /*
             * No recognizer exists, so Android cannot provide another
             * callback.
             *
             * Try to finish the locally collected audio analysis.
             */
            Log.e(
                "STT",
                "Manual stop requested but SpeechRecognizer is null."
            )

            val sendAnalysis =
                activeSendAnalysis

            if (sendAnalysis != null) {
                sendAnalysis()
            } else {
                activeFailure?.invoke()
            }

            activeFinish?.invoke()
        }
    }

    fun normalizeHebrewNumbers(
        text: String
    ): String {
        val map =
            mapOf(
                "אפס" to "0",

                "אחד" to "1",
                "אחת" to "1",

                "שתיים" to "2",
                "שניים" to "2",

                "שלוש" to "3",
                "שלושה" to "3",

                "ארבע" to "4",
                "ארבעה" to "4",

                "חמש" to "5",
                "חמישה" to "5",

                "שש" to "6",
                "שישה" to "6",

                "שבע" to "7",
                "שבעה" to "7",

                "שמונה" to "8",

                "תשע" to "9",
                "תשעה" to "9",

                "עשר" to "10",
                "עשרה" to "10"
            )

        return text
            .split(" ")
            .joinToString(" ") { word ->
                map[word.trim()] ?: word
            }
    }

    /*
     * Call this when Android invokes onBeginningOfSpeech().
     *
     * The 10-second duration starts when actual speech begins,
     * not when the microphone button is pressed.
     */
    fun markReliableSpeechStart() {
        analyzer?.startSpeech()
    }
}