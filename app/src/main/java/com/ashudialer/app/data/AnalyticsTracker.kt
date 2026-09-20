package com.ashudialer.app.data

import android.content.Context
import android.os.Bundle
import com.ashudialer.app.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Privacy-first aggregate usage analytics.
 *
 * No phone numbers, contact names, call contents, recording contents or any other personally
 * identifying information is ever sent. Every event carries only a short fixed label chosen from
 * the constants below, so what can be reported is limited by construction: there is nowhere to put
 * a number or a name.
 *
 * What the Firebase console then shows without any extra code:
 *   - Active users (daily / weekly / monthly), sessions and retention.
 *   - New installs: Firebase logs `first_open` by itself on the first launch after an install.
 *   - App version breakdown: every event below carries `app_version`.
 * What it can NOT show: raw download counts, because the APK is distributed through GitHub Releases,
 * not a store. GitHub keeps a download counter per release asset (Releases page, or
 * `gh release view <tag> --json assets`).
 */
object AnalyticsTracker {

    /** Which feature was used. A fixed list on purpose - never pass user data as a value. */
    object Feature {
        const val CALL_RECORDING_ENABLED = "call_recording_enabled"
        const val AUTO_RECORD_ENABLED = "auto_record_enabled"
        const val PRIVATE_SPACE_SETUP = "private_space_setup"
        const val THEME_CHANGED = "theme_changed"
        const val INCOMING_STYLE_CHANGED = "incoming_style_changed"
        const val UPDATE_INSTALLED = "update_installed"
        const val SETUP_COMPLETED = "setup_completed"
    }

    fun logAppOpened(context: Context) {
        log(context, "ashu_app_open")
    }

    /** Records that a feature was switched on / used, with an optional short fixed detail such as a theme id. */
    fun logFeature(context: Context, feature: String, detail: String? = null) {
        log(context, "ashu_feature", feature, detail)
    }

    private fun log(context: Context, event: String, feature: String? = null, detail: String? = null) {
        try {
            FirebaseAnalytics.getInstance(context).logEvent(event, Bundle().apply {
                putString("app_version", BuildConfig.VERSION_NAME)
                putString("android_sdk", android.os.Build.VERSION.SDK_INT.toString())
                if (feature != null) putString("feature", feature.take(40))
                // Only short, id-like details (theme ids, style ids). Anything longer or containing
                // characters other than letters/digits/_/- is dropped rather than sent.
                if (detail != null && detail.length <= 32 && detail.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    putString("detail", detail)
                }
            })
        } catch (_: Exception) {
            // Analytics must never affect dialer startup or calling.
        }
    }
}
