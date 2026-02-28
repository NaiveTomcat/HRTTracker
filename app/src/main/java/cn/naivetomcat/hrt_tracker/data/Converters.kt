package cn.naivetomcat.hrt_tracker.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Room 类型转换器
 */
class Converters {
    @TypeConverter
    fun fromUUID(uuid: UUID): String {
        return uuid.toString()
    }

    @TypeConverter
    fun toUUID(uuid: String): UUID {
        return UUID.fromString(uuid)
    }

    @TypeConverter
    fun fromMap(map: Map<String, Double>): String {
        return Json.encodeToString(map)
    }

    @TypeConverter
    fun toMap(value: String): Map<String, Double> {
        return if (value.isEmpty()) {
            emptyMap()
        } else {
            Json.decodeFromString(value)
        }
    }
}
