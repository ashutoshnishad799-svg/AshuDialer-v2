package com.ashudialer.app.telecom

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.app.NotificationManager
import android.provider.Settings

/**
 * MIUI (and some other OEM skins) applies its own background-restriction
 * layer on top of Android's default-dialer role. A third-party app can hold
 * ROLE_DIALER correctly (confirmed via TelecomManager.defaultDialerPackage)
 * and MIUI will still route calls to its own dialer/notification if the app
 * isn't separately allow-listed for "Autostart" and unrestricted battery
 * usage - this is not something Android's public API can detect or fix for
 * us, only MIUI's own settings screens can. This object gets the person
 * there directly instead of leaving them to hunt through Settings.
 */
object OemPermissionHelper {

    fun isLikelyMiui(): Boolean {
        return Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
            Build.BRAND.contains("xiaomi", ignoreCase = true) ||
            Build.BRAND.contains("redmi", ignoreCase = true) ||
            Build.BRAND.contains("poco", ignoreCase = true) ||
            isMiuiBuildPropPresent()
    }

    private fun isMiuiBuildPropPresent(): Boolean {
        return try {
            val getProp = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
            val result = getProp.invoke(null, "ro.miui.ui.version.name")
            val value = result as? String
            val isPresent = value != null && value.isNotBlank()
            isPresent
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Opens MIUI's Autostart manager, scoped to this app where the intent
     * supports it. Several fallbacks are tried in order because the exact
     * component/action has changed across MIUI versions and no single one
     * is guaranteed present.
     */
    fun openMiuiAutostartSettings(context: Context): Boolean {
        val attempts = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            },
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.MainActivity"
                )
            }
        )
        for (intent in attempts) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next fallback.
            } catch (_: Exception) {
                // Try the next fallback.
            }
        }
        return openAppBatterySettings(context)
    }

    /** Generic (non-MIUI-specific) battery optimization screen for this app, as a last resort. */
    // ------------------------------------------------------------------------------------------
    // "Calls on the lock screen" - everything that has to be allowed for a ringing call to turn the screen
    // on, open full screen, and stay usable without asking for the PIN.
    // ------------------------------------------------------------------------------------------

    /** One thing that is switched off and stops calls from showing properly on the lock screen. */
    data class LockScreenIssue(val id: String, val title: String, val hint: String)

    fun canDrawOverlays(context: Context): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (_: Throwable) { true }

    fun openOverlaySettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) { openAppBatterySettings(context) }

    /**
     * Xiaomi / Redmi / POCO (MIUI, HyperOS) keep two of their own per-app switches that Android does not know
     * about. Without them MIUI turns a full-screen call into a plain notification, does not switch the screen
     * on, and hides the call screen behind the lock screen, so answering asks for the PIN. Both are read from
     * MIUI's own app-ops (codes 10020 = show on lock screen, 10021 = start from background).
     * Returns true / false, or null when the phone did not answer (some HyperOS builds), meaning "unknown".
     */
    private fun miuiOp(context: Context, op: Int): Boolean? {
        if (!isLikelyMiui()) return null
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val method = ops.javaClass.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val result = method.invoke(ops, op, android.os.Process.myUid(), context.packageName) as Int
            result == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) { null }
    }

    fun miuiShowOnLockScreenAllowed(context: Context): Boolean? = miuiOp(context, 10020)
    fun miuiBackgroundStartAllowed(context: Context): Boolean? = miuiOp(context, 10021)

    private const val PREFS = "ashu_oem_prefs"
    private const val KEY_MIUI_CONFIRMED = "miui_lockscreen_confirmed"

    /** The person tapped "I've turned these on" (used only when the phone cannot report the switches). */
    fun confirmMiuiPermissions(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MIUI_CONFIRMED, true).apply()
    }

    fun isMiuiConfirmedByUser(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MIUI_CONFIRMED, false)

    /** Everything currently switched off that matters for lock-screen calls (empty = all good). */
    fun lockScreenIssues(context: Context): List<LockScreenIssue> = buildList<LockScreenIssue> {
        if (!canUseFullScreenIntent(context)) {
            add(LockScreenIssue("fsi", "Full screen notifications", "Lets the call screen open over the lock screen"))
        }
        if (!canDrawOverlays(context)) {
            add(LockScreenIssue("overlay", "Display over other apps", "Lets the call screen start while the phone is locked or asleep"))
        }
        if (isLikelyMiui()) {
            val lock = miuiShowOnLockScreenAllowed(context)
            val bg = miuiBackgroundStartAllowed(context)
            val confirmed = isMiuiConfirmedByUser(context)
            // A confirmed "true" never shows; an unknown (null) shows until the person says they turned it on.
            if (lock == false || (lock == null && !confirmed)) {
                add(LockScreenIssue("miui", "Show on lock screen", "Xiaomi: turn this on in the app's permission list"))
            }
            if (bg == false || (bg == null && !confirmed)) {
                add(LockScreenIssue("miui", "Display pop-up windows while running in the background", "Xiaomi: turn this on in the app's permission list"))
            }
        }
    }.distinctBy { it.title }

    /** Opens the exact settings page for one issue returned by [lockScreenIssues]. */
    fun openLockScreenIssue(context: Context, id: String): Boolean = when (id) {
        "fsi" -> openFullScreenIntentSettings(context)
        "overlay" -> openOverlaySettings(context)
        else -> openMiuiPermissionEditor(context)
    }

    /** MIUI's own per-app permission list (the page with "Show on lock screen" and the pop-up switch). */
    fun openMiuiPermissionEditor(context: Context): Boolean {
        val pkg = context.packageName
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", pkg),
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                .putExtra("extra_pkgname", pkg),
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setPackage("com.miui.securitycenter")
                .putExtra("extra_pkgname", pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { /* try the next page */ }
        }
        return false
    }

    /**
     * Whether this app may pop the incoming-call screen over the lock screen.
     *
     * Only Android 14+ (API 34) gates this behind a user-visible switch
     * ("Full screen notifications"). On Android 13 and below the manifest
     * permission is granted automatically, so this returns true. Without it a
     * locked phone shows only a small heads-up banner and the big call screen
     * never wakes the display - the "screen lock rahta hai to full screen call
     * nahi aata" problem.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } catch (_: Throwable) {
            true
        }
    }

    /** Opens the "Full screen notifications" switch for this app (Android 14+), else the app's details page. */
    fun openFullScreenIntentSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            openAppBatterySettings(context)
        }
    }

    fun openAppBatterySettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens Android's own Default apps screen (Settings -> Apps -> Default
     * apps) as a manual fallback for setting the default dialer.
     *
     * Why this exists: RoleManager.createRequestRoleIntent(ROLE_DIALER)
     * (used by requestDefaultDialerIntent in DialerPermissions.kt) hands
     * off to a system/OEM-owned confirmation UI this app has no control
     * over. On MIUI specifically, that handoff can occasionally never
     * return a result to onActivityResult at all - not a crash, just a
     * dialog that doesn't resolve - which looks to the person like the
     * whole app froze on the setup screen, with no way forward except
     * force-closing. Manually going to Settings -> Apps -> Default apps ->
     * Phone app and picking Ashu Dialer there is the exact same underlying
     * action (it sets the same ROLE_DIALER role) but through a path this
     * app isn't waiting on a callback from, so it works even when the
     * RoleManager intent itself is stuck. MainActivity's existing
     * ON_RESUME re-check (see the comment there) picks up the change the
     * moment the person comes back, with no extra plumbing needed here.
     */
    fun openDefaultAppsSettings(context: Context): Boolean {
        val attempts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            // MIUI's own Default apps screen, tried before the generic app
            // details fallback since it's a closer match to what the
            // person actually needs (the Phone app default picker, not
            // just this app's own settings page).
            add(Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$ManageDefaultAppsActivity"
                )
            })
        }
        for (intent in attempts) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next fallback.
            } catch (_: Exception) {
                // Try the next fallback.
            }
        }
        return openAppBatterySettings(context)
    }
}
