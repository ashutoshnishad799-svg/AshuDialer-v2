package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A phone number the person has chosen to protect: placing an outgoing call
 * to it (from anywhere in the app - the dialer, Recents, a contact card)
 * requires successfully entering the Private Space password first. This is
 * the number-level PIN-lock feature - distinct from Private Space's call
 * history view, which simply *filters* the main call log (CallLogEntity)
 * down to calls involving these same numbers rather than duplicating call
 * records into a second table.
 *
 * displayLabel is an optional person-given name for the entry (e.g. "Mom's
 * old number") shown in the locked-numbers management list, independent of
 * whatever the number's actual saved contact name is - someone locking a
 * number may not want it displayed with an identifying contact name in a
 * list that's meant to be discreet.
 */
@Entity(tableName = "locked_numbers")
data class LockedNumberEntity(
    @PrimaryKey val phoneNumber: String,
    val displayLabel: String = "",
    val addedAtMillis: Long = System.currentTimeMillis()
)
