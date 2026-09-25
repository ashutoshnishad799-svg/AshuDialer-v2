package com.ashudialer.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportedSpamDao {

    @Query("SELECT * FROM reported_spam ORDER BY reportedAtMillis DESC")
    fun observeAll(): Flow<List<ReportedSpamEntity>>

    @Query("SELECT * FROM reported_spam")
    suspend fun getAllOnce(): List<ReportedSpamEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM reported_spam WHERE phoneNumber = :number)")
    suspend fun isReportedExact(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun report(entry: ReportedSpamEntity)

    @Delete
    suspend fun unreport(entry: ReportedSpamEntity)

    @Query("DELETE FROM reported_spam WHERE phoneNumber = :number")
    suspend fun unreportByNumber(number: String)
}

/**
 * Same "formatting differs, same number" problem as every other
 * number-lookup in this app (see VibrationRuleDao's
 * getPatternIdForIncomingNumber for the identical fix applied there) -
 * whether a number the person is currently looking at (an incoming call,
 * a Recents/Contact detail entry) has already been reported needs
 * phoneNumbersMatch(), not exact string equality, since the same number
 * can show up formatted differently between where it was reported from
 * and where it's being checked.
 */
suspend fun ReportedSpamDao.isReported(number: String): Boolean {
    if (isReportedExact(number)) return true
    if (number.isBlank()) return false
    return getAllOnce().any { com.ashudialer.app.util.phoneNumbersMatch(it.phoneNumber, number) }
}
