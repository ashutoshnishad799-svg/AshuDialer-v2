// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.WorkerThread
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Extracts the bundled scrcpy-server jar from this module's assets to shared storage, where the
 * privileged shell process can read and execute it. SHA-256 verified both after extraction and
 * on every subsequent use (see ShellService.kt's own re-verification before exec) so a corrupted
 * or tampered file on shared storage can never be run with shell privileges.
 */
object ServerExtractor {

    private const val TAG = "AppCalls:ServerExtractor"

    @WorkerThread
    fun ensureServerFile(context: Context, serverPath: String): Boolean {
        val file = File(serverPath)
        if (file.exists() && verifyServerHash(file)) {
            AppCallsLogger.d(TAG, "Server file already present and verified at $serverPath")
            return true
        }
        AppCallsLogger.d(TAG, "Server file absent or hash mismatch, extracting from assets...")
        return extractFromAssets(context, file)
    }

    private fun extractFromAssets(context: Context, destFile: File): Boolean {
        return try {
            context.assets.open(BuildConfig.SCRCPY_SERVER_ASSET_NAME).use { inputStream ->
                writeFile(destFile, inputStream)
            }
            val verified = verifyServerHash(destFile)
            if (verified) {
                AppCallsLogger.d(TAG, "Server extracted and verified: ${destFile.path}")
            } else {
                AppCallsLogger.w(TAG, "Server extraction succeeded but hash verification FAILED")
            }
            verified
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Asset extraction failed: ${e.message}")
            false
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun writeFile(destFile: File, input: InputStream) {
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { output ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead = input.read(buffer)
            while (bytesRead > 0) {
                output.write(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        // World-readable so the shell process (UID 2000) can open it - it cannot read this app's
        // private data directory, but shared-storage files marked readable here are visible to it.
        destFile.setReadable(true, false)
    }

    fun verifyServerHash(file: File): Boolean {
        if (!file.exists()) {
            AppCallsLogger.e(TAG, "Cannot verify: file not found at ${file.path}")
            return false
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val matches = actualHash.equals(ScrcpyConfig.EXPECTED_SERVER_SHA256, ignoreCase = true)
            if (!matches) {
                AppCallsLogger.w(TAG, "SHA-256 mismatch: expected=${ScrcpyConfig.EXPECTED_SERVER_SHA256} actual=$actualHash")
            }
            matches
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Hash verification error: ${e.message}", e)
            false
        }
    }
}
