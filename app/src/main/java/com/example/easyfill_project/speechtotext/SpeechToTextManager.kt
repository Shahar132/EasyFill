package com.example.easyfill_project.speechtotext

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher

class SpeechToTextManager(private val context: Context) {

    fun startSpeechRecognition(
        speechLauncher: ActivityResultLauncher<Intent>
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "דברו עכשיו")
        }

        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "זיהוי דיבור לא זמין במכשיר הזה",
                Toast.LENGTH_LONG
            ).show()
        }
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
            .joinToString("") { word ->
                map[word.trim()] ?: word
            }
    }
}