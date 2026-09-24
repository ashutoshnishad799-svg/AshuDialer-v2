package com.ashudialer.app.data

import android.content.Context
import com.ashudialer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Public updater: Firebase first, public GitHub Releases fallback. No token is required. */
data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String?,
    val releaseName: String?,
    val releaseUrl: String?,
    val updateAvailable: Boolean,
    val error: String? = null,
    val apkDownloadUrl: String? = null,
    val apkAssetName: String? = null,
    val releaseNotes: String? = null
)

class UpdateChecker(private val context: Context) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME

        // Primary source: Firebase Hosting latest.json.
        fetchFirebaseManifest(current)?.let { return@withContext it }

        // Fallback: the public GitHub release created by CI. This prevents the
        // update screen from showing a scary HTTP 404 when Hosting has not yet
        // been deployed, while still finding a real newer APK when CI released it.
        fetchLatestGitHubRelease(current)?.let { return@withContext it }

        // Never turn a temporary update-server outage into a red error screen.
        // The installed build is still usable, so present a clean current-state.
        UpdateCheckResult(
            currentVersion = current,
            latestVersion = current,
            releaseName = "Ashu Dialer $current",
            releaseUrl = null,
            updateAvailable = false
        )
    }

    private fun fetchFirebaseManifest(current: String): UpdateCheckResult? {
        var conn: HttpURLConnection? = null
        return try {
            val endpoint = "$UPDATE_MANIFEST_URL?t=${System.currentTimeMillis()}"
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AshuDialer/$current")
            }
            if (conn.responseCode !in 200..299) return null

            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val latestCode = json.optInt("versionCode", -1)
            val latest = json.optString("versionName").trim().ifBlank { null }
            val apkUrl = json.optString("downloadUrl").trim().takeIf { it.isNotBlank() }
            val releaseNotes = json.optString("releaseNotes").trim().takeIf { it.isNotBlank() }
            if (latestCode < 0 || latest == null || apkUrl == null || !isAllowedDownloadUrl(apkUrl)) return null

            UpdateCheckResult(
                currentVersion = current,
                latestVersion = latest,
                releaseName = "Ashu Dialer $latest",
                releaseUrl = apkUrl,
                updateAvailable = latestCode > BuildConfig.VERSION_CODE,
                apkDownloadUrl = apkUrl,
                apkAssetName = apkUrl.substringAfterLast('/').substringBefore('?').ifBlank { "AshuDialer-$latest.apk" },
                releaseNotes = releaseNotes
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchLatestGitHubRelease(current: String): UpdateCheckResult? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(GITHUB_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "AshuDialer/$current")
            }
            if (conn.responseCode !in 200..299) return null

            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val latest = json.optString("tag_name").removePrefix("v").trim().ifBlank { null } ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var apkName: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name").trim()
                val url = asset.optString("browser_download_url").trim()
                // The release contains a single signed APK.
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    apkName = name
                    apkUrl = url
                    break
                }
            }

            val notes = json.optString("body").trim().ifBlank { null }
            val newer = compareVersions(latest, current) > 0
            UpdateCheckResult(
                currentVersion = current,
                latestVersion = latest,
                releaseName = json.optString("name").trim().ifBlank { "Ashu Dialer $latest" },
                releaseUrl = apkUrl,
                updateAvailable = newer && !apkUrl.isNullOrBlank(),
                apkDownloadUrl = apkUrl,
                apkAssetName = apkName,
                releaseNotes = notes
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Downloads the APK from the manifest URL first. If that URL is unavailable
     * (for example Firebase Spark rejects .apk hosting), transparently falls
     * back to the latest public GitHub Release asset. The update screen can
     * therefore keep Firebase as the public manifest without requiring APKs
     * to be hosted by Firebase itself.
     */
    suspend fun downloadApk(
        url: String,
        destination: java.io.File,
        onProgress: (percent: Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isAllowedDownloadUrl(url)) return@withContext false

        // Up to 3 attempts on the primary URL: a flaky mobile connection that drops half way
        // used to fail the whole update with one toast. Each attempt starts a fresh .part file.
        repeat(3) { attempt ->
            if (downloadFromUrl(url, destination, onProgress)) return@withContext true
            if (attempt < 2) kotlinx.coroutines.delay(1200L * (attempt + 1))
        }

        // Firebase Hosting on the free Spark plan cannot serve APK executables.
        // Try the public CI release before reporting a failure to the user.
        val github = fetchLatestGitHubRelease(BuildConfig.VERSION_NAME)
        val fallbackUrl = github?.apkDownloadUrl
        if (fallbackUrl != null && isAllowedDownloadUrl(fallbackUrl) &&
            compareVersions(github.latestVersion ?: "", BuildConfig.VERSION_NAME) >= 0) {
            repeat(2) { attempt ->
                if (downloadFromUrl(fallbackUrl, destination, onProgress)) return@withContext true
                if (attempt < 1) kotlinx.coroutines.delay(1500L)
            }
        }
        false
    }

    private fun downloadFromUrl(url: String, destination: java.io.File, onProgress: (Int) -> Unit): Boolean {
        if (!isAllowedDownloadUrl(url)) return false
        var conn: HttpURLConnection? = null
        val temp = java.io.File(destination.parentFile, destination.name + ".part")
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
                // GitHub answers with a 302 to a signed objects.githubusercontent.com URL; the
                // built-in redirect handling refuses to follow an http<->https change but is fine
                // for https -> https, which is what GitHub uses.
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "AshuDialer/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode !in 200..299) return false
            // The final URL after redirects must still be one of the allowed hosts, so a redirect
            // cannot quietly hand the installer a file from somewhere else.
            if (!isAllowedDownloadUrl(conn.url.toString())) return false

            val total = conn.contentLengthLong
            destination.parentFile?.mkdirs()
            temp.delete()
            var done = 0L
            var lastPercent = -2
            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
                        if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                    }
                }
            }
            // A truncated download (server closed early) must never reach the installer: it would
            // fail with the unhelpful "There was a problem parsing the package".
            if (total > 0 && temp.length() != total) return false
            if (!temp.isFile || temp.length() < 1024L * 100) return false
            if (destination.exists()) destination.delete()
            temp.renameTo(destination) && destination.isFile && destination.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
            conn?.disconnect()
        }
    }

    private fun isAllowedDownloadUrl(value: String): Boolean = try {
        val uri = android.net.Uri.parse(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            ALLOWED_DOWNLOAD_HOSTS.any { uri.host.equals(it, ignoreCase = true) }
    } catch (_: Exception) { false }

    /**
     * Compares dotted versions. Numeric parts are compared numerically ("1.10" > "1.9"). A
     * pre-release suffix ("1.6.0-beta") sorts BEFORE the same plain version ("1.6.0"), which the
     * old implementation got wrong by throwing the suffix away and calling them equal.
     */
    private fun compareVersions(a: String, b: String): Int {
        fun split(v: String): Pair<List<Int>, String> {
            val clean = v.trim().removePrefix("v").removePrefix("V")
            val dash = clean.indexOf('-')
            val core = if (dash >= 0) clean.substring(0, dash) else clean
            val pre = if (dash >= 0) clean.substring(dash + 1) else ""
            return core.split('.').mapNotNull { it.toIntOrNull() } to pre
        }
        val (pa, preA) = split(a)
        val (pb, preB) = split(b)
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return when {
            preA.isEmpty() && preB.isEmpty() -> 0
            preA.isEmpty() -> 1     // release beats its own pre-release
            preB.isEmpty() -> -1
            else -> preA.compareTo(preB)
        }
    }

    companion object {
        private const val DEFAULT_BUFFER = 32 * 1024
        private const val UPDATE_MANIFEST_URL = "https://ashu-phone-07x.web.app/latest.json"
        // The repository the APKs are actually published to (see .github/workflows/build.yml).
        // It used to point at ".../Ashudialer" (no "-v2"), a repo that has no releases, so the
        // GitHub fallback always returned 404 and "no update" was reported forever.
        private const val GITHUB_REPO = "ashutoshnishad799-svg/AshuDialer-v2"
        private const val GITHUB_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "ashu-phone-07x.web.app",
            "github.com",
            "api.github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )
    }
}
