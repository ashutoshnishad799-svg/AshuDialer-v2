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

    /**
     * Exact-string lookup only - kept private-ish (still used directly by
     * clearRule/setRule's REPLACE-by-primary-key semantics) but no longer
     * how an incoming call's pattern gets resolved. See
     * PatternForIncomingNumber below for why.
     */
    @Query("SELECT patternId FROM vibration_rules WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getPatternIdExact(phoneNumber: String): String?

    /**
     * Every saved rule's number, for matching an incoming call's number
     * against them with phoneNumbersMatch() rather than SQL exact
     * equality. An incoming call's number as Telecom hands it over rarely
     * has the exact same formatting as whatever the person typed into the
     * "Assign a pattern" dialog - one may carry a country code / leading
     * "+" and the other may not, so a straight `WHERE phoneNumber =
     * :phoneNumber` almost never matched and a saved pattern silently
     * never played on a real ringing call, even though the rule itself
     * was stored correctly (visible in the Vibration Patterns list, used
     * on Local Backup export/import, etc.) That symptom - "pattern shows
     * as saved but never triggers" - is exactly what a formatting
     * mismatch on an exact-match query looks like from the outside.
     */
    @Query("SELECT * FROM vibration_rules")
    suspend fun getAllOnce(): List<VibrationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRule(rule: VibrationRuleEntity)

    @Query("DELETE FROM vibration_rules WHERE phoneNumber = :phoneNumber")
    suspend fun clearRule(phoneNumber: String)
}

/**
 * Resolves the pattern for an incoming call's number the same way every
 * other "is this the same number" decision in this app is made -
 * phoneNumbersMatch(), which normalizes formatting (spaces, a missing/
 * extra "+", a missing/extra country code) instead of requiring the
 * incoming number to be byte-for-byte identical to whatever string is
 * stored for the rule. Falls back to the fast exact-match query first
 * since that's the common case (most saved rules will already be in
 * whatever format the person typed matches the log) and only falls
 * through to the full scan + fuzzy match when that misses, so this stays
 * cheap for the overwhelmingly common case while still being correct for
 * the mismatched-formatting one.
 */
suspend fun VibrationRuleDao.getPatternIdForIncomingNumber(number: String): String? {
    getPatternIdExact(number)?.let { return it }
    if (number.isBlank()) return null
    return getAllOnce().firstOrNull { rule ->
        com.ashudialer.app.util.phoneNumbersMatch(rule.phoneNumber, number)
    }?.patternId
}
