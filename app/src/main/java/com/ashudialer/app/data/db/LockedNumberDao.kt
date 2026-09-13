package com.ashudialer.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedNumberDao {

    @Query("SELECT * FROM locked_numbers ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<LockedNumberEntity>>

    @Query("SELECT * FROM locked_numbers")
    suspend fun getAllSnapshot(): List<LockedNumberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LockedNumberEntity)

    @Delete
    suspend fun delete(entity: LockedNumberEntity)

    @Query("DELETE FROM locked_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByNumber(phoneNumber: String)
}
