package com.pulse.sampling.models.matchers

import androidx.annotation.Keep
import com.pulse.sampling.models.PulseSdkName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public sealed class PulseDeviceMatchCondition {
    @Serializable
    public class ValueBasedMatchCondition internal constructor(
        @SerialName("type")
        public val type: String,
        @SerialName("sdks")
        public val sdks: Set<PulseSdkName>,
        @SerialName("value")
        public val value: String,
    ) : PulseDeviceMatchCondition()

    /**
     * For unknown matching rule which is still not implemented
     */
    @Keep
    @Serializable
    public object UNKNOW : PulseDeviceMatchCondition()
}
