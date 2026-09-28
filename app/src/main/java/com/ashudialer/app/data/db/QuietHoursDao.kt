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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuietHoursDao {

    @Query("SELECT * FROM quiet_hours_schedule WHERE id = 1 LIMIT 1")
    fun observe(): Flow<QuietHoursEntity?>

    @Query("SELECT * FROM quiet_hours_schedule WHERE id = 1 LIMIT 1")
    suspend fun getSnapshot(): QuietHoursEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: QuietHoursEntity)
}
