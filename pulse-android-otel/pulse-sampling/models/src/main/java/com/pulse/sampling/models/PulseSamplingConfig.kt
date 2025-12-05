package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.sampling.models.matchers.PulseDeviceMatchCondition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal typealias SamplingRate = Float

@Keep
@Serializable
public class PulseSamplingConfig(
    @SerialName("default")
    public val default: PulseDefaultSamplingConfig,
    @SerialName("rules")
    public val rules: List<PulseSamplingRule>,
    @SerialName("criticalEventPolicies")
    public val criticalEventPolicies: PulseCriticalEventPolicies? = null,
)

@Keep
@Serializable
public class PulseSamplingRule(
    @SerialName("name")
    public val name: String,
    @SerialName("match")
    public val match: PulseDeviceMatchCondition,
    @SerialName("session_sample_rate")
    public val sessionSampleRate: SamplingRate,
)

@Keep
@Serializable
public class PulseDefaultSamplingConfig(
    @SerialName("session_sample_rate")
    public val sessionSampleRate: SamplingRate,
)

@Keep
@Serializable
public class PulseCriticalEventPolicies(
    @SerialName("alwaysSend")
    public val alwaysSend: List<PulseCriticalEventPolicy>
)

@Keep
@Serializable
public class PulseCriticalEventPolicy(
    @SerialName("name")
    public val name: String,
    @SerialName("props")
    public val props: List<PulseProp>,
    @SerialName("scopes")
    public val scopes: List<PulseSignalScope>
)
