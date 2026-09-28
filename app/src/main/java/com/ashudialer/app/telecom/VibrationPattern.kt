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


enum class VibrationPattern(val displayName: String, val timings: LongArray) {
    DEFAULT("Default", longArrayOf(0, 300, 200, 300)),
    SHORT_PULSES("Short pulses", longArrayOf(0, 100, 100, 100, 100, 100, 100, 100)),
    LONG_BUZZ("Long buzz", longArrayOf(0, 900)),
    HEARTBEAT("Heartbeat", longArrayOf(0, 120, 90, 120, 340, 120, 90, 120)),
    ESCALATING("Escalating", longArrayOf(0, 100, 150, 200, 150, 300, 150, 400));

    companion object {
        fun fromId(id: String?): VibrationPattern = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
