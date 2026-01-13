package com.pulse.otel.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

public open class PulseFallbackToUnknownEnumSerializer<T : Enum<T>>(
    enumClass: KClass<T>,
    unknownKeyName: String = UNKNOWN_KEY_NAME,
) : KSerializer<T> {
    private val values: Array<out T> =
        enumClass.java.enumConstants ?: error("com.pulse.otel.util.PulseFallbackToUnknownEnumSerializer works with enum classes")

    private val fallback: T =
        values.firstOrNull { enum ->
            enum.name == unknownKeyName
        } ?: error(
            "Enum ${enumClass.simpleName ?: "no name"} must have exactly one @FallbackEnumValue",
        )

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.pulse.otel.util.PulseFallbackToUnknownEnumSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        encoder.encodeString(value.name.lowercase())
    }

    override fun deserialize(decoder: Decoder): T {
        val decoded = decoder.decodeString()
        return values.firstOrNull { it.name == decoded.uppercase() } ?: fallback
    }

    public companion object {
        public const val UNKNOWN_KEY_NAME: String = "UNKNOWN"
    }
}
