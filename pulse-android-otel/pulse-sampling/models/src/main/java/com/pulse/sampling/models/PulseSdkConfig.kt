package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseSdkConfig internal constructor(
    @SerialName("version")
    public val version: Int = Int.MIN_VALUE,
    @SerialName("description")
    public val description: String = "",
    @SerialName("sampling")
    public val sampling: PulseSamplingConfig = PulseSamplingConfig(),
    @SerialName("signals")
    public val signals: PulseSignalConfig = PulseSignalConfig(),
    @SerialName("interaction")
    public val interaction: PulseInteractionConfig = PulseInteractionConfig(),
    @SerialName("features")
    public val features: List<PulseFeatureConfig> = emptyList(),
)
