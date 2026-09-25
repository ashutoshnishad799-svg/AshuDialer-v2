// Adapted from ShizuCallRecorder's PhoneStateReceiver (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.ashudialer.app.appcalls.AppCallsLogger

/**
 * Listens for the system PHONE_STATE broadcast and hands it to [CallRecordingCoordinator].
 *
 * This is what makes recording work for every carrier call regardless of which dialer placed it,
 * and it keeps working when the app's UI process isn't running - the manifest registration wakes
 * the app up for each call state change.
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        AppCallsLogger.v("AppCalls:PhoneState", "state=$state number=${number ?: "(none)"}")
        CallRecordingCoordinator.getInstance(context).handlePhoneState(state, number)
    }
}
