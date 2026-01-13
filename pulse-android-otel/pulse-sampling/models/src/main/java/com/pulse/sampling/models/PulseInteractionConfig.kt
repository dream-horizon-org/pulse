package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseInteractionConfig internal constructor(
    @SerialName("collectorUrl")
    public val collectorUrl: String,
    @SerialName("configUrl")
    public val configUrl: String,
    @SerialName("beforeInitQueueSize")
    public val beforeInitQueueSize: Int,
)
