/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is adapted from ShizuCallRecorder
 * (https://github.com/kitsumed/ShizuCallRecorder),
 * Copyright (C) kitsumed and contributors, licensed under GPL-3.0-or-later.
 * The original "adapted from" note is kept below. Changes were made by
 * Ashutosh Nishad in 2026.
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
