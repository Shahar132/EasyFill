package com.example.easyfill_project.texttospeech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

class TextToSpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null

    init {
        // Try to force Google TTS engine
        tts = TextToSpeech(
            context,
            { status ->
                if (status == TextToSpeech.SUCCESS) {

                    // Print which engine is actually used
                    Log.d("TTS", "Engine used: ${tts?.defaultEngine}")

                    // Try to set Hebrew
                    val result = tts?.setLanguage(Locale("he", "IL"))

                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.e("TTS", "Hebrew not supported on this device")

                        Toast.makeText(
                            context,
                            "המכשיר לא תומך בהקראת עברית. יש להפעיל Google TTS",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Log.d("TTS", "Hebrew supported ")
                    }

                } else {
                    Log.e("TTS", "TTS initialization failed")

                    Toast.makeText(
                        context,
                        "שגיאה בהפעלת הקראת טקסט",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            "com.google.android.tts" // force Google engine
        )
    }

    fun speak(text: String) {
        if (tts == null) {
            Log.e("TTS", "TTS is null")
            return
        }

        Log.d("TTS", "Speaking text: $text")

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    fun stop() {
        tts?.stop()
    }
}