package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Private Space's lock configuration - a single row (id is always 1), same
 * single-row pattern as QuietHoursEntity. Holds only salts and derived
 * hashes (see util/SecureHash.kt), never the password or backup code
 * themselves in plaintext.
 *
 * isSetUp distinguishes "never configured" from "configured" so the app
 * knows whether tapping into Private Space should show the first-time setup
 * flow (choose a password, get shown a backup code once) or the unlock
 * screen.
 */
@Entity(tableName = "private_space_config")
data class PrivateSpaceEntity(
    @PrimaryKey val id: Int = 1,
    val isSetUp: Boolean = false,
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val backupCodeSalt: String = "",
    val backupCodeHash: String = ""
)
