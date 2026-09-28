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
interface BlockedNumberDao {

    @Query("SELECT * FROM blocked_numbers ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE phoneNumber = :number)")
    suspend fun isBlocked(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(entry: BlockedNumberEntity)

    @Delete
    suspend fun unblock(entry: BlockedNumberEntity)
}
