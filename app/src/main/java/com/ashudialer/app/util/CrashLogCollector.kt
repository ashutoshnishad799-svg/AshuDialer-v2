package com.ashudialer.app.util

import android.content.Context
import com.ashudialer.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves a local, on-device record of the app's own uncaught crashes, so Help & Feedback has
 * something real to attach when the user chooses to send feedback - nothing here sends
 * anything anywhere by itself, and nothing here is uploaded automatically. This intentionally
 * adds no new library (no Crashlytics or similar): every other repository in this app already
 * keeps to a "nothing leaves the device unless the user explicitly shares it" rule (see
 * ContactShareHelper, LocalBackupRepository), and a crash reporter is exactly the kind of thing
 * that quietly phones home by default, so it stays local and explicit like everything else here.
 *
 * WHAT THIS DOES: wraps Thread.defaultUncaughtExceptionHandler. When the app crashes, the wrapper
 * writes a small text file (timestamp, app version, device model/OS version, and the stack trace)
 * to this app's own cache directory, then always calls through to whatever handler was installed
 * before it, so the OS's normal crash behavior (process death, any system dialog) is completely
 * unaffected - this only ever adds a file write in front of that, never replaces or suppresses it.
 *
 * WHAT IT CANNOT DO: catch crashes that happen so early or so severely that this handler itself
 * never runs (a native crash below the JVM, or the process being killed outright), and it cannot
 * recover the app from a crash - recovery is still the OS's job, this only leaves a note behind
 * about what happened. The write itself is synchronous plain File I/O (no coroutines, no
 * DataStore) because a coroutine dispatch is not guaranteed to run before a dying process is torn
 * down; if the write itself fails for any reason (disk full, permissions), that failure is
 * swallowed so it can never stop the real handler from running afterward.
 */
object CrashLogCollector {

    private const val FOLDER_NAME = "crash_logs"
    private const val MAX_KEPT = 5

    /** Call once, as early as possible in Application.onCreate(). Safe to call more than once. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let a problem while saving the report mask the real crash below.
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val folder = File(context.cacheDir, FOLDER_NAME).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(folder, "crash_$stamp.txt")

        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        file.writeText(
            buildString {
                appendLine("Ashu Dialer crash report")
                appendLine("Time: ${Date()}")
                appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(stackTrace)
            }
        )

        // Keep only the most recent MAX_KEPT reports so this folder can never grow without bound.
        folder.listFiles()
            ?.filter { it.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_KEPT)
            ?.forEach { it.delete() }
    }

    /** The most recent crash report, if any exists, newest first. Null if the app has never crashed. */
    fun latestCrashReport(context: Context): File? =
        File(context.cacheDir, FOLDER_NAME)
            .listFiles()
            ?.filter { it.name.startsWith("crash_") }
            ?.maxByOrNull { it.lastModified() }
}
