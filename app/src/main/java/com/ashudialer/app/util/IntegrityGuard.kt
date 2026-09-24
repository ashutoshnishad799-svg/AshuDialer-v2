package com.ashudialer.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.ashudialer.app.BuildConfig
import java.security.MessageDigest

/**
 * Checks that this copy of the app is the official one: signed with the release certificate the build
 * pipeline embedded ([BuildConfig.EXPECTED_CERT_SHA256]) and not switched to debuggable.
 *
 * WHAT THIS DOES: anyone who unpacks the APK, changes it and re-signs it has to use their own key, and their
 * key's fingerprint is different, so the modified copy notices and refuses to open (see TamperedScreen).
 * Android also refuses to install a differently-signed APK over the real one, so a modified copy cannot
 * silently replace it either.
 *
 * WHAT IT CANNOT DO: no check that runs inside the app can stop a determined person for good. Someone who
 * edits the compiled code can remove the check itself. R8 renaming and shrinking (already on for release
 * builds) makes that slower and harder, but not impossible. The real protections are that the signing key
 * stays secret and that people only download from the official Releases page.
 *
 * The check is skipped when no fingerprint was embedded (local and debug builds), so building the project
 * yourself always works. It only ever blocks the main screen: calls, the in-call screen and the notification
 * paths are never touched, so a phone can always place and answer calls, including emergency calls.
 */
object IntegrityGuard {

    enum class Verdict { OK, SKIPPED, TAMPERED }

    @Volatile
    private var cached: Verdict? = null

    fun verify(context: Context): Verdict {
        cached?.let { return it }
        val verdict = compute(context.applicationContext)
        cached = verdict
        return verdict
    }

    private fun compute(context: Context): Verdict {
        val expectedHex = BuildConfig.EXPECTED_CERT_SHA256.trim().lowercase().replace(":", "")
        if (expectedHex.length != 64) return Verdict.SKIPPED

        // A release APK is never debuggable. One that is has been repackaged so a debugger can attach and edit it.
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) return Verdict.TAMPERED

        return try {
            val expected = hexToBytes(expectedHex) ?: return Verdict.SKIPPED
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners
            if (signers == null || signers.isEmpty()) return Verdict.TAMPERED
            // EVERY signer must be the official one. Constant-time compare, though the value is not secret.
            val allOfficial = signers.all { sig ->
                MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()), expected)
            }
            if (allOfficial) Verdict.OK else Verdict.TAMPERED
        } catch (_: Exception) {
            // The system could not tell us the signer (very rare). Refusing here would lock out a genuine install,
            // so this fails open; the value is only ever compared, never trusted.
            Verdict.SKIPPED
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[2 * i], 16)
            val lo = Character.digit(hex[2 * i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
