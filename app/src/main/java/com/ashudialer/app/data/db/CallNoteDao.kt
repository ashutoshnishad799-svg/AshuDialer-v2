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
