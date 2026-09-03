package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sequential PCM Audio playback engine with barge-in interruption support.
 * Plays Gemini native audio chunks via Android's AudioTrack and tracks speaking amplitude.
 */
class AudioPlayer {

    companion object {
        private const val TAG = "ArushiAudioPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000 // Standard Gemini Live output sample rate
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate = DEFAULT_SAMPLE_RATE
    private var playbackJob: Job? = null
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    fun initialize(sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        if (audioTrack != null && currentSampleRate == sampleRate) return

        release()
        currentSampleRate = sampleRate
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                CHANNEL_OUT,
                ENCODING
            )
            val bufferSize = maxOf(minBufferSize, 8192)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            Log.d(TAG, "AudioTrack initialized at $sampleRate Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun startPlaybackQueue(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Audio playback queue loop started")
            while (isActive) {
                try {
                    val chunk = audioQueue.receive()
                    if (audioTrack == null) {
                        initialize(currentSampleRate)
                    }

                    if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.play()
                    }

                    _isPlaying.value = true
                    calculateOutputRms(chunk)

                    var bytesWritten = 0
                    while (bytesWritten < chunk.size && isActive) {
                        val written = audioTrack?.write(chunk, bytesWritten, chunk.size - bytesWritten) ?: -1
                        if (written > 0) {
                            bytesWritten += written
                        } else {
                            break
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Error playing audio chunk: ${e.message}")
                } finally {
                    if (audioQueue.isEmpty) {
                        _isPlaying.value = false
                        _outputAmplitude.value = 0f
                    }
                }
            }
        }
    }

    /**
     * Enqueue a PCM chunk for playback.
     */
    fun enqueueAudio(pcmBytes: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        if (pcmBytes.isEmpty()) return
        if (sampleRate != currentSampleRate) {
            // If sample rate changed and queue is idle, re-initialize
            if (!_isPlaying.value) {
                initialize(sampleRate)
            }
        }
        audioQueue.trySend(pcmBytes)
    }

    /**
     * Barge-in interruption: immediately stops playback, flushes the AudioTrack,
     * and purges all queued audio chunks.
     */
    fun stopAndClearQueue() {
        Log.d(TAG, "Barge-in interruption: Clearing audio queue and flushing playback")
        // Drain channel
        while (true) {
            val item = audioQueue.tryReceive().getOrNull() ?: break
        }
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Exception pausing/flushing AudioTrack: ${e.message}")
        }
        _isPlaying.value = false
        _outputAmplitude.value = 0f
    }

    /**
     * Speaker Diagnostic Test: plays a clean 440Hz sine wave tone for the specified duration.
     */
    fun playDiagnosticTone(scope: CoroutineScope, freqHz: Double = 440.0, durationMs: Int = 1000) {
        scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Playing speaker diagnostic test tone ($freqHz Hz, ${durationMs}ms)")
            stopAndClearQueue()
            initialize(DEFAULT_SAMPLE_RATE)

            val sampleRate = DEFAULT_SAMPLE_RATE
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmBytes = ByteArray(numSamples * 2)

            for (i in 0 until numSamples) {
                val time = i.toDouble() / sampleRate
                val angle = 2.0 * PI * freqHz * time
                // Envelope window to prevent clicks (fade in/out 50ms)
                val fadeSamples = (sampleRate * 0.05).toInt()
                val envelope = when {
                    i < fadeSamples -> i.toDouble() / fadeSamples
                    i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                    else -> 1.0
                }
                val sampleValue = (sin(angle) * 0.5 * envelope * Short.MAX_VALUE).toInt().coerceIn(
                    Short.MIN_VALUE.toInt(),
                    Short.MAX_VALUE.toInt()
                )
                pcmBytes[i * 2] = (sampleValue and 0xFF).toByte()
                pcmBytes[i * 2 + 1] = ((sampleValue shr 8) and 0xFF).toByte()
            }

            enqueueAudio(pcmBytes, sampleRate)
        }
    }

    fun release() {
        stopAndClearQueue()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    private fun calculateOutputRms(pcmBytes: ByteArray) {
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
        _outputAmplitude.value = (rms * 3.0).coerceIn(0.0, 1.0).toFloat()
    }
}
