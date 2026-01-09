package com.pulse.sampling.models

import androidx.annotation.Keep

@Keep
public enum class TelemetrySdkName(
    public val value: String,
) {
    PULSE_ANDROID_JAVA("pulse-android-java"),
    PULSE_ANDROID_RN("pulse-android-rn"),
    PULSE_IOS_SWIFT("pulse-ios-swift"),
    PULSE_IOS_RN("pulse-ios-rn"),
    OPENTELEMETRY("opentelemetry"),
    ;

    public companion object {
        public fun toPulseSdkName(telemetrySdkName: TelemetrySdkName): PulseSdkName {
            return when (telemetrySdkName) {
                PULSE_ANDROID_JAVA -> PulseSdkName.ANDROID_JAVA
                PULSE_ANDROID_RN -> PulseSdkName.ANDROID_RN
                PULSE_IOS_SWIFT -> PulseSdkName.IOS_SWIFT
                PULSE_IOS_RN -> PulseSdkName.IOS_RN
                OPENTELEMETRY -> PulseSdkName.ANDROID_JAVA // Default fallback
            }
        }

        public fun fromString(value: String?): TelemetrySdkName? {
            if (value == null) return null
            val lowerValue = value.lowercase()
            return values().firstOrNull { it.value.lowercase() == lowerValue }
        }
    }
}

