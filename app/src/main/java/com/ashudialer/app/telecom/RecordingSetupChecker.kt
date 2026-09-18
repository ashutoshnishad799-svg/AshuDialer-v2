package com.ashudialer.app.telecom

import android.content.ComponentName
import android.content.Context
import android.app.NotificationManager
import com.ashudialer.app.appcalls.ShizukuConnectionManager

/**
 * Single source of truth for recording prerequisites. Call recording is now
 * intentionally Shizuku-only: there is no root, Magisk, privileged-install,
 * accessibility, or legacy direct audio-source setup path in Ashu Dialer.
 */
object RecordingSetupChecker {
    fun isShizukuInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("moe.shizuku.manager", 0)
        true
    } catch (_: Exception) {
        false
    }

    fun isShizukuRunning(): Boolean = ShizukuConnectionManager.isAvailable()

    fun hasShizukuPermission(): Boolean = ShizukuConnectionManager.hasPermission()

    /**
     * Shizuku authorization is the setup prerequisite we can verify here.
     * CAPTURE_AUDIO_OUTPUT is a privileged Android permission and is not granted
     * merely by authorizing Ashu Dialer in Shizuku. The actual audio source is
     * therefore validated when the recording pipeline starts.
     */
    fun hasShizukuAudioCapturePermission(): Boolean =
        isShizukuRunning() && hasShizukuPermission()

    fun isShizukuReady(): Boolean =
        isShizukuRunning() && hasShizukuPermission()

    fun isAppCallNotificationAccessGranted(context: Context): Boolean = try {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val component = ComponentName(
            context,
            "com.ashudialer.app.appcalls.AppCallNotificationListenerService"
        )
        notificationManager?.isNotificationListenerAccessGranted(component) ?: false
    } catch (_: Exception) {
        false
    }
}
