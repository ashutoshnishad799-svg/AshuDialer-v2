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
