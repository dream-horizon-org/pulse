package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Keep
@Serializable
public class PulseFeatureConfig internal constructor(
    @SerialName("featureName")
    public val featureName: PulseFeatureName,
    @SerialName("sessionSampleRate")
    public val sessionSampleRate: SamplingRate,
    @SerialName("sdks")
    public val sdks: Collection<PulseSdkName>,
    @SerialName("config")
    public val config: JsonObject? = null,
)
