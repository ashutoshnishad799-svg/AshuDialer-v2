package com.ashudialer.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Query("SELECT * FROM call_log ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_log ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getRecentSnapshot(limit: Int): List<CallLogEntity>

    @Query("SELECT * FROM call_log WHERE phoneNumber = :phoneNumber ORDER BY timestampMillis DESC")
    suspend fun getHistoryForNumber(phoneNumber: String): List<CallLogEntity>

    @Query("SELECT * FROM call_log WHERE direction = 'MISSED' ORDER BY timestampMillis DESC")
    fun observeMissed(): Flow<List<CallLogEntity>>

    @Query("SELECT timestampMillis FROM call_log")
    suspend fun allTimestamps(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<CallLogEntity>)

    @Delete
    suspend fun delete(entry: CallLogEntity)

    @Query("DELETE FROM call_log WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByNumber(phoneNumber: String)

    @Query("DELETE FROM call_log WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<Long>)

    @Query("DELETE FROM call_log")
    suspend fun clearAll()
}
