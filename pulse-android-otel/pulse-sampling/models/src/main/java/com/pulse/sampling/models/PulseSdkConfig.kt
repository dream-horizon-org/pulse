package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseSdkConfig internal constructor(
    @SerialName("version")
    public val version: Int,
    @SerialName("description")
    public val description: String,
    @SerialName("sampling")
    public val sampling: PulseSamplingConfig,
    @SerialName("signals")
    public val signals: PulseSignalConfig,
    @SerialName("interaction")
    public val interaction: PulseInteractionConfig,
    @SerialName("features")
    public val features: List<PulseFeatureConfig>,
) {
    public companion object {
        public const val CURRENT_SUPPORTED_CONFIG_VERSION: Int = 1
    }
}
