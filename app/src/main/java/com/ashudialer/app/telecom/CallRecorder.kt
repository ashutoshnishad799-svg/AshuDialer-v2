package com.ashudialer.app.telecom

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.ashudialer.app.BuildConfig
import com.ashudialer.app.recording.RootScrcpyBackend
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyAudioCodec
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyAudioMuxer
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyAudioSource
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyClient
import com.ashudialer.app.recording.integrations.scrcpy.ScrcpyConfig
import com.ashudialer.app.recording.integrations.scrcpy.ServerExtractor
import com.ashudialer.app.recording.integrations.shizuku.ShizukuConnectionManager
import com.ashudialer.app.recording.IShellService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stable two-way call recorder controller.
 *
 * Backend order:
 *  - root flavor: direct `su` -> scrcpy-server, no Shizuku required;
 *  - normal flavor: Shizuku ShellService -> scrcpy-server;
 *  - final fallback: ordinary MIC MediaRecorder so the recording feature never
 *    disappears completely when elevated capture is unavailable.
 *
 * The important change from the old implementation is that the normal/root
 * elevated path never depends on MediaRecorder.AudioSource.VOICE_CALL being
 * exposed to the app UID. The privileged shell/root process owns that tap.
 */
enum class RecordingMode {
    VOICE_CALL,
    VOICE_UPLINK_DOWNLINK,
    VOICE_COMMUNICATION,
    MICROPHONE,
    FAILED
}

data class RecordingResult(
    val file: File,
    val mode: RecordingMode,
    val callerLabel: String
)

private const val RECORDINGS_FOLDER_NAME = "Ashu Dialer"

class CallRecorder(private val context: Context) {
    private val appContext = context.applicationContext
    private val rootBackend = RootScrcpyBackend(appContext)
    private var shizukuManager: ShizukuConnectionManager? = null
    private var shellService: IShellService? = null
    private var rootSessionActive = false

    private var scrcpyClient: ScrcpyClient? = null
    private var scrcpyReadPfd: ParcelFileDescriptor? = null
    private var muxer: ScrcpyAudioMuxer? = null
    private var outputStream: FileOutputStream? = null
    private var tempOutputFile: File? = null
    private var readScope: CoroutineScope? = null
    private var readJob: Job? = null
    private var recordingStartedAtMs: Long = 0L
    private var lastPacketAtMs: Long = 0L

    private var micRecorder: MediaRecorder? = null
    private var currentMode: RecordingMode = RecordingMode.FAILED
    private var currentCallerLabel: String = ""

    val isRecording: Boolean
        get() = scrcpyClient != null || micRecorder != null

    fun currentMode(): RecordingMode? = currentMode.takeIf { isRecording && it != RecordingMode.FAILED }

    fun currentCallerLabel(): String? = currentCallerLabel.takeIf { isRecording && it.isNotBlank() }

    fun elapsedSeconds(): Int {
        val start = recordingStartedAtMs
        return if (start <= 0L) 0 else ((SystemClock.elapsedRealtime() - start) / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * Returns a non-zero activity value while encoded packets are arriving.
     * This keeps the existing in-call UI's recording indicator meaningful
     * without pretending an encoded AAC stream exposes raw PCM amplitude.
     */
    fun currentAmplitude(): Int {
        if (micRecorder != null) {
            return try { micRecorder?.maxAmplitude ?: 0 } catch (_: Throwable) { 0 }
        }
        return if (lastPacketAtMs > 0L && SystemClock.elapsedRealtime() - lastPacketAtMs < 1500L) 9000 else 0
    }

    /** The old MediaRecorder source-switch recovery is no longer needed for the elevated path. */
    fun restartOnSustainedSilence(): RecordingMode? = null

    fun start(callerLabel: String): RecordingMode {
        if (isRecording) return currentMode
        currentCallerLabel = callerLabel.ifBlank { "call" }

        val elevatedStarted = if (BuildConfig.CALL_RECORDING_USE_ROOT) {
            startRootScrcpy() || startShizukuScrcpy()
        } else {
            startShizukuScrcpy()
        }
        if (elevatedStarted) return currentMode

        // Keep a real last-resort recording feature in the normal APK. It is
        // intentionally labeled MIC in the UI because it cannot guarantee the
        // remote party's audio without an elevated backend.
        if (startMicrophoneFallback()) return currentMode

        currentMode = RecordingMode.FAILED
        currentCallerLabel = ""
        return currentMode
    }

    private fun startRootScrcpy(): Boolean {
        return try {
            val serverPath = ensureServer()
            val pipe = rootBackend.start(
                serverPath = serverPath,
                audioSource = ScrcpyAudioSource.VOICE_CALL,
                codec = ScrcpyAudioCodec.AAC
            ) ?: return false
            rootSessionActive = true
            attachScrcpyPipe(pipe, RecordingMode.VOICE_CALL)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Direct root recording backend failed", e)
            runCatching { rootBackend.stop() }
            rootSessionActive = false
            false
        }
    }

    private fun startShizukuScrcpy(): Boolean {
        return try {
            if (!ShizukuConnectionManager.isAvailable()) return false
            val manager = shizukuManager ?: ShizukuConnectionManager(appContext).also { shizukuManager = it }
            // getShellService() owns the permission-request/suspension path.
            // Do not return false immediately after launching the Shizuku dialog: on
            // a first-run device the recording tap should continue automatically once
            // the user grants permission.
            val connected = runBlocking {
                manager.getShellService()
            }
            val pipe = connected.startRecording(
                ScrcpyAudioSource.VOICE_CALL.cliKey,
                ScrcpyAudioCodec.AAC.cliKey,
                ScrcpyAudioCodec.AAC.defaultBitRate,
                ensureServer(),
                BuildConfig.DEBUG,
                null
            ) ?: return false
            shellService = connected
            attachScrcpyPipe(pipe, RecordingMode.VOICE_CALL)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku recording backend failed", e)
            runCatching { shellService?.stopRecording() }
            false
        }
    }

    private fun ensureServer(): String {
        val serverPath = ScrcpyConfig.getServerPath(appContext)
        check(ServerExtractor.ensureServerFile(appContext, serverPath)) {
            "scrcpy-server asset missing or SHA-256 verification failed"
        }
        return serverPath
    }

    private fun attachScrcpyPipe(pipe: ParcelFileDescriptor, mode: RecordingMode) {
        val safeLabel = currentCallerLabel.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "call" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // Keep the filename compatible with Ashu's existing recordings parser.
        val output = File(appContext.cacheDir, "recordings_tmp/${safeLabel}_$stamp.m4a").apply {
            parentFile?.mkdirs()
        }
        output.delete()

        outputStream = FileOutputStream(output)
        muxer = ScrcpyAudioMuxer(outputStream!!.fd, output.name).also {
            it.initialize(ScrcpyAudioCodec.AAC)
        }
        scrcpyReadPfd = pipe
        currentMode = mode
        recordingStartedAtMs = SystemClock.elapsedRealtime()
        lastPacketAtMs = 0L
        tempOutputFile = output

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        readScope = scope
        scrcpyClient = ScrcpyClient(
            inputPfd = pipe,
            expectedCodec = ScrcpyAudioCodec.AAC,
            listener = object : ScrcpyClient.AudioPacketListener {
                override fun onMetadataReceived(codec: ScrcpyAudioCodec) {
                    muxer?.initialize(codec)
                }

                override fun onAudioPacket(packet: ScrcpyClient.AudioPacket) {
                    if (!packet.isConfigPacket) lastPacketAtMs = SystemClock.elapsedRealtime()
                    muxer?.writePacket(packet, ScrcpyAudioCodec.AAC)
                }

                override fun onStreamEnd(error: String?) {
                    if (!error.isNullOrBlank()) Log.w(TAG, "scrcpy audio stream ended: $error")
                }
            }
        )
        readJob = scope.launch(Dispatchers.IO) {
            try {
                scrcpyClient?.start()
            } catch (e: Throwable) {
                Log.w(TAG, "scrcpy reader stopped", e)
            }
        }
    }

    private fun startMicrophoneFallback(): Boolean {
        val file = File(
            appContext.cacheDir,
            "recordings_tmp/${currentCallerLabel.filter { it.isLetterOrDigit() }.ifBlank { "call" }}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        )
        file.parentFile?.mkdirs()
        return try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            micRecorder = r
            tempOutputFile = file
            currentMode = RecordingMode.MICROPHONE
            recordingStartedAtMs = SystemClock.elapsedRealtime()
            lastPacketAtMs = recordingStartedAtMs
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Microphone fallback failed", e)
            runCatching { micRecorder?.release() }
            micRecorder = null
            file.delete()
            false
        }
    }

    fun stop(): RecordingResult? {
        if (!isRecording) return null
        return try {
            val wasMic = micRecorder != null
            if (wasMic) {
                runCatching { micRecorder?.stop() }
                runCatching { micRecorder?.release() }
                micRecorder = null
            } else {
                runCatching { shellService?.stopRecording() }
                if (rootSessionActive) runCatching { rootBackend.stop() }
                runCatching {
                    runBlocking { withTimeoutOrNull(2500L) { readJob?.join() } }
                }
                runCatching { scrcpyClient?.stop() }
                runCatching { scrcpyReadPfd?.close() }
                runCatching { readScope?.cancel() }
                runCatching { muxer?.close() }
                runCatching { outputStream?.close() }
            }

            val temp = tempOutputFile
            val resultFile = if (temp != null && temp.exists() && temp.length() > 0L) {
                moveToPublicStorage(temp) ?: persistToPrivateOwnedStorage(temp)
            } else null

            val result = resultFile?.let { RecordingResult(it, currentMode, currentCallerLabel) }
            resetSession()
            result
        } catch (e: Throwable) {
            Log.w(TAG, "Recording stop failed", e)
            resetSession()
            null
        }
    }

    private fun resetSession() {
        runCatching { scrcpyClient?.stop() }
        runCatching { scrcpyReadPfd?.close() }
        runCatching { readScope?.cancel() }
        runCatching { outputStream?.close() }
        scrcpyClient = null
        scrcpyReadPfd = null
        muxer = null
        outputStream = null
        readScope = null
        readJob = null
        tempOutputFile = null
        recordingStartedAtMs = 0L
        lastPacketAtMs = 0L
        currentMode = RecordingMode.FAILED
        currentCallerLabel = ""
        rootSessionActive = false
        shellService = null
        shizukuManager?.unbind()
        shizukuManager = null
    }

    private fun persistToPrivateOwnedStorage(temp: File): File {
        val dir = File(appContext.filesDir, "recordings").apply { mkdirs() }
        val destination = File(dir, temp.name)
        temp.copyTo(destination, overwrite = true)
        temp.delete()
        return destination
    }

    private fun moveToPublicStorage(tempFile: File): File? {
        return try {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, tempFile.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGS_FOLDER_NAME")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val uri = resolver.insert(collection, values) ?: return null
            val copied = resolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out) }
                true
            } ?: false
            if (!copied) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            tempFile.delete()
            // The rest of Ashu's recording UI intentionally identifies rows
            // by filename; keep returning the same logical public path so its
            // existing MediaStore resolver continues to work.
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$RECORDINGS_FOLDER_NAME/${tempFile.name}")
        } catch (e: Throwable) {
            Log.w(TAG, "Couldn't move recording to public MediaStore", e)
            null
        }
    }

    companion object {
        private const val TAG = "CallRecorder"
        private const val PRIVATE_SPACE_RECORDINGS_DIR = "private_space_recordings"
        private const val SILENT_SOURCES_PREFS = "call_recorder_silent_sources"
        private const val SILENT_SOURCES_KEY = "known_silent_audio_sources"

        /**
         * Per-device memory of which MediaRecorder.AudioSource ints have
         * been directly confirmed to produce zero signal for an entire
         * call on this hardware (see restartOnSustainedSilence/stop()).
         * Stored as a small SharedPreferences set of source-int strings -
         * deliberately separate from AppSettingsRepository's DataStore
         * since this is an internal recovery cache the person never sees
         * or edits, not a user-facing setting.
         *
         */
        internal fun loadKnownSilentSources(context: Context): Set<Int> {
            return try {
                context.getSharedPreferences(SILENT_SOURCES_PREFS, Context.MODE_PRIVATE)
                    .getStringSet(SILENT_SOURCES_KEY, emptySet())
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toSet()
                    ?: emptySet()
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to load known-silent sources", e)
                emptySet()
            }
        }

        private fun markSourceKnownSilent(context: Context, source: Int) {
            try {
                val prefs = context.getSharedPreferences(SILENT_SOURCES_PREFS, Context.MODE_PRIVATE)
                val current = prefs.getStringSet(SILENT_SOURCES_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
                if (current.add(source.toString())) {
                    prefs.edit().putStringSet(SILENT_SOURCES_KEY, current).apply()
                    Log.i("CallRecorder", "audio source $source confirmed silent on this device - will be skipped on future calls")
                }
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to persist known-silent source", e)
            }
        }

        /** Lets Settings offer a "forget learned silent sources" reset if the person's audio setup ever changes (new ROM, root state, etc). */
        fun clearKnownSilentSources(context: Context) {
            try {
                context.getSharedPreferences(SILENT_SOURCES_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                Log.w("CallRecorder", "Failed to clear known-silent sources", e)
            }
        }

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

        fun listRecordings(context: Context): List<File> {
            val fromMediaStore = queryMediaStoreRecordings(context)

            val legacyDir = File(context.filesDir, "recordings")
            val fromLegacyStorage = if (legacyDir.exists()) {
                legacyDir.listFiles()?.toList() ?: emptyList()
            } else emptyList()

            return (fromMediaStore + fromLegacyStorage)
                .distinctBy { it.absolutePath }
                .sortedByDescending { it.lastModified() }
        }

        private fun queryMediaStoreRecordings(context: Context): List<File> {
            val results = mutableListOf<File>()
            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA
                )
                val selection: String?
                val selectionArgs: Array<String>?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
                    selectionArgs = arrayOf("Music/$RECORDINGS_FOLDER_NAME/")
                } else {
                    selection = null
                    selectionArgs = null
                }

                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                        if (path != null) {
                            results.add(File(path))
                        } else {
                            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                            results.add(File(File(musicDir, RECORDINGS_FOLDER_NAME), cursor.getString(nameCol)))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CallRecorder", "MediaStore recordings query failed", e)
            }
            return results
        }

        /**
         * THE FIX for recordings that won't delete from inside the app: the
         * old version deleted via resolver.delete(collection, "DISPLAY_NAME
         * = ?", ...) - a bulk delete against the whole MediaStore audio
         * collection matched only by filename, with no RELATIVE_PATH/volume
         * constraint, and its result was never checked. If that delete
         * matched zero rows for any reason (a DISPLAY_NAME MediaStore
         * silently adjusted to avoid a collision, timing, etc.), the code
         * still fell through to a plain File.delete() - which fails
         * silently under scoped storage (Android 10+) for a file this app
         * doesn't have raw filesystem access to, since it was written via
         * MediaStore's resolver.insert()+openOutputStream() (see
         * moveToPublicStorage above), not direct file I/O. The net result:
         * deleteRecording() could return with nothing actually deleted, no
         * exception thrown, and the caller (MainActivity's deleteRecording)
         * never checked the return value either - so the file just
         * reappeared the next time the list refreshed, with no feedback
         * that anything had gone wrong.
         *
         * This version first queries for the SPECIFIC row this file
         * corresponds to (matched by DISPLAY_NAME AND RELATIVE_PATH
         * together, not filename alone) to get its real item URI, then
         * deletes that exact row - the precise, reliable form, rather than
         * a bulk delete hoping the WHERE clause matches the right thing.
         * It then verifies success by checking the file is actually gone
         * from disk afterward, rather than trusting either individual
         * call's return value - MediaStore's own delete() normally removes
         * the underlying physical file as part of removing the row, so
         * this is the one check that reflects what the person actually
         * cares about (is it gone).
         */
        fun deleteRecording(context: Context, file: File): Boolean {
            try {
                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val projection = arrayOf(MediaStore.Audio.Media._ID)
                val selection: String
                val selectionArgs: Array<String>
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
                    selectionArgs = arrayOf(file.name, "Music/$RECORDINGS_FOLDER_NAME/")
                } else {
                    selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
                    selectionArgs = arrayOf(file.name)
                }
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val itemUri = ContentUris.withAppendedId(collection, id)
                        try {
                            resolver.delete(itemUri, null, null)
                        } catch (e: Exception) {
                            Log.w("CallRecorder", "MediaStore delete failed for $itemUri", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CallRecorder", "MediaStore query-then-delete failed", e)
            }
            // Legacy/fallback path for files not tracked in MediaStore at
            // all (pre-Q devices, or the permanent app-storage fallback
            // path in stop() above when moveToPublicStorage failed) - a
            // plain file this app's own package directory or a legacy
            // public path it wrote directly still owns.
            if (file.exists()) {
                try { file.delete() } catch (_: Exception) {}
            }
            return !file.exists()
        }
    }
}
