package com.ashudialer.app.appcalls

import android.util.Log

/**
 * Small wrapper so this module's logging can be initialized to forward into the shell process's
 * own callback when running inside ShellService (see ShellService.kt), and to plain Log.* when
 * running in the app process - mirrors AppLogger's dual-mode design in upstream
 * ShizuCallRecorder, trimmed to only what this module needs.
 */
object AppCallsLogger {
    @Volatile private var remoteCallback: ((level: String, tag: String, message: String, stackTrace: String?) -> Unit)? = null
    @Volatile private var verbose: Boolean = false

    /** Called once inside the shell process (ShellService's constructor) to relay logs back to the app process. */
    fun initAsRemote(callback: (level: String, tag: String, message: String, stackTrace: String?) -> Unit, verboseLogging: Boolean) {
        remoteCallback = callback
        verbose = verboseLogging
    }

    fun d(tag: String, message: String) = emit("D", tag, message, null)
    fun i(tag: String, message: String) = emit("I", tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = emit("W", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = emit("E", tag, message, throwable)
    fun v(tag: String, message: String) { if (verbose) emit("V", tag, message, null) }

    private fun emit(level: String, tag: String, message: String, throwable: Throwable?) {
        val cb = remoteCallback
        if (cb != null) {
            runCatching { cb(level, tag, message, throwable?.stackTraceToString()) }
            return
        }
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message, throwable)
            "E" -> Log.e(tag, message, throwable)
            "V" -> Log.v(tag, message)
        }
    }
}
