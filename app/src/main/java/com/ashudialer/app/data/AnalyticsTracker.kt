package com.ashudialer.app.data

import android.content.Context
import android.os.Bundle
import com.ashudialer.app.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Privacy-first aggregate usage analytics.
 *
 * No phone numbers, contacts, call contents, or personally identifying
 * information are sent by this tracker. Firebase Analytics provides the
 * aggregate active-user/session counts in the Firebase console.
 */
object AnalyticsTracker {

    fun logAppOpened(context: Context) {
        try {
            FirebaseAnalytics.getInstance(context).logEvent("ashu_app_open", Bundle().apply {
                putString("app_version", BuildConfig.VERSION_NAME)
            })
        } catch (_: Exception) {
            // Analytics must never affect dialer startup or calling.
        }
    }
}
