package com.example.easyfill_project.screen

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SoundManager {

    // Compose observes this value.
    //
    // "none" means no background sound is currently playing.
    // Otherwise it contains the sound key, for example "calm".
    var selectedSound by mutableStateOf("none")
        private set

    private var mediaPlayer: MediaPlayer? = null

    fun play(
        context: Context,
        soundName: String,
        soundRes: Int
    ) {
        // Stops the previous sound before starting another one.
        stopOnlySound()

        // Saves the key of the new sound.
        // Compose immediately notices this change.
        selectedSound = soundName

        mediaPlayer = MediaPlayer.create(context, soundRes)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    fun stop() {
        // Stops and releases the current sound.
        stopOnlySound()

        // Updates the state to show that no sound is playing.
        selectedSound = "none"
    }

    private fun stopOnlySound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}