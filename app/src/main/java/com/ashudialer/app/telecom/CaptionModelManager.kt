package com.ashudialer.app.telecom

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.android.StorageService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * One caption language the person can download. Deliberately a short,
 * fixed list rather than every language Vosk publishes - each entry is a
 * real, separately-downloaded ~50MB file, and offering dozens of options
 * that mostly won't be chosen just adds scroll-length and decision cost to
 * the settings page for no benefit. Hinglish conversation in a call is
 * usually mostly one of these three; if this list turns out to be missing
 * something people actually ask for, it is a two-line addition (source URL
 * + folder name), not a redesign.
 */
enum class CaptionLanguage(val displayName: String, val assetFolderName: String, val downloadUrl: String) {
    ENGLISH_INDIA(
        "English (India)",
        "vosk-model-small-en-in-0.4",
        "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
    ),
    HINDI(
        "Hindi",
        "vosk-model-small-hi-0.22",
        "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
    ),
    ENGLISH_US(
        "English (US/UK)",
        "vosk-model-small-en-us-0.15",
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    );
}

sealed class CaptionModelState {
    data object NotDownloaded : CaptionModelState()
    data class Downloading(val progressPercent: Int) : CaptionModelState()
    data object Unpacking : CaptionModelState()
    data class Ready(val model: Model) : CaptionModelState()
    data class Failed(val message: String) : CaptionModelState()
}

/**
 * Downloads, unpacks, and loads the Vosk model for a chosen CaptionLanguage.
 * Nothing here runs unless the person explicitly turns Live Captions on and
 * picks a language in Settings - the model is never bundled in the APK (it
 * would add ~50MB per language to every install, most of which would go
 * unused by people who never turn this on) and never downloaded silently in
 * the background.
 *
 * Models are cached in app-private storage (filesDir), one at a time - a
 * newly chosen language's download replaces the previous one rather than
 * accumulating several 50MB models the person only ever uses one of. Once
 * unpacked, the same Model is reused for every call until the app process
 * dies, since Model construction itself (loading the acoustic/language
 * model files from disk) is the expensive part, not per-call recognizer
 * creation.
 */
class CaptionModelManager(private val context: Context) {

    private var cachedModel: Model? = null
    private var cachedLanguage: CaptionLanguage? = null

    fun modelsDir(): File = File(context.filesDir, "caption_models").apply { mkdirs() }

    fun isDownloaded(language: CaptionLanguage): Boolean =
        File(modelsDir(), language.assetFolderName).let { it.isDirectory && it.list()?.isNotEmpty() == true }

    /** Returns the already-loaded Model without touching disk, if this exact language is already cached in memory. */
    fun cachedModelFor(language: CaptionLanguage): Model? =
        cachedModel.takeIf { cachedLanguage == language }

    fun sizeOnDiskMb(language: CaptionLanguage): Long {
        val dir = File(modelsDir(), language.assetFolderName)
        if (!dir.isDirectory) return 0L
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total / (1024 * 1024)
    }

    fun deleteDownloaded(language: CaptionLanguage) {
        if (cachedLanguage == language) {
            cachedModel?.close()
            cachedModel = null
            cachedLanguage = null
        }
        File(modelsDir(), language.assetFolderName).deleteRecursively()
    }

    /**
     * Downloads the model zip (if not already on disk), unpacks it, and
     * loads it into a Vosk Model object. Emits progress via onState so the
     * settings screen can show a real percentage rather than an
     * indeterminate spinner for what can be a 30-60 second download on a
     * slower connection - the whole point of scoping this to Settings
     * rather than starting it mid-call is that the person should never be
     * waiting on a download during an actual incoming call.
     */
    suspend fun ensureReady(
        language: CaptionLanguage,
        onState: (CaptionModelState) -> Unit
    ): Model? = withContext(Dispatchers.IO) {
        cachedModelFor(language)?.let {
            onState(CaptionModelState.Ready(it))
            return@withContext it
        }

        try {
            if (!isDownloaded(language)) {
                onState(CaptionModelState.Downloading(0))
                val zipFile = File(context.cacheDir, "${language.assetFolderName}.zip")
                if (!downloadZip(language.downloadUrl, zipFile) { percent ->
                        onState(CaptionModelState.Downloading(percent))
                    }
                ) {
                    onState(CaptionModelState.Failed("Download failed. Check your internet connection and try again."))
                    return@withContext null
                }
                onState(CaptionModelState.Unpacking)
                val extractedTo = File(modelsDir(), language.assetFolderName)
                extractedTo.deleteRecursively()
                unzip(zipFile, modelsDir())
                zipFile.delete()
                if (!extractedTo.isDirectory) {
                    onState(CaptionModelState.Failed("The downloaded model looked incomplete. Please try again."))
                    return@withContext null
                }
            } else {
                onState(CaptionModelState.Unpacking)
            }

            val model = Model(File(modelsDir(), language.assetFolderName).absolutePath)
            cachedModel = model
            cachedLanguage = language
            onState(CaptionModelState.Ready(model))
            model
        } catch (e: Exception) {
            onState(CaptionModelState.Failed(e.message ?: "Couldn't set up captions for this language."))
            null
        }
    }

    private fun downloadZip(url: String, destination: File, onProgress: (Int) -> Unit): Boolean {
        if (!isAllowedHost(url)) return false
        var conn: HttpURLConnection? = null
        val temp = File(destination.parentFile, destination.name + ".part")
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 60_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                useCaches = false
            }
            if (conn.responseCode !in 200..299) return false
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
            destination.parentFile?.mkdirs()
            temp.delete()
            var written = 0L
            var lastReportedPercent = -1
            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (totalBytes != null) {
                            val percent = ((written * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (!temp.isFile || temp.length() < 1024L) return false
            if (destination.exists()) destination.delete()
            temp.renameTo(destination)
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
            conn?.disconnect()
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                // Zip-slip guard: refuse to write outside targetDir even if a
                // malicious/corrupt zip entry name tried to path-traverse out
                // (e.g. "../../somewhere"). Model zips are fetched only from
                // the fixed alphacephei.com URLs above, but this costs
                // nothing and removes the need to fully trust that host
                // forever.
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun isAllowedHost(value: String): Boolean = try {
        val uri = android.net.Uri.parse(value)
        uri.scheme.equals("https", ignoreCase = true) && uri.host.equals("alphacephei.com", ignoreCase = true)
    } catch (_: Exception) {
        false
    }
}
