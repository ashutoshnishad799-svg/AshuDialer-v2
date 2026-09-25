package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Smart Quiet Hours: a person-defined time window where incoming calls are
 * automatically silently declined (sent straight to voicemail, no ring, no
 * notification) unless the caller is a starred favorite - which still rings
 * through normally. Single-row table (id is always 1) since there's one
 * active schedule at a time, editable from Settings.
 */
@Entity(tableName = "quiet_hours_schedule")
data class QuietHoursEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val allowFavorites: Boolean = true,
    // If a number calls twice within this many minutes during quiet hours,
    // the second call rings through anyway - a common "it might be urgent"
    // signal real phones' native DND modes use (repeat-caller bypass).
    val allowRepeatCallerBypass: Boolean = true,
    val repeatCallerWindowMinutes: Int = 15
)
