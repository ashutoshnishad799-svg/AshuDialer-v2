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
package com.ashudialer.app.appcalls.recording

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/** Handles the "Delete" button on the "Recording saved" notification. */
class PostRecordingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecordingNotificationHelper.ACTION_DELETE_RECORDING) return
        val path = intent.getStringExtra(RecordingNotificationHelper.EXTRA_RECORDING_PATH) ?: return
        RecordingLibrary.delete(context.applicationContext, File(path))
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(RecordingNotificationHelper.POST_CALL_NOTIFICATION_ID)
    }
}
