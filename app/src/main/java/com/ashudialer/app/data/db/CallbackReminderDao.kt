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
