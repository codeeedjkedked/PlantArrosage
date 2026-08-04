package fr.plantarrosage.core.perenual

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Sérialiseurs tolérants aux incohérences de type de l'API Perenual.
 *
 * Ce ne sont pas des précautions théoriques : selon l'enregistrement et le palier de clé, un même
 * champ arrive tantôt en booléen tantôt en `0`/`1`, tantôt en chaîne tantôt en tableau. Sans ces
 * sérialiseurs, la fiche d'une espèce sur deux échoue à se décoder.
 */

/** Accepte `true`, `false`, `0`, `1`, `"0"`, `"1"`, `"true"`, `"yes"`. */
object FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val input = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val primitive = input.decodeJsonElement() as? JsonPrimitive ?: return false

        primitive.booleanOrNull?.let { return it }
        primitive.intOrNull?.let { return it != 0 }

        return primitive.content.trim().lowercase() in setOf("1", "true", "yes", "oui")
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/** Accepte `"full_sun"` comme `["full_sun", "part_shade"]`, et tolère `null`. */
object StringOrListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder as? JsonDecoder ?: return emptyList()

        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> element
                .filterIsInstance<JsonPrimitive>()
                .map { it.content }
                .filter { it.isNotBlank() }

            is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()

            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) =
        ListSerializer(String.serializer()).serialize(encoder, value)
}

/** Accepte un nombre là où une chaîne est attendue (`hardiness.min` arrive dans les deux formes). */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> element.content
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * Le repère d'arrosage est parfois un objet `{value, unit}`, parfois la chaîne
 * « upgrade required » sur le palier gratuit. Les deux doivent se décoder sans erreur.
 */
object BenchmarkSerializer : KSerializer<PerenualBenchmarkDto> {
    override val descriptor: SerialDescriptor = PerenualBenchmarkDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): PerenualBenchmarkDto {
        val input = decoder as? JsonDecoder
            ?: return PerenualBenchmarkDto.serializer().deserialize(decoder)

        return when (val element = input.decodeJsonElement()) {
            is JsonObject -> input.json.decodeFromJsonElement(PerenualBenchmarkDto.serializer(), element)
            is JsonPrimitive -> PerenualBenchmarkDto(value = element.content, unit = null)
            else -> PerenualBenchmarkDto()
        }
    }

    override fun serialize(encoder: Encoder, value: PerenualBenchmarkDto) =
        PerenualBenchmarkDto.serializer().serialize(encoder, value)
}
