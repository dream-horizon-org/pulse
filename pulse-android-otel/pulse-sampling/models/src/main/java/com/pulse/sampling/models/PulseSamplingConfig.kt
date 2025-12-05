package com.pulse.sampling.models

import kotlinx.serialization.Serializable

@Serializable
public class PulseSamplingConfig(
    public val default: PulseDefaultSamplingConfig,
    public val rules: List<PulseSamplingRule>,
)

@Serializable
public sealed class PulseSamplingRule(
    public class ValueBaseRule
)

@Serializable
public class PulseDefaultSamplingConfig(
    public val sessionSamplingRate: Float,
)
