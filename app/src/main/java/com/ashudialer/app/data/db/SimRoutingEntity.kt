package com.ashudialer.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "sim_routing_rules")
data class SimRoutingEntity(
    @PrimaryKey val phoneNumber: String,

    val preferredSimAccountId: String
)
