// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import com.ashudialer.app.appcalls.scrcpy.ScrcpyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * ShellService runs inside the privileged shell process (UID 2000, or 0 on a rooted device)
 * that Shizuku spawns and manages. Running under that identity lets it launch scrcpy-server via
 * `app_process` and capture audio a normal app process cannot reach.
 *
 *   Shell Process (UID 2000/0)
 *     ShellService (this class, AIDL stub)
 *       |- launches scrcpy-server (app_process) -> connects to our LocalServerSocket
 *       |- AudioRelayCoroutine: socket -> pipe write-end (kept in shell)
 *       |                                pipe read-end -> app process's ScrcpyClient
 *       |- LogConsumerCoroutine: drains scrcpy-server's stdout
 *       `- ProcessMonitorCoroutine: waits for scrcpy-server exit
 *
 * Shizuku requirements: must have a no-arg constructor AND a single-Context constructor
 * (Shizuku v13+), must be @Keep so R8 doesn't strip/rename it, and destroy() must call
 * exitProcess() to actually terminate the shell process when Shizuku asks it to.
 */
@Keep
class ShellService : IShellService.Stub {

    private companion object {
        const val TAG = "AppCalls:ShellService"

        /** 32 KB relay buffer - a balance between latency and syscall overhead at typical AAC bitrates. */
        const val RELAY_BUFFER_SIZE = 32 * 1024

        /** Grace period after Process.destroy() so scrcpy-server can flush its last audio frame before the pipe closes. */
        const val PROCESS_STOP_GRACE_PERIOD_SEC = 2L
    }

    private val isRecordingActive = AtomicBoolean(false)

    private var scrcpyProcess: Process? = null
    private var serverSocket: LocalServerSocket? = null
    private var clientConnection: LocalSocket? = null

    /**
     * Write end of the kernel pipe. Do NOT close this before scrcpy-server exits - it may still
     * be buffering its final frame and will write it after SIGTERM; closing early would truncate
     * the recording with a broken-pipe error in the relay coroutine.
     */
    private var audioWriteEnd: ParcelFileDescriptor? = null

    private var shellScope: CoroutineScope? = null
    private var audioPipeRelayJob: Job? = null

    @Keep constructor() : this(null)

    /** @param context The shell process's own Context, supplied by Shizuku v13+ (or null on older versions). */
    @Keep constructor(context: Context?) {
        Log.i(TAG, "ShellService process started, running as UID=${android.os.Process.myUid()}")
    }

    override fun startRecording(
        audioSource: String,
        audioCodec: String,
        audioBitRate: Int,
        serverPath: String,
        isDebuggingModeEnabled: Boolean,
        listener: ILogCallback
    ): ParcelFileDescriptor? {
        AppCallsLogger.initAsRemote(
            { level, tag, message, stack -> listener.onLogEvent(level, tag, message, stack) },
            isDebuggingModeEnabled
        )

        if (isRecordingActive.get()) {
            AppCallsLogger.w(TAG, "startRecording() rejected: a session is already active")
            return null
        }

        try {
            AppCallsLogger.i(TAG, "Initialising the ShellService recording pipeline...")

            // Security check: verify the jar's SHA-256 before exec, in the shell process
            // itself (reduces, though doesn't fully eliminate, a TOCTOU window).
            val serverJarFile = File(serverPath)
            if (!serverJarFile.exists() || !com.ashudialer.app.appcalls.scrcpy.ServerExtractor.verifyServerHash(serverJarFile)) {
                AppCallsLogger.w(TAG, "Server jar absent or SHA-256 mismatch at $serverPath - aborting")
                return null
            }

            val pipe = ParcelFileDescriptor.createPipe()
            val pipeReadEnd = pipe[0]  // returned to app process
            val pipeWriteEnd = pipe[1] // written by this service's relay coroutine
            audioWriteEnd = pipeWriteEnd

            val socketName = ScrcpyConfig.getRandomSocketName()
            val serverFullSocketName = ScrcpyConfig.SERVER_SOCKET_NAME_PREFIX + socketName
            serverSocket = LocalServerSocket(serverFullSocketName)
            AppCallsLogger.d(TAG, "Listening on abstract socket '$serverFullSocketName'")

            shellScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // Start the relay coroutine BEFORE launching scrcpy-server so accept() is already waiting.
            spawnAudioRelayCoroutine(isDebuggingModeEnabled)

            val serverArgs = ScrcpyConfig.buildServerArgs(socketName, audioBitRate)
            val launchCommand = mutableListOf("app_process", "/", ScrcpyConfig.SERVER_MAIN_CLASS)
            launchCommand.addAll(serverArgs)

            val scrcpyBuilder = ProcessBuilder(launchCommand).apply {
                environment()["CLASSPATH"] = serverPath
                redirectErrorStream(true)
            }
            scrcpyProcess = scrcpyBuilder.start()
            isRecordingActive.set(true)
            AppCallsLogger.i(TAG, "scrcpy-server launched successfully")

            spawnLogConsumerCoroutine(scrcpyProcess!!)
            spawnProcessMonitorCoroutine(scrcpyProcess!!)

            AppCallsLogger.i(TAG, "Recording pipeline established. Returning pipe read-end to app process.")
            return pipeReadEnd
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Critical failure during pipeline startup: ${e.message}", e)
            stopRecording()
            return null
        }
    }

    override fun stopRecording() {
        if (!isRecordingActive.compareAndSet(true, false)) {
            AppCallsLogger.w(TAG, "stopRecording() called but no active session - skipping")
            return
        }

        AppCallsLogger.i(TAG, "Stopping scrcpy-server process...")
        runCatching { scrcpyProcess?.destroy() }

        try {
            scrcpyProcess?.waitFor(PROCESS_STOP_GRACE_PERIOD_SEC, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            AppCallsLogger.w(TAG, "Interrupted while waiting for scrcpy-server exit: ${e.message}")
        }

        AppCallsLogger.d(TAG, "Waiting for relay coroutine to finish copying late bytes...")
        runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(2000L) { audioPipeRelayJob?.join() }
            }
        }

        runCatching { shellScope?.cancel() }
        runCatching { clientConnection?.close() }
        runCatching { serverSocket?.close() }
        // Close the write-end LAST so scrcpy-server had its chance to write final bytes.
        runCatching { audioWriteEnd?.close() }

        scrcpyProcess = null
        clientConnection = null
        serverSocket = null
        audioWriteEnd = null
        shellScope = null
        audioPipeRelayJob = null

        AppCallsLogger.i(TAG, "Recording pipeline stopped and all ShellService resources released")
    }

    override fun isRecording(): Boolean = isRecordingActive.get()

    override fun destroy() {
        AppCallsLogger.i(TAG, "ShellService.destroy() - terminating shell process")
        stopRecording()
        exitProcess(0)
    }

    private fun spawnAudioRelayCoroutine(verbose: Boolean) {
        audioPipeRelayJob = shellScope?.launch(Dispatchers.IO) {
            try {
                AppCallsLogger.d(TAG, "AudioRelayCoroutine: waiting for scrcpy-server connection...")
                val connection = serverSocket?.accept() ?: run {
                    AppCallsLogger.w(TAG, "AudioRelayCoroutine: server socket was null or closed")
                    return@launch
                }
                clientConnection = connection
                AppCallsLogger.i(TAG, "AudioRelayCoroutine: scrcpy-server connected")

                val sourceStream = connection.inputStream
                val destinationStream = ParcelFileDescriptor.AutoCloseOutputStream(audioWriteEnd)

                val buffer = ByteArray(RELAY_BUFFER_SIZE)
                var lastLogTimeMs = System.currentTimeMillis()

                while (isActive) {
                    val bytesRead = sourceStream.read(buffer)
                    if (bytesRead == -1) {
                        AppCallsLogger.d(TAG, "AudioRelayCoroutine: socket EOF - scrcpy-server disconnected")
                        break
                    }
                    destinationStream.write(buffer, 0, bytesRead)

                    if (verbose && bytesRead > 0) {
                        val now = System.currentTimeMillis()
                        if (now - lastLogTimeMs >= 1000) {
                            lastLogTimeMs = now
                            AppCallsLogger.v(TAG, "AudioRelayCoroutine: relayed $bytesRead bytes.")
                        }
                    }
                }
            } catch (e: IOException) {
                if (isRecordingActive.get()) {
                    AppCallsLogger.e(TAG, "AudioRelayCoroutine: unexpected I/O error: ${e.message}", e)
                } else {
                    AppCallsLogger.d(TAG, "AudioRelayCoroutine: I/O error during shutdown (expected): ${e.message}")
                }
            } finally {
                AppCallsLogger.d(TAG, "AudioRelayCoroutine finished")
                stopRecording()
            }
        }
    }

    private fun spawnLogConsumerCoroutine(process: Process) {
        shellScope?.launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (isActive && line != null) {
                        AppCallsLogger.i(TAG, "[scrcpy-server] $line")
                        line = reader.readLine()
                    }
                }
            } catch (_: InterruptedIOException) {
                AppCallsLogger.d(TAG, "LogConsumerCoroutine: interrupted (expected during shutdown)")
            } catch (e: IOException) {
                AppCallsLogger.e(TAG, "LogConsumerCoroutine: I/O error: ${e.message}", e)
            } finally {
                AppCallsLogger.d(TAG, "LogConsumerCoroutine finished")
            }
        }
    }

    private fun spawnProcessMonitorCoroutine(process: Process) {
        shellScope?.launch(Dispatchers.IO) {
            try {
                val exitCode = process.waitFor()
                if (exitCode != 0 && isRecordingActive.get()) {
                    AppCallsLogger.e(TAG, "ProcessMonitorCoroutine: scrcpy-server crashed (exit code $exitCode)")
                    stopRecording()
                } else {
                    AppCallsLogger.i(TAG, "ProcessMonitorCoroutine: scrcpy-server exited normally (code $exitCode)")
                }
            } catch (_: InterruptedException) {
                AppCallsLogger.d(TAG, "ProcessMonitorCoroutine: interrupted (expected during shutdown)")
            } finally {
                AppCallsLogger.d(TAG, "ProcessMonitorCoroutine finished")
            }
        }
    }
}
