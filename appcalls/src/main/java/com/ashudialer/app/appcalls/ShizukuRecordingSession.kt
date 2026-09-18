package com.ashudialer.app.appcalls

import android.content.Context
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * One foreground-call recording session backed only by Shizuku.
 * The app process owns the output file; the privileged capture process is
 * the Shizuku user service which launches the bundled scrcpy server.
 */
class ShizukuRecordingSession(private val context: Context) {
    private var manager: ShizukuConnectionManager? = null
    private var engine: AppCallRecordingEngine? = null
    private var shellService: IShellService? = null

    val isActive: Boolean
        get() = engine?.isActive == true

    fun start(outputFile: File, source: ScrcpyAudioSource = ScrcpyAudioSource.VOICE_CALL, bitRate: Int = 64000) {
        if (isActive) return
        val m = ShizukuConnectionManager(context.applicationContext)
        val service = runBlocking(Dispatchers.IO) { m.getShellService() }
        val e = AppCallRecordingEngine(context.applicationContext)
        try {
            e.start(service, outputFile, bitRate, source.cliKey)
            manager = m
            engine = e
            shellService = service
        } catch (t: Throwable) {
            runCatching { e.cancel(service) }
            runCatching { m.unbind() }
            throw t
        }
    }

    fun stop() {
        val e = engine ?: return
        val s = shellService
        runCatching { e.stop(s) }
        runCatching { manager?.unbind() }
        engine = null
        shellService = null
        manager = null
    }

    fun cancel() {
        val e = engine ?: return
        val s = shellService
        runCatching { e.cancel(s) }
        runCatching { manager?.unbind() }
        engine = null
        shellService = null
        manager = null
    }

    companion object {
        fun isReady(context: Context): Boolean =
            ShizukuConnectionManager.isAvailable() &&
                ShizukuConnectionManager.hasPermission(context) &&
                ShizukuConnectionManager.checkServerPermission(android.Manifest.permission.CAPTURE_AUDIO_OUTPUT)
    }
}
