package com.ashudialer.app.telecom

import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.CallLogEntity

data class SpamAssessment(
    val isLikelySpam: Boolean,
    val confidence: Int,
    val reasons: List<String>
)


object SpamDetector {

    private const val SPAM_THRESHOLD = 55


    // Only 140 is TRAI's actual telemarketing/promotional-call prefix.
    // 1600 (transactional - bank OTPs etc.) and 1800 (toll-free customer
    // care) are legitimate ranges and used to be listed here too, which
    // meant real banks/businesses calling from those ranges could get
    // scored as "Commercial/telemarketing" and mislabeled spam.
    private val KNOWN_SPAM_PREFIXES = listOf("140")


    fun scoreNumber(number: String, historyForNumber: List<CallLogEntity>): SpamAssessment {
        val digits = number.filter { it.isDigit() }
        var score = 0
        val reasons = mutableListOf<String>()

        if (KNOWN_SPAM_PREFIXES.any { digits.startsWith(it) }) {
            score += 40
            reasons += "Commercial/telemarketing number range"
        }

        if (digits.length in 5..7) {
            score += 20
            reasons += "Unusually short number"
        }

        if (historyForNumber.isNotEmpty()) {
            val rejectedCount = historyForNumber.count { it.direction == CallDirection.REJECTED }
            val veryShortCount = historyForNumber.count {
                it.direction == CallDirection.INCOMING && it.durationSeconds in 1..3
            }
            val missedCount = historyForNumber.count { it.direction == CallDirection.MISSED }

            if (rejectedCount >= 2) {
                score += 30
                reasons += "You've rejected this number $rejectedCount times before"
            }
            if (veryShortCount >= 3) {
                score += 25
                reasons += "Repeated very short calls"
            }
            if (missedCount >= 5 && historyForNumber.none { it.durationSeconds > 10 }) {
                score += 15
                reasons += "Calls repeatedly, never a real conversation"
            }
        }

        val confidence = score.coerceIn(0, 100)
        return SpamAssessment(
            isLikelySpam = confidence >= SPAM_THRESHOLD,
            confidence = confidence,
            reasons = reasons
        )
    }
}
