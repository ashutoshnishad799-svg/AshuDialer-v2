package com.ashudialer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivateSpaceDao {

    @Query("SELECT * FROM private_space_config WHERE id = 1 LIMIT 1")
    fun observe(): Flow<PrivateSpaceEntity?>

    @Query("SELECT * FROM private_space_config WHERE id = 1 LIMIT 1")
    suspend fun getSnapshot(): PrivateSpaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: PrivateSpaceEntity)

    @Query("DELETE FROM private_space_config")
    suspend fun clear()
}
