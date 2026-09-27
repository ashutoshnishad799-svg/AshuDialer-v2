package com.ashudialer.app.data

import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.CallLogDao
import com.ashudialer.app.data.db.CallLogEntity

/**
 * A contact worth reaching out to: someone who had a real, two-way calling
 * relationship in the past but who neither called nor was called by the
 * person in the recent window. Distinct from CallInsightsRepository's
 * OneSidedCaller - that flags a *direction* imbalance (you always call them,
 * or vice versa) within a single period; this flags a *recency* drop-off
 * across two periods (they used to talk a lot, now they don't talk at all),
 * which needs comparing two separate windows rather than aggregating one.
 */
data class ReconnectSuggestion(
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String?,
    val pastCallCount: Int,
    val daysSinceLastCall: Int
)

/**
 * Finds people the user used to call/hear from regularly but hasn't
 * connected with recently - the seed for a "maybe call them?" nudge on
 * Recents. Pure computation over the same bounded call-log snapshot
 * CallInsightsRepository already reads (getRecentSnapshot), so this adds no
 * new permissions, no new DAO methods, and no new database queries beyond
 * one more call to a method that already exists.
 *
 * Kept as its own repository rather than folded into CallInsightsRepository:
 * insights aggregates ONE period: reconnect suggestions compare TWO
 * (a "history" window against a "recent" window), which is a different
 * enough shape to justify not overloading CallInsights' single-period data
 * class with two-period fields only this feature would ever populate.
 */
class ReconnectRepository(private val dao: CallLogDao) {

    companion object {
        // "Had a real relationship": same >=3-call bar OneSidedCaller already
        // uses (see CallInsightsRepository.computeOneSidedCallers) - reusing
        // the existing threshold rather than inventing a second number for
        // what "enough calls to count as a pattern" means in this codebase.
        private const val MIN_PAST_CALLS = 3

        // History window: far enough back that someone who talked to this
        // number even a handful of times genuinely had a standing pattern,
        // not a one-off cluster of calls about a single errand.
        private const val HISTORY_WINDOW_DAYS = 90

        // Quiet window: long enough that an ordinary short gap (a busy week,
        // a trip) doesn't false-positive into a suggestion, short enough that
        // a surfaced suggestion still feels timely rather than stale.
        private const val QUIET_WINDOW_DAYS = 21

        private const val MAX_SUGGESTIONS = 3
    }

    suspend fun computeSuggestions(): List<ReconnectSuggestion> {
        val now = System.currentTimeMillis()
        val quietCutoff = now - QUIET_WINDOW_DAYS * 86_400_000L
        val historyCutoff = now - HISTORY_WINDOW_DAYS * 86_400_000L

        // One bounded snapshot read, same limit CallInsightsRepository uses,
        // then split in-memory by timestamp - avoids a second DAO method
        // whose only difference would be a different WHERE clause on the
        // same table.
        val snapshot = dao.getRecentSnapshot(2000)
        if (snapshot.isEmpty()) return emptyList()

        val recentCalls = snapshot.filter { it.timestampMillis >= quietCutoff }
        val recentNumbers = recentCalls.map { it.phoneNumber }.toHashSet()

        val historyCalls = snapshot.filter {
            it.timestampMillis in historyCutoff until quietCutoff
        }

        return historyCalls
            .groupBy { it.phoneNumber }
            .mapNotNull { (number, calls) -> buildSuggestion(number, calls, recentNumbers, now) }
            .sortedByDescending { it.pastCallCount }
            .take(MAX_SUGGESTIONS)
    }

    private fun buildSuggestion(
        number: String,
        calls: List<CallLogEntity>,
        recentNumbers: Set<String>,
        now: Long
    ): ReconnectSuggestion? {
        // Already talked to them again within the quiet window - not gone
        // quiet, nothing to suggest.
        if (number in recentNumbers) return null

        // MISSED-only history isn't a relationship that "went quiet" - it
        // never connected in the first place. Mirrors
        // computeOneSidedCallers' same reasoning for excluding MISSED from
        // its direction counts.
        val connectedCalls = calls.filter { it.direction != CallDirection.MISSED && it.direction != CallDirection.REJECTED }
        if (connectedCalls.size < MIN_PAST_CALLS) return null

        val lastCall = calls.maxByOrNull { it.timestampMillis } ?: return null
        val daysSince = ((now - lastCall.timestampMillis) / 86_400_000L).toInt()

        return ReconnectSuggestion(
            displayName = calls.firstOrNull { !it.displayName.isNullOrBlank() }?.displayName ?: number,
            phoneNumber = number,
            photoUri = calls.firstOrNull { !it.photoUri.isNullOrBlank() }?.photoUri,
            pastCallCount = connectedCalls.size,
            daysSinceLastCall = daysSince
        )
    }
}
