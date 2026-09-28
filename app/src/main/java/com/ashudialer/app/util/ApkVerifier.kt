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
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/**
 * Verifies a downloaded update APK BEFORE it is handed to the system installer.
 *
 * WHY THIS EXISTS: the in-app updater downloads an APK from a URL and asks Android to install it. Without a check,
 * anyone who could tamper with that download (a compromised release, a hijacked hosting account, a malicious
 * redirect) could push arbitrary code onto every user's phone. Android itself would refuse to install an APK
 * signed with a DIFFERENT key over this app, so the practical damage is limited, but refusing early is better than
 * relying on the last line of defence, and it gives the user a clear message instead of an installer error.
 *
 * WHAT IT CHECKS:
 *  - the file parses as an APK at all;
 *  - it is for THIS package (a different app is never installed through the updater);
 *  - its version is strictly newer than the running one (no downgrade / replay of an old vulnerable build);
 *  - every signing certificate in it matches the certificate this app is running under.
 *
 * The last check is the important one: "signed by the same key as the app the user already trusts".
 */
object ApkVerifier {

    sealed class Result {
        object Ok : Result()
        data class Rejected(val reason: String) : Result()
    }

    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File): Result {
        if (!apk.isFile || apk.length() < 100L * 1024) return Result.Rejected("file missing or too small")

        val pm = context.packageManager
        val archive = try {
            pm.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } catch (_: Exception) {
            null
        } ?: return Result.Rejected("not a valid APK")

        if (archive.packageName != context.packageName) {
            return Result.Rejected("APK is for a different app (${archive.packageName})")
        }

        val running = try {
            pm.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            return Result.Rejected("cannot read the installed version")
        }
        val runningCode = running.longVersionCode
        val newCode = archive.longVersionCode
        if (newCode <= runningCode) {
            return Result.Rejected("APK is not newer than the installed version")
        }

        val newSigners = archive.signingInfo?.let {
            if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
        }?.map { digest(it.toByteArray()) }.orEmpty()
        if (newSigners.isEmpty()) return Result.Rejected("APK is unsigned")

        val installed = try {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: Exception) {
            return Result.Rejected("cannot read the installed signature")
        }
        val installedSigners = installed.signingInfo?.let {
            if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
        }?.map { digest(it.toByteArray()) }.orEmpty()
        if (installedSigners.isEmpty()) return Result.Rejected("cannot read the installed signature")

        // Every certificate in the new APK must be one the installed app is already signed with.
        val allTrusted = newSigners.all { candidate ->
            installedSigners.any { MessageDigest.isEqual(it, candidate) }
        }
        if (!allTrusted) return Result.Rejected("APK is signed with a different key")

        return Result.Ok
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
