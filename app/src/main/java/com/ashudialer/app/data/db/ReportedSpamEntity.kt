package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A number the person has explicitly reported as spam/scam, separate from
 * blocking. Reporting and blocking are two different intents that used to
 * be conflated into one action ("Report spam" silently just called
 * blockNumber() and nothing else) - someone might want to flag a number as
 * a known scammer for their own future reference (and to warn themselves
 * if it calls again) without necessarily wanting every future call from it
 * silently rejected, e.g. a spoofed/rotating scam number they want a
 * record of but that will never call from the same digits twice anyway.
 * The two lists are independent: a number can be reported without being
 * blocked, blocked without being reported, or both.
 */
@Entity(tableName = "reported_spam")
data class ReportedSpamEntity(
    @PrimaryKey val phoneNumber: String,
    val reportedAtMillis: Long = System.currentTimeMillis(),
    // Free-text reason captured at report time (e.g. "Fake bank call",
    // "Robocall", "Pretended to be courier") - optional, since requiring a
    // reason would add friction to what should be a one-tap action, but
    // worth keeping when the person does provide one so a later look at
    // Reported Numbers has more than just a bare number and timestamp.
    val reason: String = ""
)
