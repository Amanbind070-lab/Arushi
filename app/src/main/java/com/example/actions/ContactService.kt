package com.example.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class ContactMatch(
    val name: String,
    val phoneNumber: String,
    val type: String = "Mobile"
)

sealed class ContactSearchResult {
    data class SingleMatch(val contact: ContactMatch) : ContactSearchResult()
    data class MultipleMatches(val contacts: List<ContactMatch>) : ContactSearchResult()
    data class NotFound(val query: String) : ContactSearchResult()
    data object PermissionRequired : ContactSearchResult()
}

class ContactService {

    companion object {
        private const val TAG = "ArushiContactService"
    }

    fun searchContact(context: Context, query: String): ContactSearchResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return ContactSearchResult.PermissionRequired
        }

        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return ContactSearchResult.NotFound(query)
        }

        val matches = mutableListOf<ContactMatch>()
        val seenNumbers = mutableSetOf<String>()

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (c.moveToNext()) {
                    val name = if (nameIdx != -1) c.getString(nameIdx) ?: "" else ""
                    val rawNumber = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                    val typeCode = if (typeIdx != -1) c.getInt(typeIdx) else -1

                    val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "")
                    if (cleanNumber.isEmpty() || seenNumbers.contains(cleanNumber)) {
                        continue
                    }

                    val typeStr = when (typeCode) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                        else -> "Phone"
                    }

                    // Check exact or partial match
                    if (name.equals(trimmedQuery, ignoreCase = true) ||
                        name.contains(trimmedQuery, ignoreCase = true)
                    ) {
                        seenNumbers.add(cleanNumber)
                        matches.add(ContactMatch(name = name, phoneNumber = cleanNumber, type = typeStr))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}", e)
        }

        return when {
            matches.isEmpty() -> ContactSearchResult.NotFound(trimmedQuery)
            matches.size == 1 -> ContactSearchResult.SingleMatch(matches.first())
            else -> {
                // If there's an EXACT name match among multiple, check if they share name
                val exactMatches = matches.filter { it.name.equals(trimmedQuery, ignoreCase = true) }
                if (exactMatches.size == 1) {
                    ContactSearchResult.SingleMatch(exactMatches.first())
                } else {
                    ContactSearchResult.MultipleMatches(matches.take(5))
                }
            }
        }
    }
}
