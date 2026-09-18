package com.ashudialer.app.telecom

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.ashudialer.app.appcalls.ShizukuRecordingSession
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecordingMode { VOICE_CALL, VOICE_UPLINK, VOICE_DOWNLINK, VOICE_COMMUNICATION, OUTPUT, APP_CALL, FAILED }

data class RecordingResult(
    val file: File,
    val mode: RecordingMode,
    val callerLabel: String
)

private const val RECORDINGS_FOLDER_NAME = "Ashu Dialer"

/**
 * Native phone-call recorder. It deliberately has no MediaRecorder/root/Magisk path.
 * Capture is performed through Shizuku's privileged user service using scrcpy-server.
 */
class CallRecorder(private val context: Context) {
    private var session: ShizukuRecordingSession? = null
    private var outputFile: File? = null
    private var currentMode: RecordingMode = RecordingMode.FAILED
    private var currentCallerLabel: String = ""
    private var recordingStartedAtMs: Long = 0L

    val isRecording: Boolean
        get() = session?.isActive == true

    fun currentMode(): RecordingMode? = currentMode.takeIf { isRecording && it != RecordingMode.FAILED }

    fun currentCallerLabel(): String? = currentCallerLabel.takeIf { isRecording && it.isNotBlank() }

    fun elapsedSeconds(): Int {
        if (recordingStartedAtMs <= 0L) return 0
        return ((SystemClock.elapsedRealtime() - recordingStartedAtMs) / 1000L).toInt().coerceAtLeast(0)
    }

    /** The Shizuku/scrcpy pipeline does not expose an amplitude meter to the UI. */
    fun currentAmplitude(): Int = 0

    fun start(callerLabel: String, audioSourceKey: String = ScrcpyAudioSource.VOICE_CALL.cliKey): RecordingMode {
        if (isRecording) return currentMode
        if (!ShizukuRecordingSession.isReady(context)) {
            Log.w("CallRecorder", "Shizuku is not running or Ashu Dialer is not authorised")
            currentMode = RecordingMode.FAILED
            return currentMode
        }

        currentCallerLabel = callerLabel
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeLabel = callerLabel.filter { it.isLetterOrDigit() }.ifBlank { "call" }
        val dir = File(context.cacheDir, "recordings_tmp").apply { mkdirs() }
        val file = File(dir, "${safeLabel}_${timestamp}_voice_call.m4a")

        return try {
            val source = runCatching { ScrcpyAudioSource.fromKey(audioSourceKey) }.getOrDefault(ScrcpyAudioSource.VOICE_CALL)
            val newSession = ShizukuRecordingSession(context)
            newSession.start(file, source, 64000)
            session = newSession
            outputFile = file
            currentMode = when (source) {
                ScrcpyAudioSource.VOICE_CALL -> RecordingMode.VOICE_CALL
                ScrcpyAudioSource.VOICE_CALL_UPLINK -> RecordingMode.VOICE_UPLINK
                ScrcpyAudioSource.VOICE_CALL_DOWNLINK -> RecordingMode.VOICE_DOWNLINK
                ScrcpyAudioSource.VOICE_COMMUNICATION -> RecordingMode.VOICE_COMMUNICATION
                ScrcpyAudioSource.OUTPUT -> RecordingMode.OUTPUT
                else -> RecordingMode.VOICE_CALL
            }
            recordingStartedAtMs = SystemClock.elapsedRealtime()
            Log.i("CallRecorder", "Shizuku recording started using ${source.cliKey}")
            currentMode
        } catch (t: Throwable) {
            Log.e("CallRecorder", "Shizuku recording start failed", t)
            runCatching { file.delete() }
            session = null
            outputFile = null
            currentMode = RecordingMode.FAILED
            recordingStartedAtMs = 0L
            currentCallerLabel = ""
            currentMode
        }
    }

    fun stop(): RecordingResult? {
        val activeSession = session ?: return null
        val tempFile = outputFile
        return try {
            activeSession.stop()
            val finalFile = tempFile?.let { moveToPublicStorage(it) } ?: null ?: tempFile?.let {
                runCatching {
                    val permanentDir = File(context.filesDir, "recordings").apply { mkdirs() }
                    val destination = File(permanentDir, it.name)
                    it.copyTo(destination, overwrite = true)
                    it.delete()
                    destination
                }.getOrNull()
            } ?: return null

            val result = RecordingResult(finalFile, currentMode, currentCallerLabel)
            resetState()
            result
        } catch (t: Throwable) {
            Log.e("CallRecorder", "Shizuku recording stop failed", t)
            runCatching { activeSession.cancel() }
            resetState()
            null
        }
    }

    private fun resetState() {
        session = null
        outputFile = null
        currentMode = RecordingMode.FAILED
        currentCallerLabel = ""
        recordingStartedAtMs = 0L
    }

    private fun moveToPublicStorage(tempFile: File): File? {
        if (!tempFile.exists() || tempFile.length() <= 0L) return null
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, tempFile.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGS_FOLDER_NAME/")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> tempFile.inputStream().use { input -> input.copyTo(out) } } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            tempFile.delete()
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), RECORDINGS_FOLDER_NAME).resolve(tempFile.name)
        } catch (t: Throwable) {
            Log.w("CallRecorder", "Couldn't publish recording to MediaStore", t)
            null
        }
    }

    companion object {
        private const val PRIVATE_SPACE_RECORDINGS_DIR = "private_space_recordings"
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
