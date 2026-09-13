package com.ashudialer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SimRoutingDao {

    @Query("SELECT * FROM sim_routing_rules ORDER BY phoneNumber")
    fun observeAll(): Flow<List<SimRoutingEntity>>

    @Query("SELECT preferredSimAccountId FROM sim_routing_rules WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getPreferredSimId(phoneNumber: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRule(rule: SimRoutingEntity)

    @Query("DELETE FROM sim_routing_rules WHERE phoneNumber = :phoneNumber")
    suspend fun clearRule(phoneNumber: String)
}
