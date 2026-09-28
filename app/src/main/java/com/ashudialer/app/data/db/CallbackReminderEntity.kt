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
package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A "call me back at this time" reminder the person set from a Recents entry
 * (usually a missed call). Kept in Room, not just as a live AlarmManager
 * PendingIntent, so the reminder survives a device reboot (BootReceiver can
 * re-schedule from this table) and so the UI has something durable to list/
 * cancel from - an AlarmManager alarm alone is fire-and-forget with no way
 * to query "what's still pending".
 */
@Entity(
    tableName = "callback_reminders",
    indices = [Index(value = ["phoneNumber"]), Index(value = ["triggerAtMillis"])]
)
data class CallbackReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String,
    val triggerAtMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    // False once the alarm has fired and the notification has been shown -
    // kept as a row (rather than deleted) briefly so BootReceiver rescheduling
    // logic and any in-flight query can't race with a delete of the same row.
    val fired: Boolean = false
)
