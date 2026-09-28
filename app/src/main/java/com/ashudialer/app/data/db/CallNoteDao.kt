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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallNoteDao {


    @Query("SELECT * FROM call_notes ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<CallNoteEntity>>


    @Query("SELECT * FROM call_notes WHERE phoneNumber = :phoneNumber ORDER BY createdAtMillis DESC")
    fun observeForNumber(phoneNumber: String): Flow<List<CallNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: CallNoteEntity): Long

    @Update
    suspend fun update(note: CallNoteEntity)

    @Delete
    suspend fun delete(note: CallNoteEntity)

    @Query("DELETE FROM call_notes")
    suspend fun clearAll()
}
