package com.ashudialer.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ashudialer.app.BuildConfig
import java.io.File

/**
 * Creates a small, shareable diagnostic report for bug reports.
 *
 * The report intentionally redacts phone-like numbers, email addresses and
 * other obvious identifiers before the log text is written. It is meant for
 * troubleshooting, not for collecting call/contact content.
 */
object DiagnosticReport {
    private const val AUTHORITY = "com.ashudialer.app.fileprovider"

    fun share(context: Context) {
        val file = runCatching { create(context) }.getOrNull() ?: return
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Phone diagnostic report ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, "Phone diagnostic report attached. Please include a short description of the problem.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("diagnostic_report", uri)
        }
        context.startActivity(Intent.createChooser(send, "Send diagnostic report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun create(context: Context): File {
        val out = File(context.cacheDir, "diagnostics/phone-diagnostic-${System.currentTimeMillis()}.txt")
        out.parentFile?.mkdirs()

        val defaultDialer = runCatching {
            val role = context.getSystemService(android.app.role.RoleManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && role != null && role.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) "yes" else "no"
        }.getOrDefault("unknown")

        val log = runCatching {
            ProcessBuilder("logcat", "-d", "-t", "500", "-v", "brief")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().use { it.readText() }
        }.getOrDefault("(App log buffer unavailable on this device.)")

        val sanitized = redact(log)
            .lineSequence()
            .filter { line ->
                val l = line.lowercase()
                // Keep the report focused on this app's own diagnostic tags.
                l.contains("mainactivity") ||
                    l.contains("ashu") ||
                    l.contains("callnotification") ||
                    l.contains("callrecorder") ||
                    l.contains("pixelincall") ||
                    l.contains("outgoingcall") ||
                    l.contains("callbackreminder") ||
                    l.contains("dialerpermissions")
            }
            .takeLast(220)
            .joinToString("\n")

        out.writeText(
            buildString {
                appendLine("Phone diagnostic report")
                appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Package: ${BuildConfig.APPLICATION_ID}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Default dialer role: $defaultDialer")
                appendLine("Locale: ${context.resources.configuration.locales[0]}")
                appendLine()
                appendLine("Recent app diagnostic log (sanitized)")
                appendLine("-----------------------------------")
                appendLine(sanitized.ifBlank { "(No matching app log entries were available.)" })
                appendLine()
                appendLine("Privacy note: this report is generated on-device. Phone-like numbers and email addresses are redacted before sharing.")
            }
        )
        return out
    }

    private fun redact(input: String): String {
        return input
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL REDACTED]")
            .replace(Regex("(?<!\\d)(?:\\+?\\d[\\d .()_-]{6,}\\d)(?!\\d)"), "[NUMBER REDACTED]")
    }
}
