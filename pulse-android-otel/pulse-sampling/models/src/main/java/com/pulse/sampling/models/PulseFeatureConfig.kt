package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseFeatureConfig(
    @SerialName("featureName")
    public val featureName: String,
    @SerialName("enabled")
    public val enabled: Boolean,
    @SerialName("sessionSampleRate")
    public val sessionSampleRate: SamplingRate,
    @SerialName("sdks")
    public val sdks: Set<PulseSdkName>,
)
