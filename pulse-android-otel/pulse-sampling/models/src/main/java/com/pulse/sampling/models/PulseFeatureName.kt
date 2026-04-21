package com.pulse.sampling.models

import androidx.annotation.Keep
import com.pulse.utils.PulseFallbackToUnknownEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable(with = PulseFeatureNameSerializer::class)
public enum class PulseFeatureName {
    @SerialName("java_crash")
    JAVA_CRASH,

    @SerialName("js_crash")
    JS_CRASH,

    @SerialName("cpp_crash")
    CPP_CRASH,

    @SerialName("java_anr")
    JAVA_ANR,

    @SerialName("cpp_anr")
    CPP_ANR,

    @SerialName("interaction")
    INTERACTION,

    @SerialName("network_change")
    NETWORK_CHANGE,

    @SerialName("custom_events")
    CUSTOM_EVENTS,

    @SerialName("rn_screen_load")
    RN_SCREEN_LOAD,

    @SerialName("rn_screen_interactive")
    RN_SCREEN_INTERACTIVE,

    @SerialName("rn_screen_session")
    RN_SCREEN_SESSION,

    @SerialName("session_replay")
    SESSION_REPLAY,

    @SerialName("click")
    CLICK,

    @SerialName("ios_crash")
    IOS_CRASH,

    @SerialName("ios_network")
    IOS_NETWORK,

    @SerialName("rn_network")
    RN_NETWORK,

    @SerialName("ios_lifecycle")
    IOS_LIFECYCLE,

    @SerialName("android_activity")
    ANDROID_ACTIVITY,

    @SerialName("android_fragment")
    ANDROID_FRAGMENT,

    @SerialName("android_slowrendering")
    ANDROID_SLOWRENDERING,

    @SerialName(PulseFallbackToUnknownEnumSerializer.UNKNOWN_KEY_NAME)
    UNKNOWN,
}

private class PulseFeatureNameSerializer : PulseFallbackToUnknownEnumSerializer<PulseFeatureName>(PulseFeatureName::class)
