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
    public val props: Collection<PulseProp>,
    @SerialName("scopes")
    public val scopes: Collection<PulseSignalScope>,
    @SerialName("sdks")
    public val sdks: Collection<PulseSdkName>,
) {
    public companion object {
        public val allMatchLogCondition: PulseSignalMatchCondition =
            PulseSignalMatchCondition(
                name = ".*",
                props = emptySet(),
                scopes = PulseSignalScope.allValuesExceptUnknown,
                sdks = PulseSdkName.allValuesExceptUnknown,
            )
    }
}
