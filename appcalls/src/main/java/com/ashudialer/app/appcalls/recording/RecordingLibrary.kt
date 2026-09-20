package com.ashudialer.app.appcalls.recording

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ashudialer.app.appcalls.AppCallsLogger
import java.io.File

/**
 * Lists and deletes finished recordings. Looks in the same two places [RecordingStorage] writes
 * to: MediaStore `Music/Ashu Dialer` and the app-private `filesDir/recordings` folder.
 *
 * Kept in this module (rather than the app's CallRecorder) so the foreground service can run its
 * auto-delete pass without depending on the :app module.
 */
object RecordingLibrary {

    private const val TAG = "AppCalls:Library"
    private val AUDIO_EXTENSIONS = setOf("m4a", "ogg", "mp3", "wav", "aac", "opus", "3gp")

    fun listAll(context: Context): List<File> {
        val fromMediaStore = queryMediaStore(context)
        val privateDir = File(context.filesDir, "recordings")
        // walkTopDown: recordings now live in per-source sub-folders (Phone/, WhatsApp/ ...), while
        // ones saved by older versions sit directly in this folder. Both are picked up.
        val fromPrivate = privateDir.walkTopDown().filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }.toList()
        return (fromMediaStore + fromPrivate)
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
    }

    private fun collectionUri() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    private fun queryMediaStore(context: Context): List<File> {
        val results = mutableListOf<File>()
        try {
            // minSdk is 29 (Android 10), which is where RELATIVE_PATH was introduced, so the column
            // is always available on every device this app installs on.
            val projection = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.RELATIVE_PATH
            )
            val relative = "Music/${RecordingStorage.FOLDER_NAME}/"
            // Prefix match (LIKE 'Music/Ashu Dialer/%'): matches the old flat folder AND every
            // sub-folder. "_" and "%" are LIKE wildcards, but the folder name contains neither.
            val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("$relative%")
            } else null to null

            context.contentResolver.query(collectionUri(), projection, selection, args, null)?.use { c ->
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relCol = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                while (c.moveToNext()) {
                    val path = if (dataCol >= 0) c.getString(dataCol) else null
                    // "Music/Ashu Dialer/WhatsApp/" -> "Ashu Dialer/WhatsApp"
                    val rel = if (relCol >= 0) c.getString(relCol)?.removePrefix("Music/")?.trim('/') else null
                    val dir = if (!rel.isNullOrBlank()) File(musicDir, rel) else File(musicDir, RecordingStorage.FOLDER_NAME)
                    results.add(path?.let(::File) ?: File(dir, c.getString(nameCol)))
                }
            }
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "MediaStore query failed: ${e.message}")
        }
        return results
    }

    /** Deletes the exact MediaStore row (matched by name + folder), then the file itself. Returns true once it is gone. */
    fun delete(context: Context, file: File): Boolean {
        try {
            val resolver = context.contentResolver
            val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Name + folder PREFIX: the file may be in "Ashu Dialer/" (old layout) or in a
                // sub-folder such as "Ashu Dialer/WhatsApp/" (new layout).
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to
                    arrayOf(file.name, "Music/${RecordingStorage.FOLDER_NAME}/%")
            } else {
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?" to arrayOf(file.name)
            }
            resolver.query(collectionUri(), arrayOf(MediaStore.Audio.Media._ID), selection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (c.moveToNext()) {
                    runCatching { resolver.delete(ContentUris.withAppendedId(collectionUri(), c.getLong(idCol)), null, null) }
                }
            }
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "MediaStore delete failed: ${e.message}")
        }
        if (file.exists()) runCatching { file.delete() }
        return !file.exists()
    }
}
