// Adapted from ShizuCallRecorder's AppCallNotificationListenerService (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects ongoing WhatsApp/Telegram VoIP calls via their persistent call notification, and
 * drives [AppCallRecordingEngine] the same way [com.ashudialer.app.telecom.InCallActivity]
 * drives [com.ashudialer.app.telecom.CallRecorder] for native telephony calls.
 *
 * Why a notification listener? A VoIP call inside a third-party app never raises
 * TelephonyManager.ACTION_PHONE_STATE_CHANGED - Android has no public broadcast for "a call is
 * happening inside app X". The one reliable signal every well-behaved call app exposes is its
 * *ongoing call* notification: persistent for the call's duration, tagged
 * Notification.CATEGORY_CALL (mandatory for Android 12+'s CallStyle API).
 *
 * Requires the user to grant Notification Access to this app (Settings > Notification access) -
 * the same system permission any notification-reading app needs.
 */
class AppCallNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "AppCalls:NotifListener"
        private const val RECORDINGS_FOLDER_NAME = "Ashu Dialer"
    }

    /**
     * Notification key -> target for every call currently treated as active. Both WhatsApp and
     * Telegram re-post their ongoing-call notification roughly every second to tick the visible
     * call duration, which re-fires onNotificationPosted for the *same* key many times over one
     * call - this map's putIfAbsent semantics ensure a recording session starts only once per
     * call, and stops only when the key is actually removed.
     */
    private val activeCalls = ConcurrentHashMap<String, AppCallTarget>()

    private var engine: AppCallRecordingEngine? = null
    private var shizukuManager: ShizukuConnectionManager? = null
    private var currentTarget: AppCallTarget? = null
    private var recordingStartedAtMs: Long = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppCallsLogger.d(TAG, "Notification listener connected. Scanning existing notifications for in-progress calls...")
        try {
            activeNotifications?.forEach(::handlePosted)
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Failed to scan existing notifications on connect: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppCallsLogger.w(TAG, "Notification listener disconnected.")
        activeCalls.clear()
        stopActiveRecording()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handlePosted(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val target = activeCalls.remove(sbn.key) ?: return
        AppCallsLogger.i(TAG, "${target.key} call notification ended (key=${sbn.key}). Stopping recording.")
        stopActiveRecording()
    }

    // -- Private helpers

    private fun handlePosted(sbn: StatusBarNotification) {
        val target = AppCallTarget.fromPackageName(sbn.packageName) ?: return
        if (!isTargetEnabled(target)) return
        if (!looksLikeOngoingCallNotification(sbn.notification)) return

        // putIfAbsent: if this key is already tracked, this is just the per-second duration tick,
        // not a new call - ignore it to avoid starting a duplicate session.
        if (activeCalls.putIfAbsent(sbn.key, target) != null) return

        val callerLabel = extractCallerLabel(sbn.notification) ?: target.displayName
        AppCallsLogger.i(TAG, "Detected ongoing ${target.key} call (notification key=${sbn.key}). Starting recording.")
        startRecording(sbn.key, target, callerLabel)
    }

    private fun isTargetEnabled(target: AppCallTarget): Boolean {
        val host = AppCallsHost.current ?: return false
        val enabled = try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(500L) { host.isAppCallRecordingEnabled(target) }
            }
        } catch (e: Exception) {
            null
        }
        return enabled ?: false
    }

    /**
     * Requires both CATEGORY_CALL (the category every well-behaved call app sets on its in-call
     * notification) and FLAG_ONGOING_EVENT (sets it apart from a *missed*-call notification,
     * which uses the same category but is dismissible and not ongoing).
     */
    private fun looksLikeOngoingCallNotification(notification: Notification): Boolean {
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        return isOngoing && notification.category == Notification.CATEGORY_CALL
    }

    /** The notification title is usually the contact's name, as shown by the messaging app itself. */
    private fun extractCallerLabel(notification: Notification): String? =
        notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotBlank() }

    private fun startRecording(notificationKey: String, target: AppCallTarget, callerLabel: String) {
        val host = AppCallsHost.current ?: run {
            activeCalls.remove(notificationKey, target)
            return
        }
        if (engine?.isActive == true) {
            AppCallsLogger.w(TAG, "startRecording() called while already recording - ignoring")
            return
        }

        currentTarget = target
        val newEngine = AppCallRecordingEngine(applicationContext)
        engine = newEngine
        val manager = ShizukuConnectionManager(applicationContext) {
            AppCallsLogger.w(TAG, "Shizuku binder died mid-recording - stopping")
            stopActiveRecording()
        }
        shizukuManager = manager

        host.applicationScope.launch {
            try {
                if (!ShizukuConnectionManager.isAvailable() ||
                    !ShizukuConnectionManager.hasPermission(applicationContext) ||
                    !ShizukuConnectionManager.checkServerPermission(android.Manifest.permission.CAPTURE_AUDIO_OUTPUT)
                ) {
                    AppCallsLogger.w(TAG, "Shizuku not available or permission not granted - cannot record this ${target.key} call")
                    activeCalls.remove(notificationKey, target)
                    return@launch
                }
                val shellService = manager.getShellService()

                val dir = File(applicationContext.cacheDir, "appcall_recordings_tmp").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val safeLabel = callerLabel.filter { it.isLetterOrDigit() }.ifBlank { target.key }
                val tempFile = File(dir, "${safeLabel}_${timestamp}_appcall.m4a")

                recordingStartedAtMs = android.os.SystemClock.elapsedRealtime()
                newEngine.start(shellService, tempFile)
                AppCallsLogger.i(TAG, "Recording started for ${target.key} call from '$callerLabel'")
            } catch (e: Exception) {
                AppCallsLogger.e(TAG, "Failed to start app-call recording: ${e.message}", e)
                engine = null
                shizukuManager = null
                activeCalls.remove(notificationKey, target)
            }
        }
    }

    private fun stopActiveRecording() {
        val host = AppCallsHost.current ?: return
        val activeEngine = engine ?: return
        val manager = shizukuManager
        val target = currentTarget

        host.applicationScope.launch {
            try {
                val shellService = runCatching { manager?.getShellService() }.getOrNull()
                activeEngine.stop(shellService)
                manager?.unbind()

                val tempFile = File(applicationContext.cacheDir, "appcall_recordings_tmp")
                    .listFiles()
                    ?.filter { it.isFile }
                    ?.maxByOrNull { it.lastModified() }

                if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
                    val savedFile = saveToPublicStorage(applicationContext, tempFile) ?: run {
                        val permanentDir = File(applicationContext.filesDir, "recordings").apply { mkdirs() }
                        val destination = File(permanentDir, tempFile.name)
                        tempFile.copyTo(destination, overwrite = true)
                        tempFile.delete()
                        destination
                    }
                    AppCallsLogger.i(TAG, "Saved ${target?.key} call recording to ${savedFile.absolutePath}")
                } else {
                    AppCallsLogger.w(TAG, "No audio captured for ${target?.key} call - discarding")
                    tempFile?.delete()
                }
            } catch (e: Exception) {
                AppCallsLogger.e(TAG, "Error finalizing app-call recording: ${e.message}", e)
            } finally {
                engine = null
                shizukuManager = null
                currentTarget = null
                recordingStartedAtMs = 0L
            }
        }
    }

    /** Mirrors CallRecorder.kt's own moveToPublicStorage exactly, so both engines' output lands in the same place with the same naming. */
    private fun saveToPublicStorage(context: Context, tempFile: File): File? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, tempFile.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGS_FOLDER_NAME")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out -> tempFile.inputStream().use { input -> input.copyTo(out) } } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(itemUri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            val legacyFile = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), RECORDINGS_FOLDER_NAME).apply { mkdirs() }, tempFile.name)
            } else null
            if (legacyFile != null) {
                resolver.openInputStream(itemUri)?.use { input -> legacyFile.outputStream().use { out -> input.copyTo(out) } }
            }
            tempFile.delete()
            legacyFile ?: File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), RECORDINGS_FOLDER_NAME), tempFile.name)
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Couldn't move app-call recording to public storage", e)
            null
        }
    }
}
