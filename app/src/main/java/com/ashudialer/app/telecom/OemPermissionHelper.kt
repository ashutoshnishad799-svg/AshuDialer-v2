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
