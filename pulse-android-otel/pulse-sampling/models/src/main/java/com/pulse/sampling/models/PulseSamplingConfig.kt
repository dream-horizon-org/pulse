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
    /**
     * Set of rules sorted by descending priority
     */
    @SerialName("rules")
    public val rules: List<PulseSessionSamplingRule>,
    @SerialName("criticalEventPolicies")
    public val criticalEventPolicies: PulseCriticalEventPolicies? = null,
    @SerialName("criticalSessionPolicies")
    public val criticalSessionPolicies: PulseCriticalEventPolicies? = null,
)

@Keep
@Serializable
public class PulseSessionSamplingRule(
    @SerialName("name")
    public val name: String,
    @SerialName("match")
    public val match: PulseDeviceMatchCondition,
    @SerialName("sdks")
    public val sdks: Set<PulseSdkName>,
    @SerialName("sessionSampleRate")
    public val sessionSampleRate: SamplingRate,
)

@Keep
@Serializable
public class PulseDefaultSamplingConfig(
    @SerialName("sessionSampleRate")
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
    public val props: Set<PulseProp>,
    @SerialName("scopes")
    public val scopes: Set<PulseSignalScope>,
    @SerialName("sdks")
    public val sdks: Set<PulseSdkName>,
)
