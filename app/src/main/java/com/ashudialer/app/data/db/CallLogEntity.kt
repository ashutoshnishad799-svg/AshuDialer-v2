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

enum class CallDirection { INCOMING, OUTGOING, MISSED, REJECTED }

@Entity(
    tableName = "call_log",
    indices = [Index(value = ["phoneNumber", "timestampMillis"], unique = true)]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String?,
    val direction: CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Int = 0,
    val isSpam: Boolean = false,
    val photoUri: String? = null
)
