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

class SpeechToTextManager(val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var analyzer: SpeechAudioAnalyzer? = null
    private var analysisSent = false

    fun startSpeechRecognition(
        onResult: (String) -> Unit,
        onAnalysisResult: (SpeechAnalysisResult) -> Unit,
        onFinished: () -> Unit
    ) {
        Log.d("STT", "startSpeechRecognition called")

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(
                context,
                "זיהוי דיבור לא זמין במכשיר הזה",
                Toast.LENGTH_LONG
            ).show()
            onFinished()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        analyzer = SpeechAudioAnalyzer()
        analysisSent = false

        var finished = false

        fun finishOnce() {
            if (!finished) {
                finished = true
                Log.d("STT", "Finished once")
                onFinished()
            }
        }

        fun sendAnalysisOnce() {
            if (analysisSent) {
                Log.d("STT_ANALYSIS", "Analysis already sent, skipping")
                return
            }

            analysisSent = true

            val result = analyzer?.analyze() ?: return

            Log.d("STT_ANALYSIS", result.toString())
            Log.d("STT_PAUSE", "Pause count = ${result.pauseCount}")
            Log.d("STT_PAUSE", "Pause durations ms = ${result.pauseDurationsMs}")
            Log.d("STT_PAUSE", "Average pause ms = ${result.averagePauseMs}")

            onAnalysisResult(result)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

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

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("STT", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("STT", "Beginning of speech")
                analyzer?.startSpeech()
            }

            override fun onRmsChanged(rmsdB: Float) {
                Log.d("STT", "RMS changed: $rmsdB")
                analyzer?.addRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d("STT", "Buffer received")
            }

            override fun onEndOfSpeech() {
                Log.d("STT", "End of speech")
            }

            override fun onError(error: Int) {
                Log.d("STT", "Error code: $error")
                sendAnalysisOnce()
                finishOnce()
            }

            override fun onResults(results: Bundle?) {
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                Log.d("STT", "Final text = $spokenText")

                if (!spokenText.isNullOrBlank()) {
                    val normalizedText = normalizeHebrewNumbers(spokenText)
                    analyzer?.updateFinalText(normalizedText)
                    onResult(normalizedText)
                }

                sendAnalysisOnce()
                finishOnce()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                Log.d("STT", "Partial text = $partialText")

                if (!partialText.isNullOrBlank()) {
                    val normalizedText = normalizeHebrewNumbers(partialText)
                    analyzer?.updatePartialText(normalizedText)
                    onResult(normalizedText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d("STT", "Event type: $eventType")
            }
        })

        Log.d("STT", "Calling startListening")
        speechRecognizer?.startListening(intent)
    }

    fun stopSpeechRecognition() {
        Log.d("STT", "stopSpeechRecognition called")

        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun stopAndAnalyze() {
        Log.d("STT", "Manual stop requested")
        speechRecognizer?.stopListening()
    }

    fun normalizeHebrewNumbers(text: String): String {
        val map = mapOf(
            "אפס" to "0",
            "אחד" to "1", "אחת" to "1",
            "שתיים" to "2", "שניים" to "2",
            "שלוש" to "3", "שלושה" to "3",
            "ארבע" to "4", "ארבעה" to "4",
            "חמש" to "5", "חמישה" to "5",
            "שש" to "6", "שישה" to "6",
            "שבע" to "7", "שבעה" to "7",
            "שמונה" to "8",
            "תשע" to "9", "תשעה" to "9",
            "עשר" to "10", "עשרה" to "10"
        )

        return text
            .split(" ")
            .joinToString(" ") { word ->
                map[word.trim()] ?: word
            }
    }
}