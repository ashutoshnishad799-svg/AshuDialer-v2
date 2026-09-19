package com.ashudialer.app.appcalls.recording

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Whether the recorded call was received or placed by this device. */
enum class CallDirection(val token: String, val label: String) {
    INCOMING("incoming", "Incoming"),
    OUTGOING("outgoing", "Outgoing");

    companion object {
        fun fromToken(token: String?) = entries.firstOrNull { it.token == token }
    }
}

/**
 * Everything known about one call that is being (or is about to be) recorded.
 * [sourceApp] is non-null for WhatsApp/Telegram VoIP calls and forces the OUTPUT audio source.
 */
@Parcelize
data class RecordingSession(
    val phoneNumber: String?,
    val direction: CallDirection,
    val contactName: String? = null,
    val isCrossCountry: Boolean = false,
    val sourceApp: String? = null
) : Parcelable {

    val isAppCall: Boolean get() = sourceApp != null

    /** Best label for file names / notifications: contact name, else number, else the app name. */
    val displayLabel: String
        get() = contactName?.takeIf { it.isNotBlank() }
            ?: phoneNumber?.takeIf { it.isNotBlank() }
            ?: sourceApp
            ?: "Unknown"

    companion object {
        const val EXTRA_SESSION = "com.ashudialer.app.appcalls.EXTRA_RECORDING_SESSION"

        /** Values some OEMs send instead of a real number for private/hidden callers. */
        private val ANONYMOUS_TOKENS = setOf("+anonymous", "anonymous", "unknown", "private", "+", "#", "")

        /** Returns null for anonymous/blank numbers so callers can treat "no number" uniformly. */
        fun sanitizeNumber(number: String?): String? {
            if (number == null) return null
            return if (number.trim().lowercase() in ANONYMOUS_TOKENS) null else number.trim()
        }

        /** Digits only (keeps a leading +) for comparing numbers regardless of formatting. */
        fun normalizeNumber(number: String): String {
            val trimmed = number.trim()
            if (trimmed.lowercase() in ANONYMOUS_TOKENS) return ""
            val digits = trimmed.filter { it.isDigit() }
            return if (trimmed.startsWith("+")) "+$digits" else digits
        }
    }
}
