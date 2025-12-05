package com.pulse.sampling.models.matchers

import androidx.annotation.Keep
import com.pulse.sampling.models.PulseProp
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseSignalMatchCondition(
    @SerialName("name")
    public val name: String,
    @SerialName("props")
    public val props: List<PulseProp>,
    @SerialName("scopes")
    public val scopes: List<PulseSignalScope>,
    @SerialName("sdks")
    public val sdks: List<PulseSdkName>,
)
