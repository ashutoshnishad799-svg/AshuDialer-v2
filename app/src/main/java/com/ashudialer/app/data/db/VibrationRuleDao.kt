package com.ashudialer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VibrationRuleDao {

    @Query("SELECT * FROM vibration_rules ORDER BY phoneNumber")
    fun observeAll(): Flow<List<VibrationRuleEntity>>

    @Query("SELECT patternId FROM vibration_rules WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getPatternId(phoneNumber: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRule(rule: VibrationRuleEntity)

    @Query("DELETE FROM vibration_rules WHERE phoneNumber = :phoneNumber")
    suspend fun clearRule(phoneNumber: String)
}
