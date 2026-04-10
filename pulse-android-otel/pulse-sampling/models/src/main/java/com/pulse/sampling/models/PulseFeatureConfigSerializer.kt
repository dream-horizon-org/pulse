@file:Suppress("ForbiddenImport") // utils around serialisation

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
    private const val KEY_FEATURE_NAME = "featureName"
    private const val KEY_SESSION_SAMPLE_RATE = "sessionSampleRate"
    private const val KEY_SDKS = "sdks"
    private const val KEY_CONFIG = "config"

    private val featureNameSerializer = serializer<PulseFeatureName>()
    private val sdkNameSerializer = serializer<PulseSdkName>()

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PulseFeatureConfig") {
            element<String>(KEY_FEATURE_NAME)
            element<Float>(KEY_SESSION_SAMPLE_RATE)
            element<List<PulseSdkName>>(KEY_SDKS)
            element<JsonElement?>(KEY_CONFIG)
        }

    override fun deserialize(decoder: Decoder): PulseFeatureConfig {
        val jsonDecoder = decoder as? JsonDecoder ?: error("PulseFeatureConfig requires Json format")
        val element = jsonDecoder.decodeJsonElement()
        val obj = element as? JsonObject ?: error("Expected JsonObject for PulseFeatureConfig")

        val json = jsonDecoder.json
        val featureName = json.decodeFromJsonElement(featureNameSerializer, obj[KEY_FEATURE_NAME] ?: error("Missing featureName"))
        val sessionSampleRate = obj[KEY_SESSION_SAMPLE_RATE]?.let { json.decodeFromJsonElement(serializer<Float>(), it) } ?: 1.0f
        val sdks = obj[KEY_SDKS]?.let { json.decodeFromJsonElement(ListSerializer(sdkNameSerializer), it) }.orEmpty()

        val configData =
            obj[KEY_CONFIG]?.let { configElement ->
                when (featureName) {
                    PulseFeatureName.SESSION_REPLAY -> {
                        runCatching {
                            json.decodeFromJsonElement(PulseFeatureConfigData.SessionReplay.serializer(), configElement)
                        }.getOrNull() ?: PulseFeatureConfigData.Unknown
                    }
                    PulseFeatureName.CLICK -> {
                        runCatching {
                            json.decodeFromJsonElement(PulseFeatureConfigData.ClickInstrumentation.serializer(), configElement)
                        }.getOrNull() ?: PulseFeatureConfigData.Unknown
                    }
                    PulseFeatureName.JAVA_CRASH,
                    PulseFeatureName.JS_CRASH,
                    PulseFeatureName.CPP_CRASH,
                    PulseFeatureName.JAVA_ANR,
                    PulseFeatureName.CPP_ANR,
                    PulseFeatureName.INTERACTION,
                    PulseFeatureName.NETWORK_CHANGE,
                    PulseFeatureName.NETWORK_INSTRUMENTATION,
                    PulseFeatureName.SCREEN_SESSION,
                    PulseFeatureName.CUSTOM_EVENTS,
                    PulseFeatureName.RN_SCREEN_LOAD,
                    PulseFeatureName.RN_SCREEN_INTERACTIVE,
                    PulseFeatureName.UNKNOWN,
                    -> {
                        PulseFeatureConfigData.Unknown
                    }
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
                    is PulseFeatureConfigData.SessionReplay -> {
                        json.encodeToJsonElement(PulseFeatureConfigData.SessionReplay.serializer(), config)
                    }
                    is PulseFeatureConfigData.ClickInstrumentation -> {
                        json.encodeToJsonElement(PulseFeatureConfigData.ClickInstrumentation.serializer(), config)
                    }
                    is PulseFeatureConfigData.Unknown -> {
                        null
                    }
                }
            }
        val obj =
            buildMap<String, JsonElement> {
                put(KEY_FEATURE_NAME, JsonPrimitive(featureNameSerializer.descriptor.getElementName(value.featureName.ordinal)))
                put(KEY_SESSION_SAMPLE_RATE, JsonPrimitive(value.sessionSampleRate))
                put(KEY_SDKS, JsonArray(value.sdks.map { JsonPrimitive(sdkNameSerializer.descriptor.getElementName(it.ordinal)) }))
                configElement?.let { put(KEY_CONFIG, it) }
            }
        jsonEncoder.encodeJsonElement(JsonObject(obj))
    }
}
