package com.ashudialer.app.telecom

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.ashudialer.app.appcalls.AppCallNotificationListenerService
import com.ashudialer.app.appcalls.ShizukuConnectionManager
import com.ashudialer.app.appcalls.recording.RecordingPrefs

/** One step of the call-recording setup, in the order Ever Dialer asks for them. */
enum class SetupStep(val title: String, val why: String, val required: Boolean) {
    SHIZUKU_INSTALLED(
        "Shizuku app installed",
        "Shizuku lets Ashu Dialer capture call audio without root. Install it once from GitHub or Play Store.",
        true
    ),
    SHIZUKU_RUNNING(
        "Shizuku is running",
        "Shizuku must be started (Wireless debugging, or root). It has to be started again after every reboot unless you use the auto-start options below.",
        true
    ),
    SHIZUKU_PERMISSION(
        "Shizuku permission for Ashu Dialer",
        "Allow Ashu Dialer to use Shizuku. This is the permission that actually unlocks call-audio recording.",
        true
    ),
    NOTIFICATIONS(
        "Notifications",
        "Shows the 'Recording' notification and lets you Open / Share / Delete a recording right after a call.",
        true
    ),
    PHONE_STATE(
        "Phone (call state)",
        "Detects when a call starts, is answered and ends, so recording starts and stops on its own.",
        true
    ),
    CONTACTS(
        "Contacts",
        "Names recordings after the person you spoke with and powers the 'ignore contacts' filter.",
        false
    ),
    CALL_LOG(
        "Call log",
        "Reads the number of outgoing calls so recordings get the right name and direction.",
        false
    ),
    BATTERY(
        "Unrestricted battery",
        "Stops Android killing the recorder in the background in the middle of a call.",
        true
    ),
    STORAGE(
        "Storage access",
        "Only needed on Android 9 and below, to save recordings into the Music folder.",
        false
    ),
    NOTIFICATION_ACCESS(
        "Notification access (WhatsApp / Telegram)",
        "Lets Ashu Dialer notice a WhatsApp or Telegram call is in progress so it can record it. Only needed for app calls.",
        false
    );
}

/** Whether each [SetupStep] is currently satisfied. */
data class SetupStatus(val done: Map<SetupStep, Boolean>) {
    fun isDone(step: SetupStep) = done[step] == true

    /** Every required step that applies to this device is done -> recording can work. */
    val readyForPhoneCalls: Boolean
        get() = SetupStep.entries.filter { it.required }.all { isDone(it) }

    val missingRequired: List<SetupStep>
        get() = SetupStep.entries.filter { it.required && !isDone(it) }
}

/**
 * Shizuku-only recording readiness. No root, no Magisk, no priv-app - the only thing that
 * grants call-audio access is the user running Shizuku and allowing this app to use it.
 */
object RecordingSetupChecker {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isShizukuInstalled(context: Context): Boolean =
        ShizukuConnectionManager.getPackageName(context) != null ||
            runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    fun isShizukuRunning(): Boolean = ShizukuConnectionManager.isAvailable()

    fun hasShizukuPermission(context: Context): Boolean = ShizukuConnectionManager.hasPermission(context)

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) granted(context, Manifest.permission.POST_NOTIFICATIONS)
        else context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false

    fun hasBatteryExemption(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun hasStorageAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 || granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun hasNotificationAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val mine = ComponentName(context, AppCallNotificationListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    fun check(context: Context): SetupStatus = SetupStatus(
        mapOf(
            SetupStep.SHIZUKU_INSTALLED to isShizukuInstalled(context),
            SetupStep.SHIZUKU_RUNNING to isShizukuRunning(),
            SetupStep.SHIZUKU_PERMISSION to (isShizukuRunning() && hasShizukuPermission(context)),
            SetupStep.NOTIFICATIONS to hasNotifications(context),
            SetupStep.PHONE_STATE to granted(context, Manifest.permission.READ_PHONE_STATE),
            SetupStep.CONTACTS to granted(context, Manifest.permission.READ_CONTACTS),
            SetupStep.CALL_LOG to granted(context, Manifest.permission.READ_CALL_LOG),
            SetupStep.BATTERY to hasBatteryExemption(context),
            SetupStep.STORAGE to hasStorageAccess(context),
            SetupStep.NOTIFICATION_ACCESS to hasNotificationAccess(context)
        )
    )

    /** Convenience for screens that only need one yes/no: can a phone call be recorded right now? */
    fun isReady(context: Context): Boolean = check(context).readyForPhoneCalls

    /** True once the user turned call recording on in the app's own settings. */
    fun isFeatureOn(context: Context): Boolean = RecordingPrefs(context).callRecordingEnabled
}
