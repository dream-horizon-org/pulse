package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Keep
@Serializable
public class PulseFeatureConfig internal constructor(
    @SerialName("featureName")
    public val featureName: PulseFeatureName = PulseFeatureName.UNKNOWN,
    @SerialName("sessionSampleRate")
    public val sessionSampleRate: SamplingRate = 1.0f,
    @SerialName("sdks")
    public val sdks: Collection<PulseSdkName> = emptyList(),
    @SerialName("config")
    public val config: JsonObject? = null,
)
