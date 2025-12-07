package org.fossify.phone.database

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromUuid(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUuid(value: String?): UUID? = value?.let { UUID.fromString(it) }

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? = map?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? = value?.let { json.decodeFromString(it) }
}
