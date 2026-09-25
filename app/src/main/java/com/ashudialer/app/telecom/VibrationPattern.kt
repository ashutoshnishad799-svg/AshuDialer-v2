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
