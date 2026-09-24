package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "call_notes",
    indices = [Index(value = ["phoneNumber"])]
)
data class CallNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val callerLabel: String,
    val text: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
