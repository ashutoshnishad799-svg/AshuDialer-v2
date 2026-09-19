package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val phoneNumber: String,
    val reason: String = "Blocked by user",
    val addedAtMillis: Long = System.currentTimeMillis()
)
