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
        internal const val ANDROID_JAVA_SDK_NAME_STR = "android_java"

        public fun fromTelemetrySdkName(telemetrySdkName: String?): PulseSdkName =
            when (telemetrySdkName?.lowercase()) {
                "pulse-android-java" -> ANDROID_JAVA
                "pulse-android-rn" -> ANDROID_RN
                "pulse-ios-swift" -> IOS_SWIFT
                "pulse-ios-rn" -> IOS_RN
                else -> ANDROID_JAVA // Default fallback
            }
    }
}

private class PulseSdkNameSerializer : PulseFallbackToUnknownEnumSerializer<PulseSdkName>(PulseSdkName::class)
