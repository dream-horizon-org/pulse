package com.pulse.sampling.models

import kotlinx.serialization.Serializable

@Serializable
public class PulseSdkConfig(
    public val version: Int,
    public val sampling: PulseSamplingConfig,
    public val signals: PulseSignalConfig,
    public val interaction: PulseInteractionConfig,
    public val features: PulseFeatureConfig,
)
