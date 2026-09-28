/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ashudialer.app.AshuDialerApp

class ViewModelFactory(private val app: AshuDialerApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                app.callLogRepository,
                app.systemCallLogRepository,
                app.contactsRepository,
                app.themePreference,
                app.appSettingsRepository,
                app.authRepository,
                app.cloudBackupRepository,
                app.database.blockedNumberDao(),
                app.callNoteRepository,
                app.database.simRoutingDao(),
                app.database.vibrationRuleDao(),
                app.localBackupRepository,
                app.videoCallSignalingRepository,
                app.quietHoursRepository,
                app.callInsightsRepository,
                app.reconnectRepository,
                app.privateSpaceRepository,
                app.localAuthRepository,
                app.callbackReminderRepository,
                app.database.reportedSpamDao()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
