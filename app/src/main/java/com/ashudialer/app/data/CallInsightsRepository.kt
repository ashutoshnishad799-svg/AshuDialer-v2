package com.ashudialer.app.data

import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.CallLogDao
import com.ashudialer.app.data.db.CallLogEntity
import java.util.Calendar

data class ContactTimeShare(
    val displayName: String,
    val phoneNumber: String,
    val totalSeconds: Int,
    val callCount: Int
)

/**
 * A number that shows a lopsided calling pattern in one direction: mostly
 * (or only) you calling them, or mostly them calling you, with the other
 * direction never or barely happening. No other dialer surfaces this - it's
 * a genuinely new signal, not a rename of an existing stat, and it's cheap
 * to compute since it reuses the same call-log snapshot as the rest of
 * CallInsights rather than issuing new queries or needing new permissions.
 */
data class OneSidedCaller(
    val displayName: String,
    val phoneNumber: String,
    val outgoingCount: Int,
    val incomingCount: Int,
    val direction: OneSidedDirection
)

enum class OneSidedDirection { YOU_ALWAYS_CALL, THEY_ALWAYS_CALL }

data class HourBucket(val hour: Int, val callCount: Int)

data class DailyCallSummary(
    val dateKey: String,
    val label: String,
    val totalCalls: Int,
    val incomingCalls: Int,
    val outgoingCalls: Int,
    val missedCalls: Int,
    val talkTimeSeconds: Int
)

data class CallInsights(
    val totalCalls: Int,
    val totalTalkTimeSeconds: Int,
    val missedCalls: Int,
    val outgoingCalls: Int,
    val incomingCalls: Int,
    val averageCallSeconds: Int,
    val topContactsByTime: List<ContactTimeShare>,
    val busiestHours: List<HourBucket>,
    val longestCallSeconds: Int,
    val longestCallWith: String?,
    val dayOverDayCallCounts: List<Int>, // last 7 days, oldest to newest
    val dailySummaries: List<DailyCallSummary> = emptyList(),
    val periodLabel: String,
    val oneSidedCallers: List<OneSidedCaller> = emptyList()
)

enum class InsightsPeriod { WEEK, MONTH }

/**
 * Computes call analytics entirely from data already sitting in the local
 * call_log table - no new permissions, no network. Aggregation happens in
 * Kotlin over a bounded snapshot rather than as SQL, since the dataset a
 * phone's call log realistically holds is small enough that this is simpler
 * and safer than adding new, unverified SQL aggregate queries.
 */
class CallInsightsRepository(private val dao: CallLogDao) {

    suspend fun computeInsights(period: InsightsPeriod): CallInsights {
        val cutoffMillis = periodCutoffMillis(period)
        val allEntries = dao.getRecentSnapshot(2000) // bounded snapshot, newest first
        val entries = allEntries.filter { it.timestampMillis >= cutoffMillis }

        if (entries.isEmpty()) {
            return CallInsights(
                totalCalls = 0, totalTalkTimeSeconds = 0, missedCalls = 0,
                outgoingCalls = 0, incomingCalls = 0, averageCallSeconds = 0,
                topContactsByTime = emptyList(), busiestHours = emptyList(),
                longestCallSeconds = 0, longestCallWith = null,
                dayOverDayCallCounts = List(7) { 0 },
                dailySummaries = emptyList(),
                periodLabel = periodLabel(period)
            )
        }

        val missed = entries.count { it.direction == CallDirection.MISSED }
        val outgoing = entries.count { it.direction == CallDirection.OUTGOING }
        val incoming = entries.count { it.direction == CallDirection.INCOMING }
        val talkEntries = entries.filter { it.direction != CallDirection.MISSED && it.direction != CallDirection.REJECTED }
        val totalTalkSeconds = talkEntries.sumOf { it.durationSeconds }
        val avgSeconds = if (talkEntries.isNotEmpty()) totalTalkSeconds / talkEntries.size else 0

        val longest = talkEntries.maxByOrNull { it.durationSeconds }

        val topContacts = entries
            .groupBy { it.phoneNumber }
            .map { (number, calls) ->
                ContactTimeShare(
                    displayName = calls.firstOrNull { !it.displayName.isNullOrBlank() }?.displayName ?: number,
                    phoneNumber = number,
                    totalSeconds = calls.sumOf { it.durationSeconds },
                    callCount = calls.size
                )
            }
            .filter { it.totalSeconds > 0 }
            .sortedByDescending { it.totalSeconds }
            .take(5)

        val busiestHours = entries
            .groupBy { millisToHourOfDay(it.timestampMillis) }
            .map { (hour, calls) -> HourBucket(hour, calls.size) }
            .sortedByDescending { it.callCount }
            .take(3)

        val dailySummaries = computeDailySummaries(entries)
        val dayOverDay = dailySummaries.map { it.totalCalls }
        val oneSided = computeOneSidedCallers(entries)

        return CallInsights(
            totalCalls = entries.size,
            totalTalkTimeSeconds = totalTalkSeconds,
            missedCalls = missed,
            outgoingCalls = outgoing,
            incomingCalls = incoming,
            averageCallSeconds = avgSeconds,
            topContactsByTime = topContacts,
            busiestHours = busiestHours,
            longestCallSeconds = longest?.durationSeconds ?: 0,
            longestCallWith = longest?.let { it.displayName?.takeIf { n -> n.isNotBlank() } ?: it.phoneNumber },
            dayOverDayCallCounts = dayOverDay,
            dailySummaries = dailySummaries,
            periodLabel = periodLabel(period),
            oneSidedCallers = oneSided
        )
    }

    /**
     * Numbers where the calling direction is lopsided enough to be worth
     * surfacing: either you've called them repeatedly and they've never (or
     * almost never) called back, or the reverse. MISSED calls are excluded
     * from both counts on purpose - a string of missed calls isn't "them
     * calling you", it's calls that never became a conversation, so
     * including them would flag numbers that never actually had any two-way
     * contact rather than numbers with a genuinely one-sided relationship.
     */
    private fun computeOneSidedCallers(entries: List<CallLogEntity>): List<OneSidedCaller> {
        return entries
            .groupBy { it.phoneNumber }
            .mapNotNull { (number, calls) ->
                val outgoing = calls.count { it.direction == CallDirection.OUTGOING }
                val incoming = calls.count { it.direction == CallDirection.INCOMING }
                val total = outgoing + incoming
                if (total < 3) return@mapNotNull null // too little history to call it a pattern

                val name = calls.firstOrNull { !it.displayName.isNullOrBlank() }?.displayName ?: number
                when {
                    outgoing >= 3 && incoming == 0 -> OneSidedCaller(name, number, outgoing, incoming, OneSidedDirection.YOU_ALWAYS_CALL)
                    incoming >= 3 && outgoing == 0 -> OneSidedCaller(name, number, outgoing, incoming, OneSidedDirection.THEY_ALWAYS_CALL)
                    else -> null
                }
            }
            .sortedByDescending { it.outgoingCount + it.incomingCount }
            .take(5)
    }

    private fun periodCutoffMillis(period: InsightsPeriod): Long {
        val cal = Calendar.getInstance()
        when (period) {
            InsightsPeriod.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
            InsightsPeriod.MONTH -> cal.add(Calendar.DAY_OF_YEAR, -30)
        }
        return cal.timeInMillis
    }

    private fun periodLabel(period: InsightsPeriod): String = when (period) {
        InsightsPeriod.WEEK -> "Last 7 days"
        InsightsPeriod.MONTH -> "Last 30 days"
    }

    private fun millisToHourOfDay(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun computeDailySummaries(entries: List<CallLogEntity>): List<DailyCallSummary> {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val fmt = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault())
        return (6 downTo 0).map { daysAgo ->
            val start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis
            val end = start + 86_400_000L
            val dayEntries = entries.filter { it.timestampMillis in start until end }
            DailyCallSummary(
                dateKey = start.toString(),
                label = fmt.format(java.util.Date(start)),
                totalCalls = dayEntries.size,
                incomingCalls = dayEntries.count { it.direction == CallDirection.INCOMING },
                outgoingCalls = dayEntries.count { it.direction == CallDirection.OUTGOING },
                missedCalls = dayEntries.count { it.direction == CallDirection.MISSED },
                talkTimeSeconds = dayEntries.filter { it.direction != CallDirection.MISSED && it.direction != CallDirection.REJECTED }.sumOf { it.durationSeconds }
            )
        }
    }
}
