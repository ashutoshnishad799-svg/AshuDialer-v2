// Detection approach adapted from ShizuCallRecorder's AppCallNotificationListenerService (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ashudialer.app.appcalls.recording.CallDirection
import com.ashudialer.app.appcalls.recording.RecordingForegroundService
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.appcalls.recording.RecordingSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects ongoing WhatsApp / Telegram VoIP calls from their persistent call notification and asks
 * [RecordingForegroundService] to record them.
 *
 * Why a notification listener: a VoIP call inside a third-party app never fires the system
 * PHONE_STATE broadcast, so there is no public "a call started inside app X" signal. The one
 * signal every call app exposes is its *ongoing call* notification (CATEGORY_CALL + ONGOING).
 *
 * Requires Notification Access. This class only DETECTS; the recording itself runs in the
 * foreground service so Android will not kill it while the app is in the background - the old
 * version ran the pipeline right inside this listener with no foreground protection.
 */
class AppCallNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "AppCalls:NotifListener"

        /** Lower-case fragments that appear in a call notification's text in the languages the apps ship in most. */
        private val CALL_WORDS = listOf(
            "call", "calling", "ringing", "in call", "video chat", "voice chat", "on a call",
            "कॉल", "बुला"   // Hindi: "call" / "calling"
        )
    }

    /** Notification key -> target for every call currently considered active (WhatsApp re-posts its notification every second). */
    private val activeCalls = ConcurrentHashMap<String, AppCallTarget>()

    private val prefs by lazy { RecordingPrefs(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppCallsLogger.d(TAG, "Listener connected - scanning for calls already in progress.")
        runCatching { activeNotifications?.forEach(::handlePosted) }
            .onFailure { AppCallsLogger.w(TAG, "Initial scan failed: ${it.message}") }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppCallsLogger.w(TAG, "Listener disconnected.")
        if (activeCalls.isNotEmpty()) {
            activeCalls.clear()
            sendStop()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handlePosted(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val target = activeCalls.remove(sbn.key) ?: return
        AppCallsLogger.i(TAG, "${target.displayName} call ended (key=${sbn.key}).")
        // Only stop when no other tracked call remains.
        if (activeCalls.isEmpty()) sendStop()
    }

    private fun handlePosted(sbn: StatusBarNotification) {
        val target = AppCallTarget.fromPackageName(sbn.packageName) ?: return
        if (!prefs.callRecordingEnabled || !isEnabled(target)) return
        if (!looksLikeOngoingCall(sbn.notification, sbn.packageName, target.looseDetection)) return
        // Already tracking this key -> it's just the per-second duration tick, not a new call.
        if (activeCalls.putIfAbsent(sbn.key, target) != null) return

        val label = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
        AppCallsLogger.i(TAG, "Detected ongoing ${target.displayName} call from '${label ?: "?"}' - starting recorder.")

        val session = RecordingSession(
            phoneNumber = null,
            direction = CallDirection.OUTGOING, // VoIP notifications don't say who called; direction isn't reliable here
            contactName = label,
            sourceApp = target.displayName
        )
        val intent = Intent(applicationContext, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START_RECORDING
            putExtra(RecordingSession.EXTRA_SESSION, session)
        }
        runCatching { applicationContext.startForegroundService(intent) }
            .onFailure { AppCallsLogger.e(TAG, "Couldn't start the recording service: ${it.message}", it) }
    }

    private fun isEnabled(target: AppCallTarget) = when (target) {
        AppCallTarget.WHATSAPP -> prefs.recordWhatsApp
        AppCallTarget.TELEGRAM -> prefs.recordTelegram
        AppCallTarget.INSTAGRAM -> prefs.recordInstagram
        AppCallTarget.SNAPCHAT -> prefs.recordSnapchat
    }

    /**
     * Is this notification an in-progress call?
     *
     * STRICT rule (WhatsApp): an ongoing notification tagged CATEGORY_CALL. That pairing is
     * precise - nothing but a live call has both.
     *
     * LOOSE rule (Telegram, Instagram, Snapchat - [loose] = true): Telegram's call notification
     * does not consistently carry CATEGORY_CALL across every app variant/version (see
     * AppCallTarget.TELEGRAM's doc comment), and Instagram/Snapchat never reliably set it at all,
     * so the strict rule alone misses real calls from these apps. The loose rule accepts an
     * ONGOING notification (a chat message, like or story reply is never ongoing - it is
     * dismissible) that has ANY of these call signals:
     *   - category is CALL, or
     *   - it carries a call-style hint: Notification.CallStyle / EXTRA_CALL_TYPE (Android 12+), or
     *   - it uses a full-screen intent (how an incoming call is shown), or
     *   - its text mentions a call ("call", "calling", "ringing", "in call", "video chat").
     * Requiring FLAG_ONGOING_EVENT first is what keeps ordinary chat notifications from ever
     * starting a recording.
     *
     * Every decision is logged with the exact fields seen, so if an app's call notification
     * is not recognised the log shows precisely what it looked like.
     */
    private fun looksLikeOngoingCall(n: Notification, pkg: String, loose: Boolean): Boolean {
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (!ongoing) return false
        if (n.category == Notification.CATEGORY_CALL) return true
        if (!loose) return false

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val sub = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val haystack = "$title $text $sub".lowercase()
        val mentionsCall = CALL_WORDS.any { haystack.contains(it) }
        // Notification.EXTRA_CALL_TYPE exists from Android 12 (API 31); the app supports API 29+, so the
        // constant is only touched inside a plain SDK_INT check that Android Lint recognises.
        val hasCallStyle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            extras?.containsKey(Notification.EXTRA_CALL_TYPE) == true
        } else {
            false
        }
        val hasFullScreen = n.fullScreenIntent != null

        val verdict = mentionsCall || hasCallStyle || hasFullScreen
        AppCallsLogger.d(
            TAG,
            "loose-detect $pkg: ongoing=true category=${n.category} callStyle=$hasCallStyle " +
                "fullScreen=$hasFullScreen mentionsCall=$mentionsCall title='${title.take(40)}' text='${text.take(60)}' -> $verdict"
        )
        return verdict
    }

    private fun sendStop() {
        val intent = Intent(applicationContext, RecordingForegroundService::class.java)
            .setAction(RecordingForegroundService.ACTION_STOP_RECORDING)
        runCatching { applicationContext.startService(intent) }
    }
}
