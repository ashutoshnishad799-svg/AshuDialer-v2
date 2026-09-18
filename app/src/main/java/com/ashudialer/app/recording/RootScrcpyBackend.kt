package com.ashudialer.app.recording

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyAudioCodec
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyAudioSource
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyConfig
import com.ashudialer.app.recording.integrations.scrcpy.ServerExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct root backend used by the root/Magisk/KernelSU product flavor.
 * It deliberately does not talk to Shizuku: `su` launches the same
 * scrcpy-server audio process as UID 0, so a rooted install can start
 * recording immediately after boot once the APK is installed as the
 * privileged Ashu Dialer package.
 */
class RootScrcpyBackend(private val context: android.content.Context) {
    companion object {
        private const val TAG = "AshuRootRecorder"
        private const val BUFFER_SIZE = 32 * 1024
    }

    private val active = AtomicBoolean(false)
    private var process: Process? = null
    private var serverSocket: LocalServerSocket? = null
    private var clientSocket: LocalSocket? = null
    private var writeEnd: ParcelFileDescriptor? = null
    private var scope: CoroutineScope? = null
    private var relayJob: Job? = null

    fun isRootAvailable(): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return false
            }
            p.exitValue() == 0 && output.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
    }

    fun start(
        serverPath: String,
        audioSource: ScrcpyAudioSource,
        codec: ScrcpyAudioCodec
    ): ParcelFileDescriptor? {
        if (!active.compareAndSet(false, true)) return null
        try {
            check(isRootAvailable()) { "root/su is not available" }
            check(ServerExtractor.verifyServerHash(java.io.File(serverPath))) { "invalid scrcpy server" }

            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            writeEnd = pipe[1]

            val socketName = ScrcpyConfig.getRandomSocketName()
            val fullSocketName = ScrcpyConfig.SERVER_SOCKET_NAME_PREFIX + socketName
            serverSocket = LocalServerSocket(fullSocketName)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            relayJob = scope?.launch(Dispatchers.IO) {
                try {
                    val socket = serverSocket?.accept() ?: return@launch
                    clientSocket = socket
                    val input = socket.inputStream
                    val output = ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (active.get()) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0) {
                            output.write(buffer, 0, n)
                            output.flush()
                        }
                    }
                    runCatching { output.flush() }
                } catch (t: Throwable) {
                    if (active.get()) Log.w(TAG, "root audio relay stopped", t)
                }
            }

            val args = ScrcpyConfig.buildServerArgs(
                socketName = socketName,
                audioSource = audioSource,
                audioCodec = codec,
                audioBitRate = codec.defaultBitRate
            )
            val command = buildString {
                append("export CLASSPATH=")
                append(shellQuote(serverPath))
                append("; exec /system/bin/app_process / ")
                append(ScrcpyConfig.SERVER_MAIN_CLASS)
                args.forEach { append(' ').append(shellQuote(it)) }
            }
            val p = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = p

            // Drain root process logs so stderr/stdout can never fill and stall
            // scrcpy-server during a long call.
            scope?.launch(Dispatchers.IO) {
                runCatching { p.inputStream.copyTo(OutputStream.nullOutputStream()) }
            }
            scope?.launch(Dispatchers.IO) {
                val code = runCatching { p.waitFor() }.getOrNull()
                if (active.get()) Log.w(TAG, "scrcpy root process exited early: $code")
            }

            return readEnd
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to start direct root recorder", t)
            stop()
            return null
        }
    }

    fun stop() {
        if (!active.compareAndSet(true, false)) return
        runCatching { process?.destroy() }
        runCatching { process?.waitFor(2, TimeUnit.SECONDS) }
        runCatching {
            runBlocking { withTimeoutOrNull(2000L) { relayJob?.join() } }
        }
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { writeEnd?.close() }
        runCatching { scope?.cancel() }
        process = null
        serverSocket = null
        clientSocket = null
        writeEnd = null
        scope = null
        relayJob = null
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
