package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.otel.utils.PulseFallbackToUnknownEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable(with = PulseSdkNameSerializer::class)
public enum class PulseSdkName {
    @SerialName(ANDROID_JAVA_SDK_NAME_STR)
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
    @SerialName(PulseFallbackToUnknownEnumSerializer.UNKNOWN_KEY_NAME)
    UNKNOWN,
    ;

    public companion object {
        public var CURRENT_SDK_NAME: PulseSdkName = ANDROID_JAVA
        internal const val ANDROID_JAVA_SDK_NAME_STR = "android_java"
    }
}

private class PulseSdkNameSerializer : PulseFallbackToUnknownEnumSerializer<PulseSdkName>(PulseSdkName::class)
