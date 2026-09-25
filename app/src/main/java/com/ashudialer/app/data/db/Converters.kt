package com.ashudialer.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDirection(direction: CallDirection): String = direction.name

    @TypeConverter
    fun toDirection(value: String): CallDirection = CallDirection.valueOf(value)
}
