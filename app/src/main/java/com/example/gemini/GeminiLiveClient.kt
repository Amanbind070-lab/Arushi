package com.example.gemini

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class LiveConnectionState {
    data object Disconnected : LiveConnectionState()
    data object Connecting : LiveConnectionState()
    data object Connected : LiveConnectionState()
    data class Error(val message: String) : LiveConnectionState()
}

class GeminiLiveClient(
    private val scope: CoroutineScope,
    private val onAudioReceived: (ByteArray, Int) -> Unit,
    private val onTranscriptReceived: (String) -> Unit,
    private val onToolCall: (id: String, name: String, args: JSONObject, callback: (JSONObject) -> Unit) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTurnCompleted: () -> Unit
) {

    companion object {
        private const val TAG = "ArushiGeminiLive"
        private const val WS_URL_TEMPLATE =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key="
        private const val REST_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-native-audio-preview-12-2025:generateContent?key="
    }

    private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Disconnected)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private var webSocket: WebSocket? = null
    private var isSetupConfirmed = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun connect(apiKey: String) {
        if (apiKey.isBlank()) {
            Log.e(TAG, "Cannot connect: Gemini API key is unavailable")
            _connectionState.value = LiveConnectionState.Error("Secret unavailable: GEMINI_API_KEY could not be read from the environment.")
            return
        }

        if (_connectionState.value is LiveConnectionState.Connecting ||
            _connectionState.value is LiveConnectionState.Connected
        ) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        _connectionState.value = LiveConnectionState.Connecting
        isSetupConfirmed = false

        val wsUrl = "$WS_URL_TEMPLATE$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully")
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
                _connectionState.value = LiveConnectionState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = LiveConnectionState.Error("Live connection failed: ${t.localizedMessage ?: "Unknown error"}")
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", GeminiConfig.LIVE_MODEL)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", GeminiConfig.TTS_VOICE)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", GeminiConfig.SYSTEM_INSTRUCTION)
                            })
                        })
                    })
                    put("tools", GeminiConfig.getToolsDeclarationJson())
                })
            }
            Log.d(TAG, "Sending Live setup message...")
            ws.send(setupJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error building/sending setup: ${e.message}")
        }
    }

    fun sendAudioChunk(pcmBytes: ByteArray) {
        if (_connectionState.value !is LiveConnectionState.Connected || webSocket == null) {
            return
        }

        try {
            val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val realtimeInput = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }
            webSocket?.send(realtimeInput.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending realtime audio chunk: ${e.message}")
        }
    }

    fun sendTextMessage(text: String) {
        if (_connectionState.value !is LiveConnectionState.Connected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected; message dropped")
            return
        }

        try {
            val clientContent = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", text)
                                })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            webSocket?.send(clientContent.toString())
            Log.d(TAG, "Sent user text turn: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message: ${e.message}")
        }
    }

    fun sendToolResponse(callId: String, responseJson: JSONObject) {
        try {
            val toolResponseMsg = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", callId)
                            put("response", JSONObject().apply {
                                put("output", responseJson)
                            })
                        })
                    })
                })
            }
            Log.d(TAG, "Sending tool response for callId $callId: $responseJson")
            webSocket?.send(toolResponseMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}")
        }
    }

    private fun handleIncomingMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)

            // Setup confirmation
            if (root.has("setupComplete")) {
                isSetupConfirmed = true
                _connectionState.value = LiveConnectionState.Connected
                Log.d(TAG, "Gemini Live setup completed and confirmed!")
                return
            }

            // Server Content (Model Turn)
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Model turn interrupted by barge-in")
                    scope.launch(Dispatchers.Main) {
                        onInterrupted()
                    }
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Check inline audio data
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                            val dataBase64 = inlineData.optString("data", "")

                            if (dataBase64.isNotEmpty()) {
                                val pcmBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                val sampleRate = extractSampleRate(mimeType)
                                scope.launch(Dispatchers.Main) {
                                    onAudioReceived(pcmBytes, sampleRate)
                                }
                            }
                        }

                        // Check text transcript
                        if (part.has("text")) {
                            val text = part.getString("text")
                            if (text.isNotBlank()) {
                                scope.launch(Dispatchers.Main) {
                                    onTranscriptReceived(text)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    scope.launch(Dispatchers.Main) {
                        onTurnCompleted()
                    }
                }
            }

            // Tool Call from Gemini
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()

                for (i in 0 until functionCalls.length()) {
                    val fc = functionCalls.getJSONObject(i)
                    val id = fc.optString("id", "call_${System.currentTimeMillis()}")
                    val name = fc.getString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()

                    Log.d(TAG, "Received tool call from Arushi: id=$id, name=$name, args=$args")
                    scope.launch(Dispatchers.Main) {
                        onToolCall(id, name, args) { resultJson ->
                            sendToolResponse(id, resultJson)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming WebSocket message: ${e.message}", e)
        }
    }

    private fun extractSampleRate(mimeType: String): Int {
        val rateRegex = Regex("rate=(\\d+)")
        val match = rateRegex.find(mimeType)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 24000
    }

    /**
     * Fallback REST generation with native audio modality and tool calling.
     */
    suspend fun generateRestAudioTurn(
        apiKey: String,
        userPrompt: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$REST_URL_TEMPLATE$apiKey"
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userPrompt) })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", GeminiConfig.SYSTEM_INSTRUCTION) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", GeminiConfig.TTS_VOICE)
                            })
                        })
                    })
                })
                put("tools", GeminiConfig.getToolsDeclarationJson())
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder().url(url).post(body).build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "REST audio call failed: ${response.code} $responseString")
                return@withContext false
            }

            val respJson = JSONObject(responseString)
            val candidates = respJson.optJSONArray("candidates") ?: return@withContext false
            if (candidates.length() == 0) return@withContext false

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return@withContext false
            val parts = content.optJSONArray("parts") ?: JSONArray()

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val callId = "rest_call_${System.currentTimeMillis()}"
                    val name = fc.getString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()
                    withContext(Dispatchers.Main) {
                        onToolCall(callId, name, args) { /* completed */ }
                    }
                }

                if (part.has("inlineData")) {
                    val inlineData = part.getJSONObject("inlineData")
                    val mime = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                    val data = inlineData.optString("data", "")
                    if (data.isNotEmpty()) {
                        val pcm = Base64.decode(data, Base64.DEFAULT)
                        val rate = extractSampleRate(mime)
                        withContext(Dispatchers.Main) {
                            onAudioReceived(pcm, rate)
                        }
                    }
                }

                if (part.has("text")) {
                    val text = part.getString("text")
                    withContext(Dispatchers.Main) {
                        onTranscriptReceived(text)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onTurnCompleted()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during REST audio generation: ${e.message}", e)
            false
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting Gemini Live session")
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.w(TAG, "Exception closing WebSocket: ${e.message}")
        }
        webSocket = null
        _connectionState.value = LiveConnectionState.Disconnected
    }
}
