package com.ashudialer.app.data

import com.ashudialer.app.data.db.QuietHoursDao
import com.ashudialer.app.data.db.QuietHoursEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class QuietHoursRepository(private val dao: QuietHoursDao) {

    fun observe(): Flow<QuietHoursEntity> = dao.observe().map { it ?: QuietHoursEntity() }

    suspend fun save(entity: QuietHoursEntity) = dao.save(entity)

    /**
     * Whether right now falls inside the configured quiet window. Handles a
     * window that crosses midnight (e.g. 22:00 -> 07:00) the same way a
     * person would describe it in conversation - "after 10pm or before 7am" -
     * rather than only supporting a same-day start/end range.
     */
    suspend fun isCurrentlyQuietHours(): Boolean {
        val schedule = dao.getSnapshot() ?: return false
        if (!schedule.enabled) return false

        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = schedule.startHour * 60 + schedule.startMinute
        val endMinutes = schedule.endHour * 60 + schedule.endMinute

        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            // Crosses midnight - "quiet" is everything from start to
            // midnight, plus everything from midnight to end.
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    /**
     * In-memory record of when each number last called during quiet hours,
     * purely for the repeat-caller bypass - deliberately not persisted to
     * disk, since this only ever needs to survive within the current
     * process's uptime, not across app restarts or reboots.
     */
    private val recentQuietHourCalls = mutableMapOf<String, Long>()

    /**
     * True if this number already called once within the configured repeat
     * window during quiet hours - meaning this second call should ring
     * through rather than be silently declined again, on the theory that a
     * caller trying twice in a short window might have something urgent.
     * Also records this call's timestamp for the *next* call's check.
     */
    fun shouldBypassAsRepeatCaller(number: String, windowMinutes: Int): Boolean {
        val now = System.currentTimeMillis()
        val lastCallAt = recentQuietHourCalls[number]
        recentQuietHourCalls[number] = now
        if (lastCallAt == null) return false
        val windowMillis = windowMinutes * 60_000L
        return (now - lastCallAt) <= windowMillis
    }
}
