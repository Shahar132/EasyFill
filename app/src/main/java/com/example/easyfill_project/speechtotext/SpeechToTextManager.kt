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
}