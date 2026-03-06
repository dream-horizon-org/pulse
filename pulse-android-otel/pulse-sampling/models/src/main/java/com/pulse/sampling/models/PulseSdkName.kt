package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.utils.PulseFallbackToUnknownEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable(with = PulseSdkNameSerializer::class)
public enum class PulseSdkName {
    @SerialName(ANDROID_JAVA_SDK_NAME_STR)
    ANDROID_JAVA,

    @SerialName(ANDROID_RN_SDK_NAME_STR)
    ANDROID_RN,

    @SerialName(IOS_SWIFT_SDK_NAME_STR)
    IOS_SWIFT,

    @SerialName(IOS_RN_SDK_NAME_STR)
    IOS_RN,

    /**
     * Unknown SDK name which is may come in future
     */
    @SerialName(PulseFallbackToUnknownEnumSerializer.UNKNOWN_KEY_NAME)
    UNKNOWN,
    ;

    public companion object {
        internal const val ANDROID_JAVA_SDK_NAME_STR = "pulse_android_java"
        internal const val ANDROID_RN_SDK_NAME_STR = "pulse_android_rn"
        internal const val IOS_SWIFT_SDK_NAME_STR = "pulse_ios_swift"
        internal const val IOS_RN_SDK_NAME_STR = "pulse_ios_rn"
        public val allValuesExceptUnknown: Collection<PulseSdkName> = PulseSdkName.values().toSet() - UNKNOWN

        public fun fromName(telemetrySdkName: String?): PulseSdkName =
            when (telemetrySdkName?.lowercase()) {
                ANDROID_JAVA_SDK_NAME_STR -> ANDROID_JAVA
                ANDROID_RN_SDK_NAME_STR -> ANDROID_RN
                IOS_SWIFT_SDK_NAME_STR -> IOS_SWIFT
                IOS_RN_SDK_NAME_STR -> IOS_RN
                else -> UNKNOWN // Default fallback
            }
    }
}

private class PulseSdkNameSerializer : PulseFallbackToUnknownEnumSerializer<PulseSdkName>(PulseSdkName::class)
