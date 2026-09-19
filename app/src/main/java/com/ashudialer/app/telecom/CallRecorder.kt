package com.ashudialer.app.telecom

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.ashudialer.app.appcalls.recording.CallDirection
import com.ashudialer.app.appcalls.recording.RecordingForegroundService
import com.ashudialer.app.appcalls.recording.RecordingLibrary
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.appcalls.recording.RecordingSession
import java.io.File

/**
 * How a recording is being captured. With Shizuku there is one real pipeline (scrcpy audio capture
 * run as the shell user); the values below just describe which audio source it is using so the
 * in-call UI can label it. [FAILED] means nothing is recording.
 */
enum class RecordingMode { VOICE_CALL, VOICE_UPLINK_DOWNLINK, VOICE_COMMUNICATION, MICROPHONE, APP_CALL, FAILED }

data class RecordingResult(
    val file: File,
    val mode: RecordingMode,
    val callerLabel: String
)

private const val RECORDINGS_FOLDER_NAME = "Ashu Dialer"

/**
 * The in-call screen's handle on call recording.
 *
 * This used to drive MediaRecorder directly, which Android only allows for privileged / rooted
 * apps. It is now a thin controller for [RecordingForegroundService] (the Shizuku-based recorder
 * that also handles automatic recording from the phone-state broadcast): the record button just
 * asks the service to start or stop, and the state getters read the service's live state.
 *
 * The companion object keeps the recordings-library helpers (list / delete / Private Space) that
 * the Recordings screen already relies on.
 */
class CallRecorder(private val context: Context) {

    private val prefs = RecordingPrefs(context)
    private var lastLabel: String = ""

    /** True while the service is actually capturing audio (not merely waiting in standby). */
    val isRecording: Boolean
        get() = RecordingForegroundService.isRecordingNow

    fun currentMode(): RecordingMode? {
        if (!isRecording) return null
        return when (prefs.audioSource.cliKey) {
            "voice-call" -> RecordingMode.VOICE_CALL
            "voice-call-uplink", "voice-call-downlink" -> RecordingMode.VOICE_UPLINK_DOWNLINK
            "mic-voice-communication" -> RecordingMode.VOICE_COMMUNICATION
            "output" -> RecordingMode.APP_CALL
            else -> RecordingMode.MICROPHONE
        }
    }

    fun currentCallerLabel(): String? = lastLabel.takeIf { isRecording && it.isNotBlank() }

    /**
     * Asks the recording service to start now (the manual record button, or auto-record on answer).
     * Returns immediately: the service connects to Shizuku and starts capture in the background,
     * and [isRecording] flips to true once audio is actually flowing. Returns [RecordingMode.FAILED]
     * only when recording is impossible before even trying (feature off).
     */
    fun start(callerLabel: String): RecordingMode {
        if (!prefs.callRecordingEnabled) {
            Log.w("CallRecorder", "start() ignored: call recording is switched off")
            return RecordingMode.FAILED
        }
        lastLabel = callerLabel
        val session = RecordingSession(
            phoneNumber = callerLabel.takeIf { it.any(Char::isDigit) },
            direction = CallDirection.OUTGOING,
            contactName = callerLabel.takeIf { name -> name.any(Char::isLetter) }
        )
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_MANUAL_START
            putExtra(RecordingSession.EXTRA_SESSION, session)
        }
        return try {
            context.startForegroundService(intent)
            startedAt = SystemClock.elapsedRealtime()
            // The service connects to Shizuku and opens the capture pipeline asynchronously.
            // The in-call screen treats a non-FAILED result as "recording is on" and immediately
            // polls isRecording, so wait (bounded, and only on the caller's IO thread) until the
            // service really is capturing. Otherwise the record button would flash on and then
            // snap back off while the pipeline was still starting.
            val deadline = SystemClock.elapsedRealtime() + START_WAIT_MS
            while (!isRecording && SystemClock.elapsedRealtime() < deadline) {
                // Service gave up (Shizuku missing / no permission): stop waiting right away.
                if (!RecordingForegroundService.isRunning && SystemClock.elapsedRealtime() - startedAt > 1500L) break
                Thread.sleep(POLL_MS)
            }
            if (isRecording) currentMode() ?: modeForConfiguredSource()
            else {
                Log.w("CallRecorder", "Recording did not start within ${START_WAIT_MS}ms (is Shizuku running and allowed?)")
                RecordingMode.FAILED
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "Couldn't start the recording service", e)
            RecordingMode.FAILED
        }
    }

    private var startedAt: Long = 0L

    private fun modeForConfiguredSource(): RecordingMode = when (prefs.audioSource.cliKey) {
        "voice-call" -> RecordingMode.VOICE_CALL
        "voice-call-uplink", "voice-call-downlink" -> RecordingMode.VOICE_UPLINK_DOWNLINK
        "mic-voice-communication" -> RecordingMode.VOICE_COMMUNICATION
        else -> RecordingMode.MICROPHONE
    }

    fun elapsedSeconds(): Int {
        val since = RecordingForegroundService.startedAtElapsedMs
        if (!isRecording || since <= 0L) return 0
        return ((SystemClock.elapsedRealtime() - since) / 1000L).toInt().coerceAtLeast(0)
    }

    /** Shizuku capture has no MediaRecorder amplitude tap; report "signal present" so the silence warning stays quiet. */
    fun currentAmplitude(): Int = if (isRecording) 1 else 0

    /** Not needed with Shizuku: the audio source is chosen in settings, not auto-switched mid-call. */
    fun restartOnSustainedSilence(): RecordingMode? = null

    /**
     * Stops recording. The service finalizes and saves the file itself and posts the "Recording
     * saved" notification, so there is nothing to return here (kept as [RecordingResult]? for
     * source compatibility with existing callers).
     */
    fun stop(): RecordingResult? {
        try {
            context.startService(
                Intent(context, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_STOP_RECORDING)
            )
        } catch (e: Exception) {
            Log.w("CallRecorder", "stop(): couldn't reach the recording service: ${e.message}")
        }
        lastLabel = ""
        startedAt = 0L
        return null
    }

    companion object {
        private const val PRIVATE_SPACE_RECORDINGS_DIR = "private_space_recordings"
        private const val START_WAIT_MS = 8_000L
        private const val POLL_MS = 100L

        fun moveToPrivateSpace(context: Context, publicFile: File): File? {
            return try {
                val privateDir = File(context.filesDir, PRIVATE_SPACE_RECORDINGS_DIR).apply { mkdirs() }
                val destination = File(privateDir, publicFile.name)
                publicFile.copyTo(destination, overwrite = true)

                try {
                    val resolver = context.contentResolver
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    resolver.delete(collection, "${MediaStore.Audio.Media.DATA} = ?", arrayOf(publicFile.absolutePath))
                } catch (e: Exception) {
                    Log.w("CallRecorder", "Couldn't remove MediaStore row for moved recording", e)
                }
                if (publicFile.exists()) publicFile.delete()

                destination
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to move recording to Private Space", e)
                null
            }
        }

        fun moveOutOfPrivateSpace(context: Context, privateFile: File): File? {
            return try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, privateFile.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGS_FOLDER_NAME")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { out -> privateFile.inputStream().use { it.copyTo(out) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                privateFile.delete()

                queryMediaStoreRecordings(context).firstOrNull { it.name == privateFile.name }
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to move recording out of Private Space", e)
                null
            }
        }

        fun listPrivateSpaceRecordings(context: Context): List<File> {
            val dir = File(context.filesDir, PRIVATE_SPACE_RECORDINGS_DIR)
            if (!dir.exists()) return emptyList()
            return (dir.listFiles()?.toList() ?: emptyList()).sortedByDescending { it.lastModified() }
        }

        fun wipeAllPrivateSpaceRecordings(context: Context) {
            val dir = File(context.filesDir, PRIVATE_SPACE_RECORDINGS_DIR)
            if (!dir.exists()) return
            dir.listFiles()?.forEach { it.delete() }
        }

        /** All finished recordings (Music/Ashu Dialer + private app folder), newest first. */
        fun listRecordings(context: Context): List<File> = RecordingLibrary.listAll(context)

        /** Deletes a recording from disk and MediaStore. Returns true once it is really gone. */
        fun deleteRecording(context: Context, file: File): Boolean = RecordingLibrary.delete(context, file)

        /** Kept for callers that used to reset the old MediaRecorder "known silent sources" memory; nothing to reset now. */
        fun clearKnownSilentSources(context: Context) {}

        private fun queryMediaStoreRecordings(context: Context): List<File> = RecordingLibrary.listAll(context)
    }
}
