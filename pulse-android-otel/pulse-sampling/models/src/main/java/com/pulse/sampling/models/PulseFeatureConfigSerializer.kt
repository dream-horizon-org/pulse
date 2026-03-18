package com.pulse.sampling.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/**
 * Custom serializer for [PulseFeatureConfig] that deserializes the polymorphic [config]
 * based on [featureName], since the config JSON structure differs per feature.
 *
 * Serialization uses JsonPrimitive for enums (PulseFeatureName, PulseSdkName) because
 * [PulseFallbackToUnknownEnumSerializer] expects TaggedEncoder context and fails when
 * used with TreeJsonEncoder via encodeToJsonElement.
 */
internal object PulseFeatureConfigSerializer : KSerializer<PulseFeatureConfig> {
    private val featureNameSerializer = serializer<PulseFeatureName>()
    private val sdkNameSerializer = serializer<PulseSdkName>()

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PulseFeatureConfig") {
            element<String>("featureName")
            element<Float>("sessionSampleRate")
            element<List<PulseSdkName>>("sdks")
            element<JsonElement?>("config")
        }

    override fun deserialize(decoder: Decoder): PulseFeatureConfig {
        val jsonDecoder = decoder as? JsonDecoder ?: error("PulseFeatureConfig requires Json format")
        val element = jsonDecoder.decodeJsonElement()
        val obj = element as? JsonObject ?: error("Expected JsonObject for PulseFeatureConfig")

        val json = jsonDecoder.json
        val featureName = json.decodeFromJsonElement(featureNameSerializer, obj["featureName"] ?: error("Missing featureName"))
        val sessionSampleRate = obj["sessionSampleRate"]?.let { json.decodeFromJsonElement(serializer<Float>(), it) } ?: 1.0f
        val sdks = obj["sdks"]?.let { json.decodeFromJsonElement(ListSerializer(sdkNameSerializer), it) } ?: emptyList()

        val configData =
            obj["config"]?.let { configElement ->
                when (featureName) {
                    PulseFeatureName.SESSION_REPLAY ->
                        runCatching {
                            json.decodeFromJsonElement(PulseFeatureConfigData.SessionReplay.serializer(), configElement)
                        }.getOrNull() ?: PulseFeatureConfigData.Unknown
                    else -> PulseFeatureConfigData.Unknown
                }
            }

        return PulseFeatureConfig(
            featureName = featureName,
            sessionSampleRate = sessionSampleRate,
            sdks = sdks,
            config = configData,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: PulseFeatureConfig,
    ) {
        val jsonEncoder =
            encoder as? kotlinx.serialization.json.JsonEncoder
                ?: error("PulseFeatureConfig requires Json format")
        val json = jsonEncoder.json
        val configElement: JsonElement? =
            value.config?.let { config ->
                when (config) {
                    is PulseFeatureConfigData.SessionReplay ->
                        json.encodeToJsonElement(
                            PulseFeatureConfigData.SessionReplay.serializer(),
                            config,
                        )
                    is PulseFeatureConfigData.Unknown -> null
                }
            }
        val obj =
            buildMap<String, JsonElement> {
                put("featureName", JsonPrimitive(featureNameSerializer.descriptor.getElementName(value.featureName.ordinal)))
                put("sessionSampleRate", JsonPrimitive(value.sessionSampleRate))
                put("sdks", JsonArray(value.sdks.map { JsonPrimitive(sdkNameSerializer.descriptor.getElementName(it.ordinal)) }))
                configElement?.let { put("config", it) }
            }
        jsonEncoder.encodeJsonElement(JsonObject(obj))
    }
}
