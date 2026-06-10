package com.example.easyfill_project.voiceanalysis

// Android permission constants (RECORD_AUDIO)
import android.Manifest
// Needed for checking permissions
import android.content.Context
import android.content.pm.PackageManager
// Audio APIs
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
// For log messages in Logcat
import android.util.Log
// Permission helper
import androidx.core.content.ContextCompat
// Coroutines - lets us read audio in background
import kotlinx.coroutines.*
class AudioRecordManager(
    // Context is needed for checking microphone permission
    private val context: Context

) {
    //flag
    private var isRecording = false

    // The object that actually records audio from the microphone
    private var audioRecord: AudioRecord? = null
    // Background coroutine that continuously reads audio
    private var recordingJob: Job? = null
    // Audio sample rate
    // 16000Hz is common for speech processing
    private val sampleRate = 16000
    // Mono = 1 microphone channel
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    // 16-bit PCM audio
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    /**
     * Starts microphone recording
     *
     * onVolumeDb:
     * callback that returns the current volume in dB
     */
    fun startRecording(
        onVolumeDb: (Double) -> Unit
    ) {
        if (isRecording) return
        // Check if microphone permission exists

        Log.d(
            "AudioRecordManager",
            "Permission = ${
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                )
            }"
        )


        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e(
                "AudioRecordManager",
                "Missing RECORD_AUDIO permission"
            )

            return
        }

        // Android calculates the minimum safe buffer size
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        if (minBufferSize <= 0) {
            Log.e("AudioRecordManager", "Invalid buffer size")
            return
        }

        // Create microphone recorder
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, // microphone
            sampleRate,                    // 16000Hz
            channelConfig,                 // mono
            audioFormat,                   // PCM 16 bit
            minBufferSize                  // buffer size
        )

        // Buffer that receives raw audio samples
        val buffer = ShortArray(minBufferSize)

        // Start recording from microphone
        audioRecord?.startRecording()
        isRecording = true

        Log.d(
            "AudioRecordManager",
            "Recording started"
        )

        // Run recording loop in background thread
        recordingJob = CoroutineScope(
            Dispatchers.Default
        ).launch {

            // Keep recording until stopRecording() is called
            while (isActive) {

                // Read audio samples from microphone
                val read = audioRecord?.read(
                    buffer,
                    0,
                    buffer.size
                ) ?: 0

                if (read > 0) {

                    // Calculate RMS (average signal strength)
                    val rms =
                        VolumeAnalyzer.calculateRms(
                            buffer,
                            read
                        )

                    // Convert RMS -> dB
                    val db =
                        VolumeAnalyzer.rmsToDb(rms)

                    // Return value to UI thread
                    withContext(Dispatchers.Main) {

                        // Example:
                        // -45 dB = quiet
                        // -20 dB = louder
                        onVolumeDb(db)
                    }
                }
            }
        }
    }

    /**
     * Stops microphone recording
     */
    fun stopRecording() {

        if (!isRecording) return

        isRecording = false

        // Stop coroutine loop
        recordingJob?.cancel()
        recordingJob = null

        try {

            // Stop microphone
            audioRecord?.stop()

        } catch (_: Exception) {
            // Ignore errors if already stopped
        }

        // Release microphone resources
        audioRecord?.release()

        audioRecord = null

        Log.d(
            "AudioRecordManager",
            "Recording stopped"
        )
    }
}