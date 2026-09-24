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
    suspend fun getPreferredSimIdExact(phoneNumber: String): String?

    @Query("SELECT * FROM sim_routing_rules")
    suspend fun getAllOnce(): List<SimRoutingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRule(rule: SimRoutingEntity)

    @Query("DELETE FROM sim_routing_rules WHERE phoneNumber = :phoneNumber")
    suspend fun clearRule(phoneNumber: String)
}

/**
 * Same formatting-mismatch problem as VibrationRuleDao's
 * getPatternIdForIncomingNumber and ReportedSpamDao's isReported - a
 * number saved here as a routing rule and the number placeCallDirect is
 * actually dialing can be formatted differently (a leading "+91" typed
 * into one place and not the other, for instance), so an exact SQL match
 * would silently miss a real rule and fall through to asking again (or
 * worse, to the wrong SIM) even though the rule genuinely exists.
 */
suspend fun SimRoutingDao.getPreferredSimId(phoneNumber: String): String? {
    getPreferredSimIdExact(phoneNumber)?.let { return it }
    if (phoneNumber.isBlank()) return null
    return getAllOnce().firstOrNull { rule ->
        com.ashudialer.app.util.phoneNumbersMatch(rule.phoneNumber, phoneNumber)
    }?.preferredSimAccountId
}
