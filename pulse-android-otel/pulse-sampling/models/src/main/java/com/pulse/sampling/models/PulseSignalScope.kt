package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.utils.PulseFallbackToUnknownEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable(with = PulseSignalScopeSerializer::class)
public enum class PulseSignalScope {
    @SerialName("logs")
    LOGS,

    @SerialName("traces")
    TRACES,

    @SerialName("metrics")
    METRICS,

    @SerialName("baggage")
    BAGGAGE,

    @SerialName(PulseFallbackToUnknownEnumSerializer.UNKNOWN_KEY_NAME)
    UNKNOWN,
    ;

    public companion object {
        public val allValuesExceptUnknown: Collection<PulseSignalScope> = values().toSet() - UNKNOWN
    }
}

private class PulseSignalScopeSerializer : PulseFallbackToUnknownEnumSerializer<PulseSignalScope>(PulseSignalScope::class)
