package com.pulse.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

public object PulseSerialisationUtils {
    public val jsonConfigForSerialisation: Json = createJsonConfig(isStrict = PulseOtelUtils.isDebug())

    public fun createJsonConfig(isStrict: Boolean): Json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = !isStrict
            prettyPrint = isStrict
            isLenient = !isStrict
            allowSpecialFloatingPointValues = true
            useAlternativeNames = true
        }
}

public open class PulseFallbackToUnknownEnumSerializer<T : Enum<T>>(
    enumClass: KClass<T>,
    private val serialName: String = "${PulseFallbackToUnknownEnumSerializer::class.java.name}.${enumClass.java.name}",
    private val unknownKeyName: String = UNKNOWN_KEY_NAME,
) : KSerializer<T> {
    private val enumValues: List<T> =
        enumClass.java.enumConstants
            ?.toList()
            .orEmpty()
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(serialName) {
            enumValues.forEach { enumConstant ->
                element(
                    elementName =
                        enumClass
                            .java
                            .getField(enumConstant.name)
                            .getAnnotation(SerialName::class.java)
                            ?.value
                            ?: enumConstant.name,
                    descriptor =
                        PrimitiveSerialDescriptor(
                            serialName = "$serialName.${enumConstant.name}",
                            kind = PrimitiveKind.STRING,
                        ),
                )
            }
        }

    private val unknownIndex: Int =
        run {
            val matches =
                (0 until descriptor.elementsCount)
                    .filter { descriptor.getElementName(it) == unknownKeyName }

            when (matches.size) {
                0 -> error("Enum $serialName has no '$unknownKeyName' entry")
                1 -> matches[0]
                else -> error("Enum $serialName has multiple '$unknownKeyName' entries")
            }
        }

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        encoder.encodeString(descriptor.getElementName(value.ordinal))
    }

    override fun deserialize(decoder: Decoder): T {
        val input = decoder.decodeString()

        val index =
            (0 until descriptor.elementsCount)
                .firstOrNull { descriptor.getElementName(it) == input }
                ?: unknownIndex
        return enumValues[index]
    }

    public companion object {
        public const val UNKNOWN_KEY_NAME: String = "UNKNOWN"
    }
}
