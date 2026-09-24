package org.foxgirls.audioranobe.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The backend serialises an empty map as `[]` (and sometimes `null`) while a filled one is a JSON
 * object. Kotlin's map decoder rejects `[` — this serializer treats both as an empty map.
 */
open class LenientMapSerializer<V>(valueSerializer: KSerializer<V>) : KSerializer<Map<String, V>> {
    private val inner = MapSerializer(String.serializer(), valueSerializer)
    override val descriptor: SerialDescriptor = inner.descriptor

    override fun deserialize(decoder: Decoder): Map<String, V> {
        val json = decoder as? JsonDecoder ?: return inner.deserialize(decoder)
        return when (val el = json.decodeJsonElement()) {
            is JsonObject -> json.json.decodeFromJsonElement(inner, el)
            is JsonArray, JsonNull -> emptyMap()
            else -> emptyMap()
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, V>) = inner.serialize(encoder, value)
}

object StringMap : LenientMapSerializer<String>(String.serializer())
object IntMap : LenientMapSerializer<Int>(Int.serializer())
object ChapterRowsMap : LenientMapSerializer<List<ChapterRow>>(kotlinx.serialization.builtins.ListSerializer(ChapterRow.serializer()))
