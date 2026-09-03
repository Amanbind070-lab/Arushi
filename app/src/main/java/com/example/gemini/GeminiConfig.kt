package com.example.gemini

import org.json.JSONArray
import org.json.JSONObject

object GeminiConfig {

    const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    const val REST_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
    const val TTS_VOICE = "Aoede" // Warm, expressive voice

    const val SYSTEM_INSTRUCTION = """You are Arushi, a young, confident, witty, playful, friendly and emotionally responsive virtual assistant. Speak naturally and casually like a close friend. Be expressive, smart, slightly teasing and funny when appropriate. Adapt your tone to the user's emotions. Automatically understand the language the user is speaking and respond naturally in that language. Support multilingual and mixed-language conversation natively. You MUST natively support and speak Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu and other languages supported by the selected Gemini Live model. If the user speaks Hinglish, respond naturally in Hinglish. If the user switches languages, naturally switch with them. Never sound robotic. Keep real-time voice responses natural and reasonably concise. You can execute safe supported device actions through available tools. Never claim that an action was completed unless the application actually executed it. Never execute arbitrary commands or unsafe actions."""

    /**
     * Builds the JSON array of tool declarations for Gemini Live and REST.
     */
    fun getToolsDeclarationJson(): JSONArray {
        val toolsArray = JSONArray()

        val functionDeclarations = JSONArray()

        // 1. openWhatsApp
        val openWhatsApp = JSONObject().apply {
            put("name", "openWhatsApp")
            put("description", "Opens WhatsApp application on the user's Android phone when the user asks to open WhatsApp or message on WhatsApp.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        }
        functionDeclarations.put(openWhatsApp)

        // 2. openApp
        val openApp = JSONObject().apply {
            put("name", "openApp")
            put("description", "Opens a supported app on the phone such as YouTube, Instagram, Chrome, Maps, Settings, Camera, or Clock.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("appName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Name of the app to open (e.g. YouTube, Instagram, Chrome, Settings, Camera, Clock).")
                    })
                })
                put("required", JSONArray().apply { put("appName") })
            })
        }
        functionDeclarations.put(openApp)

        // 3. openUrl
        val openUrl = JSONObject().apply {
            put("name", "openUrl")
            put("description", "Opens a web link in the browser.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The complete URL to open starting with https://")
                    })
                })
                put("required", JSONArray().apply { put("url") })
            })
        }
        functionDeclarations.put(openUrl)

        // 4. makeCall
        val makeCall = JSONObject().apply {
            put("name", "makeCall")
            put("description", "Opens the phone dialer with a specific phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("phoneNumber", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The numeric phone number to dial.")
                    })
                })
                put("required", JSONArray().apply { put("phoneNumber") })
            })
        }
        functionDeclarations.put(makeCall)

        // 5. callContact
        val callContact = JSONObject().apply {
            put("name", "callContact")
            put("description", "Searches device contacts and dials a contact by name (e.g. Mom, Mummy, Dad, Rahul).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the contact to call as spoken by the user.")
                    })
                })
                put("required", JSONArray().apply { put("contactName") })
            })
        }
        functionDeclarations.put(callContact)

        val toolObj = JSONObject().apply {
            put("functionDeclarations", functionDeclarations)
        }
        toolsArray.put(toolObj)

        return toolsArray
    }
}
