package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CallDirection { INCOMING, OUTGOING, MISSED, REJECTED }

@Entity(
    tableName = "call_log",
    indices = [Index(value = ["phoneNumber", "timestampMillis"], unique = true)]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String?,
    val direction: CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Int = 0,
    val isSpam: Boolean = false,
    val photoUri: String? = null
)
