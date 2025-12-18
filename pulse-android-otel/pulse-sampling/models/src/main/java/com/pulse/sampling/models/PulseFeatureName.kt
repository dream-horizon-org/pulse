package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.otel.utils.PulseFallbackToUnknownEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable(with = PulseFeatureNameSerializer::class)
public enum class PulseFeatureName {
    @SerialName("java_crash")
    JAVA_CRASH,

    @SerialName("cpp_crash")
    CPP_CRASH,

    @SerialName("java_anr")
    JAVA_ANR,

    @SerialName("cpp_anr")
    CPP_ANR,

    @SerialName("interaction")
    INTERACTION,

    @SerialName("network_change")
    NETWORK_CHANGE,

    @SerialName(PulseFallbackToUnknownEnumSerializer.UNKNOWN_KEY_NAME)
    UNKNOWN,
}

private class PulseFeatureNameSerializer : PulseFallbackToUnknownEnumSerializer<PulseFeatureName>(PulseFeatureName::class)
