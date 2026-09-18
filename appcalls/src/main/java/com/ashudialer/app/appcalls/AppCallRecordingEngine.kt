// Adapted from ShizuCallRecorder's AudioRecordingEngine (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioCodec
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioMuxer
import com.ashudialer.app.appcalls.scrcpy.ScrcpyClient
import com.ashudialer.app.appcalls.scrcpy.ScrcpyConfig
import com.ashudialer.app.appcalls.scrcpy.ServerExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the WhatsApp/Telegram VoIP audio-capture pipeline: connects to the shell service via
 * Shizuku, reads scrcpy-server's audio pipe, and writes the result into [outputFile].
 *
 * This exists specifically because [com.ashudialer.app.telecom.CallRecorder] (AshuDialer's
 * native engine) cannot reach VoIP calls at all - there's no modem-level VOICE_CALL tap for a
 * call placed inside a third-party app. OUTPUT (REMOTE_SUBMIX) is the one source confirmed to
 * work for this: PLAYBACK hard-excludes audio tagged USAGE_VOICE_COMMUNICATION (how WhatsApp/
 * Telegram tag call audio), and any MIC-class source competes with the calling app's own
 * concurrent mic session and gets silenced by Android's privacy protections.
 *
 * Call [start] to begin, [stop] to finish and finalize the output file, or [cancel] to abort and
 * delete a partial recording.
 */
class AppCallRecordingEngine(private val context: Context) {

    companion object {
        private const val TAG = "AppCalls:RecordingEngine"
    }

    private var scrcpyClient: ScrcpyClient? = null
    private var scrcpyAudioMuxer: ScrcpyAudioMuxer? = null
    private var audioReadPipePfd: ParcelFileDescriptor? = null
    private var outputStream: FileOutputStream? = null
    private var audioPipeReadScope: CoroutineScope? = null
    private var audioPipeReadJob: Job? = null
    private var currentOutputFile: File? = null

    @Volatile
    var isPaused: Boolean = false

    val isActive: Boolean
        get() = scrcpyClient != null

    /**
     * Starts the pipeline. Requires an already-connected [shellService] (see
     * [ShizukuConnectionManager.getShellService]) - this class does not manage the Shizuku
     * connection itself, so callers control that lifecycle independently.
     *
     * @param bitRate AAC bitrate in bps; 0 or negative uses [ScrcpyAudioCodec.AAC]'s default.
     * @throws AppCallPipelineException on any setup failure, with a message safe to show the user.
     */
    fun start(shellService: IShellService, outputFile: File, bitRate: Int = -1) {
        currentOutputFile = outputFile
        val resolvedBitRate = bitRate.takeIf { it > 0 } ?: ScrcpyAudioCodec.AAC.defaultBitRate

        val serverPath = ScrcpyConfig.getServerPath(context)
        if (!ServerExtractor.ensureServerFile(context, serverPath)) {
            throw AppCallPipelineException("Couldn't prepare the recording helper file. Try again.")
        }

        val stream = try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile)
        } catch (e: Exception) {
            throw AppCallPipelineException("Couldn't create the recording file.", e)
        }
        outputStream = stream

        scrcpyAudioMuxer = ScrcpyAudioMuxer(stream.fd, outputFile.name)

        val inputPfd = try {
            shellService.startRecording(
                com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource.OUTPUT.cliKey,
                ScrcpyAudioCodec.AAC.cliKey,
                resolvedBitRate,
                serverPath,
                false,
                object : ILogCallback.Stub() {
                    override fun onLogEvent(level: String, tag: String, message: String, throwableStackTrace: String?) {
                        AppCallsLogger.d("$TAG:remote", "[$tag] $message")
                    }
                }
            )
        } catch (e: Exception) {
            releaseQuietly()
            throw AppCallPipelineException("Couldn't reach the recording helper process.", e)
        }

        if (inputPfd == null) {
            releaseQuietly()
            throw AppCallPipelineException("Recording helper process didn't start.")
        }
        audioReadPipePfd = inputPfd

        scrcpyAudioMuxer?.initialize(ScrcpyAudioCodec.AAC)

        scrcpyClient = ScrcpyClient(
            inputPfd = inputPfd,
            expectedCodec = ScrcpyAudioCodec.AAC,
            listener = object : ScrcpyClient.AudioPacketListener {
                override fun onMetadataReceived(codec: ScrcpyAudioCodec) {
                    scrcpyAudioMuxer?.initialize(codec)
                }
                override fun onAudioPacket(packet: ScrcpyClient.AudioPacket) {
                    if (isPaused) return
                    scrcpyAudioMuxer?.writePacket(packet, ScrcpyAudioCodec.AAC)
                }
                override fun onStreamEnd(error: String?) {
                    if (error != null) {
                        AppCallsLogger.w(TAG, "Audio stream ended with error: $error")
                    } else {
                        AppCallsLogger.d(TAG, "Audio stream ended normally (EOF)")
                    }
                }
            }
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        audioPipeReadScope = scope
        audioPipeReadJob = scope.launch(Dispatchers.IO) {
            try {
                scrcpyClient?.start()
            } catch (e: Exception) {
                AppCallsLogger.w(TAG, "Audio reader ended: ${e.message}")
            }
        }
    }

    /** Stops the pipeline and finalizes the output file. Safe to call even if [start] never fully succeeded. */
    fun stop(shellService: IShellService?) {
        AppCallsLogger.i(TAG, "Stopping app-call recording pipeline...")
        runCatching { shellService?.stopRecording() }

        runCatching {
            runBlocking { withTimeoutOrNull(2000L) { audioPipeReadJob?.join() } }
        }

        runCatching { scrcpyClient?.stop() }
        runCatching { audioPipeReadScope?.cancel() }
        runCatching { audioReadPipePfd?.close() }
        runCatching { scrcpyAudioMuxer?.close() }
        runCatching { outputStream?.close() }

        scrcpyClient = null
        scrcpyAudioMuxer = null
        audioReadPipePfd = null
        outputStream = null
        audioPipeReadScope = null
        audioPipeReadJob = null
        currentOutputFile = null
    }

    /** Stops and deletes the (incomplete) output file - use when a start attempt fails partway through. */
    fun cancel(shellService: IShellService?) {
        val file = currentOutputFile
        stop(shellService)
        try {
            file?.delete()
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Failed to clean up file after cancelled start", e)
        }
    }

    private fun releaseQuietly() {
        runCatching { outputStream?.close() }
        runCatching { scrcpyAudioMuxer?.close() }
        outputStream = null
        scrcpyAudioMuxer = null
        currentOutputFile = null
    }
}

/** Carries a message safe to show the user directly, separate from the technical cause. */
class AppCallPipelineException(userFriendlyMessage: String, cause: Throwable? = null) : Exception(userFriendlyMessage, cause)
