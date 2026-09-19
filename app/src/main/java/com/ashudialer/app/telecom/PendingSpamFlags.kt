package com.ashudialer.app.telecom

import java.util.concurrent.ConcurrentHashMap


object PendingSpamFlags {
    private val flags = ConcurrentHashMap<String, SpamAssessment>()

    fun mark(number: String, assessment: SpamAssessment) {
        flags[number] = assessment
    }


    fun consume(number: String): SpamAssessment? = flags.remove(number)
}
