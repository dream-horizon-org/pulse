package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public enum class PulseSignalScope {
    @SerialName("logs")
    LOGS,

    @SerialName("traces")
    TRACES,

    @SerialName("metrics")
    METRICS,

    @SerialName("baggage")
    BAGGAGE,

    @SerialName("unknown")
    UNKNOWN,
}
