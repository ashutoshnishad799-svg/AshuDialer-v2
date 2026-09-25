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
        packageNames = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
        // Unlike WhatsApp, Telegram's call notification does not
        // consistently carry Notification.CATEGORY_CALL across its app
        // variants/versions - some builds only set the newer Android 12+
        // CallStyle metadata (EXTRA_CALL_TYPE) or a full-screen intent
        // without also setting category, which the STRICT rule below
        // requires and silently misses. That's what made Telegram calls
        // not get picked up for recording at all, while WhatsApp (whose
        // notification does reliably set CATEGORY_CALL) worked. Loose
        // detection is the same fallback Instagram/Snapchat already use:
        // it only kicks in once FLAG_ONGOING_EVENT is already true (never
        // fires for a non-call notification), so this doesn't risk
        // matching something that isn't actually a call.
        looseDetection = true
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
