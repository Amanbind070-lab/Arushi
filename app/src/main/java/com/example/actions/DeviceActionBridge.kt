package com.example.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class ActionExecutionResult(
    val success: Boolean,
    val actionName: String,
    val userSummary: String,
    val rawJsonResult: JSONObject
)

class DeviceActionBridge(private val contactService: ContactService = ContactService()) {

    companion object {
        private const val TAG = "ArushiDeviceActions"

        // Safe allowlist of applications
        private val ALLOWED_APPS = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "chrome" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
            "maps" to listOf("com.google.android.apps.maps"),
            "gmail" to listOf("com.google.android.gm")
        )
    }

    /**
     * Dispatch an action by tool name and arguments.
     */
    fun executeAction(context: Context, functionName: String, args: JSONObject): ActionExecutionResult {
        Log.d(TAG, "Executing tool '$functionName' with args: $args")
        return when (functionName) {
            "openWhatsApp" -> openWhatsApp(context)
            "openApp" -> {
                val appName = args.optString("appName", "")
                openApp(context, appName)
            }
            "openUrl" -> {
                val url = args.optString("url", "")
                openUrl(context, url)
            }
            "makeCall" -> {
                val phoneNumber = args.optString("phoneNumber", "")
                makeCall(context, phoneNumber)
            }
            "callContact" -> {
                val contactName = args.optString("contactName", "")
                callContact(context, contactName)
            }
            else -> {
                val json = JSONObject().apply {
                    put("success", false)
                    put("action", functionName)
                    put("error", "Function '$functionName' is not supported or not allowlisted")
                }
                ActionExecutionResult(
                    success = false,
                    actionName = functionName,
                    userSummary = "Action not allowed: $functionName",
                    rawJsonResult = json
                )
            }
        }
    }

    fun openWhatsApp(context: Context): ActionExecutionResult {
        val packageManager = context.packageManager
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")

        for (pkg in packages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launchIntent)
                    val json = JSONObject().apply {
                        put("success", true)
                        put("action", "openWhatsApp")
                        put("message", "WhatsApp has been opened successfully on the device.")
                    }
                    return ActionExecutionResult(
                        success = true,
                        actionName = "openWhatsApp",
                        userSummary = "Opened WhatsApp",
                        rawJsonResult = json
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching WhatsApp: ${e.message}")
                }
            }
        }

        // Web fallback
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            val json = JSONObject().apply {
                put("success", true)
                put("action", "openWhatsApp")
                put("message", "WhatsApp app not installed; opened WhatsApp Web.")
            }
            ActionExecutionResult(
                success = true,
                actionName = "openWhatsApp",
                userSummary = "Opened WhatsApp Web",
                rawJsonResult = json
            )
        } catch (e: Exception) {
            val json = JSONObject().apply {
                put("success", false)
                put("action", "openWhatsApp")
                put("error", "WhatsApp is not installed on this device and web fallback failed.")
            }
            ActionExecutionResult(
                success = false,
                actionName = "openWhatsApp",
                userSummary = "WhatsApp not available",
                rawJsonResult = json
            )
        }
    }

    fun openApp(context: Context, rawAppName: String): ActionExecutionResult {
        val normalized = rawAppName.trim().lowercase()
        val pm = context.packageManager

        // Special system intent handlers
        when (normalized) {
            "settings" -> {
                return try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    val json = JSONObject().apply {
                        put("success", true)
                        put("action", "openApp")
                        put("app", "Settings")
                        put("message", "Device settings opened successfully.")
                    }
                    ActionExecutionResult(true, "openApp", "Opened Settings", json)
                } catch (e: Exception) {
                    val json = JSONObject().apply {
                        put("success", false)
                        put("action", "openApp")
                        put("error", "Failed to open device settings.")
                    }
                    ActionExecutionResult(false, "openApp", "Failed to open Settings", json)
                }
            }
            "camera" -> {
                return try {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    val json = JSONObject().apply {
                        put("success", true)
                        put("action", "openApp")
                        put("app", "Camera")
                        put("message", "Camera opened successfully.")
                    }
                    ActionExecutionResult(true, "openApp", "Opened Camera", json)
                } catch (e: Exception) {
                    val json = JSONObject().apply {
                        put("success", false)
                        put("action", "openApp")
                        put("error", "Failed to open camera.")
                    }
                    ActionExecutionResult(false, "openApp", "Failed to open Camera", json)
                }
            }
            "clock", "alarm" -> {
                return try {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    val json = JSONObject().apply {
                        put("success", true)
                        put("action", "openApp")
                        put("app", "Clock")
                        put("message", "Clock/Alarms opened successfully.")
                    }
                    ActionExecutionResult(true, "openApp", "Opened Clock", json)
                } catch (e: Exception) {
                    val json = JSONObject().apply {
                        put("success", false)
                        put("action", "openApp")
                        put("error", "Failed to open clock.")
                    }
                    ActionExecutionResult(false, "openApp", "Failed to open Clock", json)
                }
            }
        }

        // Check allowlisted apps
        val packageCandidates = ALLOWED_APPS[normalized]
        if (packageCandidates == null) {
            val json = JSONObject().apply {
                put("success", false)
                put("action", "openApp")
                put("error", "App '$rawAppName' is not in the safe allowlist. Supported apps: WhatsApp, YouTube, Instagram, Chrome, Maps, Settings, Camera, Clock.")
            }
            return ActionExecutionResult(false, "openApp", "App '$rawAppName' not supported", json)
        }

        for (pkg in packageCandidates) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launchIntent)
                    val json = JSONObject().apply {
                        put("success", true)
                        put("action", "openApp")
                        put("app", rawAppName)
                        put("package", pkg)
                        put("message", "Opened $rawAppName successfully.")
                    }
                    return ActionExecutionResult(true, "openApp", "Opened $rawAppName", json)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching package $pkg: ${e.message}")
                }
            }
        }

        val json = JSONObject().apply {
            put("success", false)
            put("action", "openApp")
            put("error", "App '$rawAppName' is supported, but it is not installed on this device.")
        }
        return ActionExecutionResult(false, "openApp", "$rawAppName is not installed", json)
    }

    fun openUrl(context: Context, rawUrl: String): ActionExecutionResult {
        val trimmed = rawUrl.trim()
        // Strict URL validation
        if (!trimmed.startsWith("https://", ignoreCase = true) && !trimmed.startsWith("http://", ignoreCase = true)) {
            val json = JSONObject().apply {
                put("success", false)
                put("action", "openUrl")
                put("error", "Invalid or unsafe URL. Only http:// and https:// links are permitted.")
            }
            return ActionExecutionResult(false, "openUrl", "Blocked unsafe URL", json)
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val json = JSONObject().apply {
                put("success", true)
                put("action", "openUrl")
                put("url", trimmed)
                put("message", "Opened URL in browser: $trimmed")
            }
            ActionExecutionResult(true, "openUrl", "Opened $trimmed", json)
        } catch (e: Exception) {
            val json = JSONObject().apply {
                put("success", false)
                put("action", "openUrl")
                put("error", "Failed to open URL: ${e.message}")
            }
            ActionExecutionResult(false, "openUrl", "Failed to open URL", json)
        }
    }

    fun makeCall(context: Context, rawNumber: String): ActionExecutionResult {
        val cleanNumber = rawNumber.trim().replace(Regex("[^0-9+]"), "")
        if (cleanNumber.length < 3) {
            val json = JSONObject().apply {
                put("success", false)
                put("action", "makeCall")
                put("error", "Invalid phone number provided: '$rawNumber'")
            }
            return ActionExecutionResult(false, "makeCall", "Invalid phone number", json)
        }

        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(cleanNumber)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            val json = JSONObject().apply {
                put("success", true)
                put("action", "makeCall")
                put("phoneNumber", cleanNumber)
                put("dialerOpened", true)
                put("message", "Opened device phone dialer with number $cleanNumber pre-filled.")
            }
            ActionExecutionResult(true, "makeCall", "Dialing $cleanNumber", json)
        } catch (e: Exception) {
            val json = JSONObject().apply {
                put("success", false)
                put("action", "makeCall")
                put("error", "Could not open dialer: ${e.message}")
            }
            ActionExecutionResult(false, "makeCall", "Failed to open dialer", json)
        }
    }

    fun callContact(context: Context, contactName: String): ActionExecutionResult {
        val trimmed = contactName.trim()
        if (trimmed.isEmpty()) {
            val json = JSONObject().apply {
                put("success", false)
                put("action", "callContact")
                put("error", "No contact name provided")
            }
            return ActionExecutionResult(false, "callContact", "No contact name", json)
        }

        return when (val result = contactService.searchContact(context, trimmed)) {
            is ContactSearchResult.PermissionRequired -> {
                val json = JSONObject().apply {
                    put("success", false)
                    put("action", "callContact")
                    put("error", "Contacts permission is required to search and call contacts on this device.")
                    put("permissionNeeded", "android.permission.READ_CONTACTS")
                }
                ActionExecutionResult(false, "callContact", "Contacts permission needed", json)
            }
            is ContactSearchResult.NotFound -> {
                val json = JSONObject().apply {
                    put("success", false)
                    put("action", "callContact")
                    put("error", "No contact found matching '$trimmed' in device contacts.")
                }
                ActionExecutionResult(false, "callContact", "Contact '$trimmed' not found", json)
            }
            is ContactSearchResult.SingleMatch -> {
                val match = result.contact
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(match.phoneNumber)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    val json = JSONObject().apply {
                        put("success", true)
                        put("action", "callContact")
                        put("contactName", match.name)
                        put("phoneNumber", match.phoneNumber)
                        put("message", "Found contact ${match.name} (${match.phoneNumber}) and opened phone dialer.")
                    }
                    ActionExecutionResult(true, "callContact", "Dialing ${match.name}", json)
                } catch (e: Exception) {
                    val json = JSONObject().apply {
                        put("success", false)
                        put("action", "callContact")
                        put("error", "Failed to open dialer for ${match.name}: ${e.message}")
                    }
                    ActionExecutionResult(false, "callContact", "Dialer failed for ${match.name}", json)
                }
            }
            is ContactSearchResult.MultipleMatches -> {
                val contactsArray = JSONArray()
                result.contacts.forEach {
                    val obj = JSONObject().apply {
                        put("name", it.name)
                        put("phoneNumber", it.phoneNumber)
                        put("type", it.type)
                    }
                    contactsArray.put(obj)
                }
                val namesList = result.contacts.joinToString(", ") { "${it.name} (${it.type})" }
                val json = JSONObject().apply {
                    put("success", false)
                    put("action", "callContact")
                    put("error", "Multiple contacts found: $namesList. Please ask user to clarify which one to call.")
                    put("contacts", contactsArray)
                }
                ActionExecutionResult(false, "callContact", "Multiple contacts named $trimmed", json)
            }
        }
    }
}
