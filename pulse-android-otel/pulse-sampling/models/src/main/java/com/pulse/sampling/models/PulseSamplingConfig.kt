package com.pulse.sampling.models

import android.content.Context
import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public typealias SamplingRate = Float

@Keep
@Serializable
public class PulseSamplingConfig internal constructor(
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
public class PulseSessionSamplingRule internal constructor(
    @SerialName("name")
    public val name: PulseDeviceAttributeName,
    @SerialName("value")
    public val value: String,
    @SerialName("sdks")
    public val sdks: Set<PulseSdkName>,
    @SerialName("sessionSampleRate")
    public val sessionSampleRate: SamplingRate,
) {
    public fun matches(context: Context): Boolean = name.matches(context, value)
}

@Keep
@Serializable
public class PulseDefaultSamplingConfig internal constructor(
    @SerialName("sessionSampleRate")
    public val sessionSampleRate: SamplingRate,
)

@Keep
@Serializable
public class PulseCriticalEventPolicies internal constructor(
    @SerialName("alwaysSend")
    public val alwaysSend: List<PulseCriticalEventPolicy>,
)

@Keep
@Serializable
public class PulseCriticalEventPolicy internal constructor(
    @SerialName("name")
    public val name: String,
    @SerialName("props")
    public val props: Set<PulseProp>,
    @SerialName("scopes")
    public val scopes: Set<PulseSignalScope>,
    @SerialName("sdks")
    public val sdks: Set<PulseSdkName>,
)
