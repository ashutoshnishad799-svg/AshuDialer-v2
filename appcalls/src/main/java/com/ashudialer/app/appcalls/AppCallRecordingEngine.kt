// Adapted from ShizuCallRecorder's AudioRecordingEngine (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioCodec
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioMuxer
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource
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
 * The single Shizuku-backed capture pipeline used for EVERYTHING this app records:
 * normal phone calls (voice-call / mic-voice-communication / uplink / downlink) and
 * WhatsApp/Telegram VoIP calls (output). Same job Ever Dialer's AudioRecordingEngine does.
 *
 * Flow: connect to the shell service via Shizuku -> scrcpy-server captures audio as the shell
 * user -> bytes come back over a kernel pipe -> [ScrcpyClient] parses packets -> [ScrcpyAudioMuxer]
 * writes them into an .m4a / .ogg file.
 *
 * Call [start] to begin, [stop] to finish and finalize the file, or [cancel] to abort and delete
 * a partial recording.
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

    /** Codec the stream header actually confirmed; may differ from what was requested. */
    @Volatile
    var activeCodec: ScrcpyAudioCodec = ScrcpyAudioCodec.AAC
        private set

    @Volatile
    var isPaused: Boolean = false

    val isActive: Boolean
        get() = scrcpyClient != null

    /** True while the pipe reader coroutine is genuinely running (used by the foreground service). */
    val isReading: Boolean
        get() = audioPipeReadJob?.isActive == true

    /**
     * Starts the pipeline. Requires an already-connected [shellService] (see
     * [ShizukuConnectionManager.getShellService]).
     *
     * @param source  What to capture. WhatsApp/Telegram callers pass [ScrcpyAudioSource.OUTPUT].
     * @param codec   AAC or Opus.
     * @param bitRate bps; 0 or negative uses the codec's default.
     * @throws AppCallPipelineException on any setup failure, with a message safe to show the user.
     */
    fun start(
        shellService: IShellService,
        outputFile: File,
        source: ScrcpyAudioSource = ScrcpyAudioSource.OUTPUT,
        codec: ScrcpyAudioCodec = ScrcpyAudioCodec.AAC,
        bitRate: Int = -1,
        debug: Boolean = false
    ) {
        currentOutputFile = outputFile
        activeCodec = codec
        val resolvedBitRate = bitRate.takeIf { it > 0 } ?: codec.defaultBitRate

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
                source.cliKey,
                codec.cliKey,
                resolvedBitRate,
                serverPath,
                debug,
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
            throw AppCallPipelineException(
                "Recording helper didn't start. Check that Shizuku is running and this app has Shizuku permission."
            )
        }
        audioReadPipePfd = inputPfd

        scrcpyAudioMuxer?.initialize(codec)

        scrcpyClient = ScrcpyClient(
            inputPfd = inputPfd,
            expectedCodec = codec,
            listener = object : ScrcpyClient.AudioPacketListener {
                override fun onMetadataReceived(codec: ScrcpyAudioCodec) {
                    activeCodec = codec
                    scrcpyAudioMuxer?.initialize(codec)
                }

                override fun onAudioPacket(packet: ScrcpyClient.AudioPacket) {
                    if (isPaused) return
                    scrcpyAudioMuxer?.writePacket(packet, activeCodec)
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
        AppCallsLogger.i(TAG, "Stopping recording pipeline...")
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
    }

    /** Stops and deletes the (incomplete) output file - use when a start attempt fails partway through. */
    fun cancel(shellService: IShellService?) {
        val file = currentOutputFile
        stop(shellService)
        currentOutputFile = null
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
    }
}

/** Carries a message safe to show the user directly, separate from the technical cause. */
class AppCallPipelineException(userFriendlyMessage: String, cause: Throwable? = null) : Exception(userFriendlyMessage, cause)
