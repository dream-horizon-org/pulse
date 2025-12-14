package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public enum class PulseSdkName {
    @SerialName("android_java")
    ANDROID_JAVA,

    @SerialName("android_rn")
    ANDROID_RN,

    @SerialName("ios_swift")
    IOS_SWIFT,

    @SerialName("ios_rn")
    IOS_RN,

    /**
     * Unknown SDK name which is may come in future
     */
    @SerialName("unknown")
    UNKNOWN,
    ;

    public companion object {
        public val CURRENT_SDK_NAME: PulseSdkName = ANDROID_JAVA
    }
}
