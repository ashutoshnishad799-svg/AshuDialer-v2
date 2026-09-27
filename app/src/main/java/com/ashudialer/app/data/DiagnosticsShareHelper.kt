package com.ashudialer.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ashudialer.app.BuildConfig
import com.ashudialer.app.util.CrashLogCollector
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Builds the "Send feedback" report for Help & Feedback: app version, device model and Android
 * version (so the developer doesn't have to ask for these, see HelpFeedbackScreen's "How do I
 * report a problem?" FAQ), plus the most recent local crash report if the app has crashed (see
 * CrashLogCollector - nothing is collected or sent unless the person taps this themselves).
 *
 * Deliberately never contains anything from a call: no phone numbers, contact names or
 * recordings, matching the "never sent" list this app already commits to everywhere else -
 * this is a device/app diagnostics report, not a call export.
 *
 * Two ways to send it:
 *  - [sendToFirestore]: the default one-tap path from the Send Feedback dialog. Writes straight
 *    to the "feedback" collection in this app's existing Firebase project (ashu-phone-07x, the
 *    same one already used for auth/cloud-backup) - no share sheet, no app to pick, arrives
 *    directly.
 *  - [share]: the older share-sheet fallback, kept for anyone who taps "share as a file" instead
 *    (e.g. to send it over their own email or chat app rather than straight to the developer).
 */
object DiagnosticsShareHelper {

    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * Writes [userMessage] plus the device/app diagnostics straight to Firestore. [onResult] is
     * called on the main thread with true on success, false if it couldn't be sent (e.g. no
     * network) so the caller can show a Toast and let the person retry - it is NOT silently
     * dropped on failure.
     */
    // Neither FirebaseAuth.signInAnonymously() nor a Firestore write carries any
    // deadline of its own - left unbounded, a write whose *local* cache commit
    // succeeds instantly but whose *server* round-trip stalls (flaky connectivity,
    // a rules rejection that doesn't propagate back through this particular
    // Task the way a normal error does) leaves the calling await() sitting
    // forever with no exception and no result, which is what a stuck "Send"
    // spinner in SendFeedbackDialog actually was: nothing was wrong with the
    // dialog's own state machine, the suspend call it was waiting on simply
    // never returned. Two separate withTimeout blocks (rather than one wrapping
    // both calls) so a timeout here is attributable to a specific stage -
    // "auth" vs "send" - which is exactly the distinction that made this bug
    // take real investigation to root-cause the first time; the person next
    // time should see which stage failed directly in errorText instead of a
    // generic failure.
    private const val AUTH_TIMEOUT_MS = 15_000L
    private const val SEND_TIMEOUT_MS = 20_000L

    /**
     * Sends the feedback report directly to Firestore. The app first makes sure
     * this install has a Firebase identity; Firestore rules require an
     * authenticated user for feedback writes. This is cancellable while the
     * network request is in flight, and each stage is time-bounded (see
     * AUTH_TIMEOUT_MS/SEND_TIMEOUT_MS above) so a stalled network or a Firestore
     * Task that never resolves can no longer hang the Send button indefinitely -
     * it surfaces a specific, stage-attributed error instead.
     */
    suspend fun sendToFirestore(context: Context, userMessage: String): Result<Unit> {
        return try {
            val signedIn = try {
                withTimeout(AUTH_TIMEOUT_MS) { AuthRepository(context).ensureSignedIn() }
            } catch (e: TimeoutCancellationException) {
                return Result.failure(IllegalStateException("Sign-in timed out - check your connection and try again."))
            } ?: return Result.failure(IllegalStateException("Firebase authentication is unavailable."))

            val crashFile = CrashLogCollector.latestCrashReport(context)
            val crashText = crashFile?.let { runCatching { it.readText() }.getOrNull() }

            val doc = hashMapOf(
                "message" to userMessage,
                "appVersion" to BuildConfig.VERSION_NAME,
                "versionCode" to BuildConfig.VERSION_CODE,
                "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                "androidRelease" to android.os.Build.VERSION.RELEASE,
                "androidSdk" to android.os.Build.VERSION.SDK_INT,
                "crashReport" to crashText,
                "uid" to signedIn.uid,
                "sentAt" to com.google.firebase.Timestamp.now()
            )

            try {
                withTimeout(SEND_TIMEOUT_MS) {
                    Firebase.firestore.collection("feedback").add(doc).await()
                }
            } catch (e: TimeoutCancellationException) {
                return Result.failure(IllegalStateException("Sending timed out - it may still arrive once you're back online, but you can try again now."))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun share(context: Context) {
        val reportText = buildReport(context)

        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(dir, "ashu_dialer_feedback.txt")
        file.writeText(reportText, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ashu Dialer feedback - v${BuildConfig.VERSION_NAME}")
            // A short prompt as the text body itself, for apps that only read EXTRA_TEXT and
            // ignore the attachment - the full report (below) is still attached either way.
            putExtra(Intent.EXTRA_TEXT, "What happened? (the attached file already has your app version and device info)\n\n")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("Feedback", uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Send feedback"))
    }

    private fun buildReport(context: Context): String = buildString {
        appendLine("Ashu Dialer feedback")
        appendLine("Sent: ${TIMESTAMP_FORMAT.format(Date())}")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("What happened?")
        appendLine("(describe it here before sending)")

        val crashFile = CrashLogCollector.latestCrashReport(context)
        if (crashFile != null) {
            appendLine()
            appendLine("---")
            appendLine("Most recent crash report on this device:")
            appendLine()
            append(runCatching { crashFile.readText() }.getOrDefault("(could not read the crash file)"))
        }
    }
}
