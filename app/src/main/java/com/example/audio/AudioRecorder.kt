package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Captures real microphone audio continuously as 16kHz 16-bit mono PCM.
 * Computes live RMS amplitude for waveform visualization and sends raw PCM bytes to listeners.
 */
class AudioRecorder {

    companion object {
        private const val TAG = "ArushiAudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE_BYTES = 2048 // ~64ms of audio at 16kHz 16-bit
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAudioChunk: (ByteArray) -> Unit
    ): Boolean {
        if (_isRecording.value) {
            Log.d(TAG, "Recording already in progress")
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, CHUNK_SIZE_BYTES * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                release()
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            Log.d(TAG, "AudioRecord started at $SAMPLE_RATE Hz")

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                while (isActive && _isRecording.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        val chunk = buffer.copyOf(bytesRead)
                        calculateRms(chunk)
                        onAudioChunk(chunk)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            release()
            return false
        }
    }

    fun stopRecording() {
        Log.d(TAG, "Stopping AudioRecord")
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping AudioRecord: ${e.message}")
        }
        release()
        _amplitude.value = 0f
    }

    private fun release() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    private fun calculateRms(pcmBytes: ByteArray) {
        if (pcmBytes.size < 2) return
        var sumSquares = 0.0
        val sampleCount = pcmBytes.size / 2
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt()
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or (low and 0xFF)
            sumSquares += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSquares / sampleCount) / 32768.0
        // Normalizing and smoothing
        val normalized = (rms * 3.5).coerceIn(0.0, 1.0).toFloat()
        _amplitude.value = normalized
    }
}
