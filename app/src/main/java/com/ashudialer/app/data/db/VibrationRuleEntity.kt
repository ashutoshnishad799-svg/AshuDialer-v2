package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "vibration_rules")
data class VibrationRuleEntity(
    @PrimaryKey val phoneNumber: String,
    val patternId: String
)
