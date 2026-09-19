package com.ashudialer.app.appcalls.recording

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioCodec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Names recordings and puts the finished file where the Recordings screen looks for it:
 *  - PUBLIC_MUSIC -> MediaStore, Music/Ashu Dialer (visible to file managers / share sheets)
 *  - PRIVATE      -> filesDir/recordings (only this app can see it)
 *
 * The capture pipeline always writes to a temp file in cacheDir first; [finalize] then moves the
 * completed file to its final home. That way a half-written file is never visible to the user and
 * a failed move never loses the recording (it falls back to private storage).
 */
object RecordingStorage {

    private const val TAG = "AppCalls:Storage"
    const val FOLDER_NAME = "Ashu Dialer"
    private const val TEMP_DIR = "recordings_tmp"

    /** Result of [finalize]: the file (for the Recordings list) and a content URI if it went to MediaStore. */
    data class SavedRecording(val file: File, val uri: Uri?)

    /** A fresh temp file for the pipeline to write into. */
    fun newTempFile(context: Context, session: RecordingSession, codec: ScrcpyAudioCodec, prefs: RecordingPrefs): File {
        val dir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        return File(dir, formatFileName(session, codec, prefs.fileNameTemplate))
    }

    /** Recording file name from the user's template. Supports {date} {direction} {phone_number} {contact_name} {cross_country} {app_source}. */
    fun formatFileName(
        session: RecordingSession,
        codec: ScrcpyAudioCodec,
        template: String,
        startTimeMillis: Long = System.currentTimeMillis()
    ): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startTimeMillis))
        val direction = if (session.direction == CallDirection.INCOMING) "in" else "out"
        val phone = session.phoneNumber.orEmpty()
        val contact = session.contactName.orEmpty()
        val app = session.sourceApp.orEmpty()

        var name = template
            .replace("{date}", date)
            .replace("{direction}", direction)
            .replace("{phone_number}", sanitize(phone))
            .replace("{contact_name}", sanitize(contact))
            .replace("{cross_country}", session.isCrossCountry.toString())
            .replace("{app_source}", sanitize(app))

        // App calls always carry their app name so WhatsApp/Telegram recordings are easy to spot.
        if (session.isAppCall && !template.contains("{app_source}")) {
            name = "${sanitize(app)}_$name"
        }
        // Keep the number in the name even if the template omitted it and there is no contact name.
        if (phone.isNotEmpty() && !template.contains("{phone_number}") && contact.isEmpty()) {
            name = "${name}_${sanitize(phone)}"
        }
        // A template made only of empty placeholders would leave "__" or nothing.
        name = name.trim('_', ' ', '-').replace(Regex("_{2,}"), "_").ifBlank { "call_$date" }
        return name + codec.containerExtension
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("""[/\\:*?"<>|\x00-\x1F]"""), "_").trim()

    /**
     * Moves the finished temp file to its final location. Never returns null: if the preferred
     * location fails, the recording is kept in private storage rather than lost.
     */
    fun finalize(context: Context, temp: File, codec: ScrcpyAudioCodec, prefs: RecordingPrefs): SavedRecording? {
        if (!temp.exists() || temp.length() <= 0L) {
            AppCallsLogger.w(TAG, "Nothing captured (missing or empty file) - discarding ${temp.name}")
            runCatching { temp.delete() }
            return null
        }

        if (prefs.storageMode == RecordingPrefs.StorageMode.PUBLIC_MUSIC) {
            saveToMediaStore(context, temp, codec)?.let { return it }
            AppCallsLogger.w(TAG, "MediaStore save failed - falling back to private storage")
        }
        return saveToPrivate(context, temp)
    }

    private fun saveToMediaStore(context: Context, temp: File, codec: ScrcpyAudioCodec): SavedRecording? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, temp.name)
                put(MediaStore.Audio.Media.MIME_TYPE, codec.fileMimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$FOLDER_NAME")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(itemUri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }

            // On Android 9 and below the file also has to physically exist in the Music folder.
            val finalFile = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER_NAME), temp.name)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                finalFile.parentFile?.mkdirs()
                resolver.openInputStream(itemUri)?.use { input -> finalFile.outputStream().use { input.copyTo(it) } }
            }
            temp.delete()
            AppCallsLogger.i(TAG, "Saved recording to Music/$FOLDER_NAME/${temp.name}")
            SavedRecording(finalFile, itemUri)
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "MediaStore save threw: ${e.message}", e)
            null
        }
    }

    private fun saveToPrivate(context: Context, temp: File): SavedRecording? {
        return try {
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val dest = File(dir, temp.name)
            temp.copyTo(dest, overwrite = true)
            temp.delete()
            AppCallsLogger.i(TAG, "Saved recording to private storage: ${dest.absolutePath}")
            SavedRecording(dest, null)
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Couldn't save recording anywhere: ${e.message}", e)
            null
        }
    }

    /** Deletes recordings older than / beyond the limits set in [RecordingPrefs]. Safe to call any time. */
    fun runAutoDelete(context: Context, prefs: RecordingPrefs, listRecordings: () -> List<File>, delete: (File) -> Unit) {
        try {
            var files = listRecordings().sortedBy { it.lastModified() }
            if (prefs.autoDeleteByTimeEnabled) {
                val unitMs = if (prefs.autoDeleteByTimeUnit == "hours") 3_600_000L else 86_400_000L
                val cutoff = System.currentTimeMillis() - prefs.autoDeleteByTimeValue * unitMs
                files.filter { it.lastModified() < cutoff }.forEach {
                    AppCallsLogger.i(TAG, "Auto-delete (age): ${it.name}")
                    delete(it)
                }
                files = files.filter { it.lastModified() >= cutoff }
            }
            if (prefs.autoDeleteBySpaceEnabled) {
                val unitBytes = if (prefs.autoDeleteBySpaceUnit == "gb") 1024L * 1024 * 1024 else 1024L * 1024
                val limit = prefs.autoDeleteBySpaceValue * unitBytes
                var total = files.sumOf { it.length() }
                for (f in files) { // oldest first
                    if (total <= limit) break
                    total -= f.length()
                    AppCallsLogger.i(TAG, "Auto-delete (space): ${f.name}")
                    delete(f)
                }
            }
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Auto-delete pass failed: ${e.message}")
        }
    }
}
