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
    val packageNames: List<String>,
    /** True for apps whose call notification is not reliably tagged as a call (see Instagram / Snapchat below). */
    val looseDetection: Boolean = false
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
    ),
    INSTAGRAM(
        key = "instagram",
        displayName = "Instagram",
        // "com.instagram.android" is the main app. "com.instagram.barcelona" is Threads, which has no
        // calls, so it is deliberately NOT listed. Lite is a separate, lighter build of Instagram.
        packageNames = listOf("com.instagram.android", "com.instagram.lite"),
        // Instagram does not reliably tag its call notification the way WhatsApp/Telegram do
        // (CATEGORY_CALL + ONGOING), so detection also accepts a call-like notification: see
        // AppCallNotificationListenerService.looksLikeOngoingCall(loose = true).
        looseDetection = true
    ),
    SNAPCHAT(
        key = "snapchat",
        displayName = "Snapchat",
        packageNames = listOf("com.snapchat.android"),
        looseDetection = true
    );

    companion object {
        fun fromPackageName(packageName: String): AppCallTarget? =
            entries.firstOrNull { packageName in it.packageNames }
    }
}
