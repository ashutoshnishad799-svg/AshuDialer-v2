package com.ashudialer.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Creates a small standards-based vCard for sharing a contact through any app. */
object ContactShareHelper {
    fun share(context: Context, contact: Contact) {
        val safeName = contact.displayName.ifBlank { "Contact" }
        val vcard = buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:${escape(safeName)}")
            appendLine("TEL;TYPE=CELL:${escape(contact.phoneNumber)}")
            appendLine("END:VCARD")
        }

        val dir = File(context.cacheDir, "shared_contacts").apply { mkdirs() }
        val file = File(dir, safeFileName(safeName))
        file.writeText(vcard, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${contact.displayName}: ${contact.phoneNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("Contact", uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share contact"))
    }

    /**
     * Shares a number that has no saved contact behind it - previously
     * "Share Contact" only existed for a saved ContactDetailScreen, so an
     * unsaved number (Recents, UnknownNumberDetailScreen) had no share
     * action at all. There's no name to build a meaningful vCard FN from
     * here, so this shares as plain text rather than a .vcf, in the
     * "Phone: +91XXXXXXXXXX" shape - readable pasted into any chat app
     * without needing a vCard-aware receiver on the other end, which
     * matters more for a bare number than it does for an already-named
     * contact.
     */
    fun shareNumber(context: Context, phoneNumber: String, displayName: String? = null) {
        val text = if (!displayName.isNullOrBlank()) {
            "$displayName\nPhone: $phoneNumber"
        } else {
            "Phone: $phoneNumber"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share contact"))
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
        .replace("\r", "")

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
        .ifBlank { "contact" } + ".vcf"
}
