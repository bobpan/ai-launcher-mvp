package com.bobpan.ailauncher.data.db

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room converter for `List<String>` serialized as JSON.
 * Registered via @ProvidedTypeConverter so Hilt can inject [json].
 */
@ProvidedTypeConverter
class Converters(private val json: Json) {
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun tagsToJson(list: List<String>): String = json.encodeToString(serializer, list)

    @TypeConverter
    fun jsonToTags(raw: String): List<String> = json.decodeFromString(serializer, raw)
}
