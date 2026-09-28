/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE file in the project root. This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.ashudialer.app.BuildConfig
import java.security.MessageDigest

/**
 * Checks that this copy of the app is the official one.
 *
 * LAYERS (each independent, all must pass on a release build that embeds a fingerprint):
 *  1. Signing certificate - every signer must match [BuildConfig.EXPECTED_CERT_SHA256].
 *  2. Not debuggable - a release APK is never debuggable; a repackaged one often is.
 *  3. No debugger attached - a debugger on a release build means someone is stepping through the code.
 *  4. Package name - a clone that changed its applicationId is not the official app.
 *  5. Signing-lineage - the certificate is read through two different platform APIs and both must agree,
 *     which defeats the simplest "hook getPackageInfo" style spoof that only patches one path.
 *
 * WHAT THIS DOES NOT DO (deliberately):
 *  - It does NOT block rooted phones, ADB, Shizuku or custom ROMs. This app records calls through Shizuku, which
 *    runs on root or wireless-ADB, so its real users are exactly the people such a check would lock out.
 *  - It does NOT claim to make modification impossible. Anyone with the source (this project is open source) can
 *    build their own copy under their own name; the GPL allows that. What this guard guarantees is narrower and
 *    honest: a copy that is NOT signed with the official key cannot pass itself off as the official app, and cannot
 *    be installed over it. Official builds are the ones on the Releases page, signed by the developer's key.
 *
 * The check is skipped when no fingerprint was embedded (local and debug builds), so building the project
 * yourself always works. It only ever gates the main screen: calls, the in-call screen and notification actions
 * are never touched, so a phone can always place and answer calls, including emergency calls.
 */
object IntegrityGuard {

    enum class Verdict { OK, SKIPPED, TAMPERED }

    /** Package the official build is published under. */
    private const val OFFICIAL_PACKAGE = "com.ashudialer.app"

    @Volatile
    private var cached: Verdict? = null

    /** Cached result. Cheap enough to call from anywhere. */
    fun verify(context: Context): Verdict {
        cached?.let { return it }
        val verdict = compute(context.applicationContext)
        cached = verdict
        return verdict
    }

    /**
     * Same check but never uses the cache. Used for a second, later check (e.g. after the UI is up) so patching
     * one call site at start-up is not enough to defeat it.
     */
    fun verifyFresh(context: Context): Verdict = compute(context.applicationContext)

    /** True only when the build is an official one AND the check positively failed. */
    fun isTampered(context: Context): Boolean = verify(context) == Verdict.TAMPERED

    private fun compute(context: Context): Verdict {
        val expectedHex = BuildConfig.EXPECTED_CERT_SHA256.trim().lowercase().replace(":", "")
        // No fingerprint embedded => a local/debug build. Nothing to compare against.
        if (expectedHex.length != 64) return Verdict.SKIPPED
        val expected = hexToBytes(expectedHex) ?: return Verdict.SKIPPED

        // Layer 2: a release APK is never debuggable.
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) return Verdict.TAMPERED

        // Layer 3: a debugger attached to a release build.
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return Verdict.TAMPERED

        // Layer 4: a clone that renamed the package is not the official app.
        if (context.packageName != OFFICIAL_PACKAGE) return Verdict.TAMPERED

        return try {
            val pm = context.packageManager

            // Layer 1 (primary API): PackageManager signing info.
            val primary = signersViaSigningInfo(pm, context.packageName)
            if (primary == null || primary.isEmpty()) return Verdict.TAMPERED
            // EVERY signer must be the official one.
            if (!primary.all { sameDigest(it, expected) }) return Verdict.TAMPERED

            // Layer 5 (cross-check): read again through a different code path. On a genuine device both agree.
            // A spoof that only patches one API returns a different answer here.
            val secondary = signersViaSourceDir(pm, context)
            if (secondary != null && secondary.isNotEmpty() && !secondary.all { sameDigest(it, expected) }) {
                return Verdict.TAMPERED
            }

            Verdict.OK
        } catch (_: Exception) {
            // The system could not tell us the signer (very rare). Refusing here would lock a genuine user out of
            // their own app, so this fails open. The value is only ever compared, never trusted.
            Verdict.SKIPPED
        }
    }

    @Suppress("DEPRECATION")
    private fun signersViaSigningInfo(pm: PackageManager, pkg: String): List<ByteArray>? {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val si = info.signingInfo ?: return null
        val sigs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return sigs?.map { it.toByteArray() }
    }

    @Suppress("DEPRECATION")
    private fun signersViaSourceDir(pm: PackageManager, context: Context): List<ByteArray>? {
        val path = context.applicationInfo.sourceDir ?: return null
        val info = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        val si = info.signingInfo ?: return null
        val sigs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return sigs?.map { it.toByteArray() }
    }

    private fun sameDigest(certBytes: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(certBytes), expected)

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

    /** Human-readable reason, for the tamper screen / diagnostics. Never shown to a genuine user. */
    @Suppress("unused")
    fun sdkInfo(): String = "sdk=${Build.VERSION.SDK_INT}"
}
