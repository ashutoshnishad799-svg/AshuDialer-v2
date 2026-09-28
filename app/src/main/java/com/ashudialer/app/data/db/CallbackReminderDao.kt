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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallbackReminderDao {

    @Query("SELECT * FROM callback_reminders WHERE fired = 0 ORDER BY triggerAtMillis ASC")
    fun observePending(): Flow<List<CallbackReminderEntity>>

    @Query("SELECT * FROM callback_reminders WHERE fired = 0 ORDER BY triggerAtMillis ASC")
    suspend fun getAllPending(): List<CallbackReminderEntity>

    @Query("SELECT * FROM callback_reminders WHERE phoneNumber = :phoneNumber AND fired = 0 LIMIT 1")
    suspend fun getPendingForNumber(phoneNumber: String): CallbackReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: CallbackReminderEntity): Long

    @Query("UPDATE callback_reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Delete
    suspend fun delete(reminder: CallbackReminderEntity)

    @Query("DELETE FROM callback_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM callback_reminders WHERE fired = 1")
    suspend fun clearFired()
}
