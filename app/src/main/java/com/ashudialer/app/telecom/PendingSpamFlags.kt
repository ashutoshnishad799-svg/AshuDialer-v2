/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.telecom

import java.util.concurrent.ConcurrentHashMap


object PendingSpamFlags {
    private val flags = ConcurrentHashMap<String, SpamAssessment>()

    fun mark(number: String, assessment: SpamAssessment) {
        flags[number] = assessment
    }


    fun consume(number: String): SpamAssessment? = flags.remove(number)
}
