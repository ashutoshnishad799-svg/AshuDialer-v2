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
        if (!looksLikeOngoingCall(sbn.notification)) return
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
    }

    /** Ongoing (not a dismissible missed-call) notification tagged as a call. */
    private fun looksLikeOngoingCall(n: Notification): Boolean =
        (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 && n.category == Notification.CATEGORY_CALL

    private fun sendStop() {
        val intent = Intent(applicationContext, RecordingForegroundService::class.java)
            .setAction(RecordingForegroundService.ACTION_STOP_RECORDING)
        runCatching { applicationContext.startService(intent) }
    }
}
