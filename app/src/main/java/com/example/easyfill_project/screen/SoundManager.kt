package com.example.easyfill_project.screen

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SoundManager {

    /**
     * The sound that is currently active in the app.
     *
     * This is effective runtime state. It may represent either:
     *
     * 1. The user's saved preference.
     * 2. A temporary chatbot adaptation.
     */
    var selectedSound by mutableStateOf("none")
        private set

    private var mediaPlayer: MediaPlayer? = null

    fun play(
        context: Context,
        soundName: String,
        soundRes: Int
    ) {
        /*
         * Avoid restarting the same sound whenever
         * Compose recomposes or DataStore emits again.
         */
        if (
            selectedSound == soundName &&
            mediaPlayer?.isPlaying == true
        ) {
            return
        }

        stopOnlySound()

        selectedSound = soundName

        mediaPlayer =
            MediaPlayer.create(
                context.applicationContext,
                soundRes
            )

        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    fun stop() {
        stopOnlySound()
        selectedSound = "none"
    }

    private fun stopOnlySound() {
        mediaPlayer?.run {
            try {
                if (isPlaying) {
                    stop()
                }
            } catch (_: IllegalStateException) {
                // Player was already in an invalid state.
            }
            release()
        }

        mediaPlayer = null
    }
}