package com.example.easyfill_project.screen

import android.content.Context
import android.media.MediaPlayer

object SoundManager {

    var selectedSound: String = "none"
    private var mediaPlayer: MediaPlayer? = null

    fun play(context: Context, soundName: String, soundRes: Int) {
        stopOnlySound()

        selectedSound = soundName

        mediaPlayer = MediaPlayer.create(context, soundRes)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    fun stop() {
        stopOnlySound()
        selectedSound = "none"
    }

    private fun stopOnlySound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}