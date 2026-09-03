package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.actions.ActionExecutionResult
import com.example.actions.DeviceActionBridge
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.gemini.GeminiLiveClient
import com.example.gemini.LiveConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class AssistantStatus {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    TOOL_EXECUTING,
    ERROR
}

data class ArushiUiState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val isLiveConnected: Boolean = false,
    val liveTranscript: String = "Tap the glowing mic to start talking with Arushi.",
    val lastAction: ActionExecutionResult? = null,
    val errorMessage: String? = null,
    val activeAmplitude: Float = 0f,
    val isDiagnosticTonePlaying: Boolean = false
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ArushiViewModel"
    }

    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    private val actionBridge = DeviceActionBridge()

    private val _uiState = MutableStateFlow(ArushiUiState())
    val uiState: StateFlow<ArushiUiState> = _uiState.asStateFlow()

    private var amplitudeJob: Job? = null

    private val geminiClient = GeminiLiveClient(
        scope = viewModelScope,
        onAudioReceived = { pcmBytes, sampleRate ->
            handleGeminiAudioChunk(pcmBytes, sampleRate)
        },
        onTranscriptReceived = { text ->
            handleGeminiTranscript(text)
        },
        onToolCall = { callId, name, args, callback ->
            handleToolCall(callId, name, args, callback)
        },
        onInterrupted = {
            handleBargeIn()
        },
        onTurnCompleted = {
            handleTurnCompleted()
        }
    )

    init {
        audioPlayer.startPlaybackQueue(viewModelScope)

        // Observe Live connection state
        viewModelScope.launch {
            geminiClient.connectionState.collect { connState ->
                when (connState) {
                    is LiveConnectionState.Connecting -> {
                        _uiState.value = _uiState.value.copy(
                            status = AssistantStatus.CONNECTING,
                            isLiveConnected = false,
                            errorMessage = null
                        )
                    }
                    is LiveConnectionState.Connected -> {
                        _uiState.value = _uiState.value.copy(
                            status = AssistantStatus.LISTENING,
                            isLiveConnected = true,
                            liveTranscript = "Hey there! Arushi here. What's on your mind?",
                            errorMessage = null
                        )
                        startMicrophoneStreaming()
                    }
                    is LiveConnectionState.Disconnected -> {
                        _uiState.value = _uiState.value.copy(
                            isLiveConnected = false,
                            status = if (_uiState.value.status != AssistantStatus.ERROR) AssistantStatus.IDLE else AssistantStatus.ERROR
                        )
                    }
                    is LiveConnectionState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            status = AssistantStatus.ERROR,
                            isLiveConnected = false,
                            errorMessage = connState.message
                        )
                    }
                }
            }
        }

        // Amplitude monitor loop for UI waveforms
        amplitudeJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30)
                val currentStatus = _uiState.value.status
                val amp = when (currentStatus) {
                    AssistantStatus.LISTENING -> audioRecorder.amplitude.value
                    AssistantStatus.SPEAKING -> audioPlayer.outputAmplitude.value
                    else -> 0f
                }
                _uiState.value = _uiState.value.copy(activeAmplitude = amp)
            }
        }
    }

    private fun getEffectiveApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    fun toggleVoiceSession() {
        if (_uiState.value.status == AssistantStatus.IDLE || _uiState.value.status == AssistantStatus.ERROR) {
            startSession()
        } else {
            stopSession()
        }
    }

    fun startSession() {
        Log.d(TAG, "Starting Arushi Live Session")
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                status = AssistantStatus.ERROR,
                errorMessage = "Secret unavailable: GEMINI_API_KEY could not be read from the environment."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            status = AssistantStatus.CONNECTING,
            errorMessage = null,
            liveTranscript = "Connecting to Gemini Live..."
        )

        audioPlayer.initialize()
        geminiClient.connect(apiKey)
    }

    fun stopSession() {
        Log.d(TAG, "Stopping Arushi Live Session")
        audioRecorder.stopRecording()
        audioPlayer.stopAndClearQueue()
        geminiClient.disconnect()
        _uiState.value = _uiState.value.copy(
            status = AssistantStatus.IDLE,
            isLiveConnected = false,
            liveTranscript = "Session ended. Tap the mic to talk again."
        )
    }

    private fun startMicrophoneStreaming() {
        val started = audioRecorder.startRecording(viewModelScope) { chunk ->
            geminiClient.sendAudioChunk(chunk)
        }
        if (!started) {
            _uiState.value = _uiState.value.copy(
                status = AssistantStatus.ERROR,
                errorMessage = "Microphone permission required or recording failed to start."
            )
        }
    }

    fun interruptSpeech() {
        Log.d(TAG, "User interrupted speech (barge-in)")
        audioPlayer.stopAndClearQueue()
        _uiState.value = _uiState.value.copy(
            status = AssistantStatus.LISTENING,
            liveTranscript = "I'm listening..."
        )
    }

    private fun handleBargeIn() {
        Log.d(TAG, "Barge-in signaled by Gemini server")
        audioPlayer.stopAndClearQueue()
        _uiState.value = _uiState.value.copy(
            status = AssistantStatus.LISTENING
        )
    }

    private fun handleGeminiAudioChunk(pcmBytes: ByteArray, sampleRate: Int) {
        _uiState.value = _uiState.value.copy(
            status = AssistantStatus.SPEAKING
        )
        audioPlayer.enqueueAudio(pcmBytes, sampleRate)
    }

    private fun handleGeminiTranscript(text: String) {
        val current = _uiState.value.liveTranscript
        val updated = if (_uiState.value.status == AssistantStatus.SPEAKING) {
            if (current.startsWith("Hey there!") || current.startsWith("Connecting") || current.startsWith("I'm listening")) {
                text
            } else {
                "$current $text".trim()
            }
        } else {
            text
        }
        _uiState.value = _uiState.value.copy(liveTranscript = updated)
    }

    private fun handleTurnCompleted() {
        Log.d(TAG, "Turn completed, waiting for audio playback to finish")
        viewModelScope.launch {
            // Wait until player queue finishes
            while (audioPlayer.isPlaying.value) {
                kotlinx.coroutines.delay(100)
            }
            if (_uiState.value.isLiveConnected) {
                _uiState.value = _uiState.value.copy(
                    status = AssistantStatus.LISTENING
                )
            }
        }
    }

    private fun handleToolCall(
        callId: String,
        name: String,
        args: JSONObject,
        sendCallback: (JSONObject) -> Unit
    ) {
        Log.d(TAG, "Executing device tool call: $name with args: $args")
        _uiState.value = _uiState.value.copy(
            status = AssistantStatus.TOOL_EXECUTING,
            liveTranscript = "Executing: $name..."
        )

        val result = actionBridge.executeAction(getApplication(), name, args)

        _uiState.value = _uiState.value.copy(
            lastAction = result,
            liveTranscript = result.userSummary
        )

        sendCallback(result.rawJsonResult)
    }

    /**
     * Executes a user command directly (e.g. from quick suggestion chips)
     */
    fun sendCommand(text: String) {
        if (!_uiState.value.isLiveConnected) {
            val apiKey = getEffectiveApiKey()
            if (apiKey.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    status = AssistantStatus.ERROR,
                    errorMessage = "Secret unavailable: GEMINI_API_KEY could not be read from the environment."
                )
                return
            }

            // Fallback direct REST generation if Live session is not currently open
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    status = AssistantStatus.CONNECTING,
                    liveTranscript = "Processing: \"$text\"..."
                )
                audioPlayer.initialize()
                val success = geminiClient.generateRestAudioTurn(apiKey, text)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        status = AssistantStatus.ERROR,
                        errorMessage = "Could not generate audio response. Check your API key and connection."
                    )
                }
            }
        } else {
            geminiClient.sendTextMessage(text)
        }
    }

    /**
     * Speaker Diagnostic Test: plays 440Hz test tone through the same AudioTrack pipeline.
     */
    fun runSpeakerDiagnostic() {
        Log.d(TAG, "Triggering speaker diagnostic test tone")
        _uiState.value = _uiState.value.copy(
            isDiagnosticTonePlaying = true,
            liveTranscript = "Playing 440Hz test tone through speaker..."
        )
        audioPlayer.playDiagnosticTone(viewModelScope, freqHz = 440.0, durationMs = 1200)

        viewModelScope.launch {
            kotlinx.coroutines.delay(1300)
            _uiState.value = _uiState.value.copy(
                isDiagnosticTonePlaying = false,
                liveTranscript = "Speaker diagnostic completed. If tone was heard, audio output is operational."
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.release()
        geminiClient.disconnect()
        amplitudeJob?.cancel()
    }
}
