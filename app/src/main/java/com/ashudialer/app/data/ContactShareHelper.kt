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
