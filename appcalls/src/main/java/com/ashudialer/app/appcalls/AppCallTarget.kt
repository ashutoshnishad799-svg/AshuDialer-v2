// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls

/**
 * Messaging apps whose VoIP calls [AppCallNotificationListenerService] knows how to detect.
 *
 * [packageNames] lists every known package id that posts the same ongoing-call notification
 * pattern for a given app, so business/forked variants are covered too - not just the main
 * package.
 */
enum class AppCallTarget(
    val key: String,
    val displayName: String,
    val packageNames: List<String>
) {
    WHATSAPP(
        key = "whatsapp",
        displayName = "WhatsApp",
        // com.whatsapp matches the package name AshuDialer's own WhatsAppLauncher.kt already uses.
        packageNames = listOf("com.whatsapp", "com.whatsapp.w4b")
    ),
    TELEGRAM(
        key = "telegram",
        displayName = "Telegram",
        packageNames = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
    );

    companion object {
        fun fromPackageName(packageName: String): AppCallTarget? =
            entries.firstOrNull { packageName in it.packageNames }
    }
}
