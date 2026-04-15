package com.pulse.android.sdk.internal

import com.pulse.sampling.core.exporters.PulseSamplingSignalProcessors
import com.pulse.sampling.models.PulseFeatureName
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.interaction.library.InteractionInstrumentation

/**
 * Applies feature flags from [PulseSamplingSignalProcessors] to [OtelRumConfig].
 * **fully** apply the remote decision: features listed by [PulseSamplingSignalProcessors.getEnabledFeatures]
 * call [OtelRumConfig.allowInstrumentation] / enable network attributes / enable custom events;
 * features not listed call the corresponding suppress / disable paths. That way remote config
 */
internal object PulseFeatureFlagUtils {
    /**
     * Holds the result of applying feature flags.
     *
     * @property isCustomEventEnabled whether the CUSTOM_EVENTS feature remains enabled
     */
    internal data class FeatureFlagResult(
        val isCustomEventEnabled: Boolean,
    )

    internal fun apply(
        config: OtelRumConfig,
        samplingProcessors: PulseSamplingSignalProcessors,
    ): FeatureFlagResult {
        val enabledFeatures = samplingProcessors.getEnabledFeatures().toSet()
        enumValues<PulseFeatureName>().forEach { feature ->
            val on = feature in enabledFeatures
            when (feature) {
                PulseFeatureName.JAVA_CRASH -> {
                    if (on) {
                        config.allowInstrumentation("crash")
                    } else {
                        config.suppressInstrumentation("crash")
                    }
                }
                PulseFeatureName.JAVA_ANR -> {
                    if (on) {
                        config.allowInstrumentation("anr")
                    } else {
                        config.suppressInstrumentation("anr")
                    }
                }
                PulseFeatureName.NETWORK_CHANGE -> {
                    if (on) {
                        config.enableNetworkAttributes()
                    } else {
                        config.disableNetworkAttributes()
                    }
                }
                PulseFeatureName.INTERACTION -> {
                    if (on) {
                        config.allowInstrumentation(InteractionInstrumentation.INSTRUMENTATION_NAME)
                    } else {
                        config.suppressInstrumentation(InteractionInstrumentation.INSTRUMENTATION_NAME)
                    }
                }
                PulseFeatureName.CLICK -> {
                    if (on) {
                        config.allowInstrumentation("view.click")
                        config.allowInstrumentation("compose.click")
                    } else {
                        config.suppressInstrumentation("view.click")
                        config.suppressInstrumentation("compose.click")
                    }
                }
                PulseFeatureName.ANDROID_ACTIVITY -> {
                    if (on) {
                        config.allowInstrumentation("activity")
                    } else {
                        config.suppressInstrumentation("activity")
                    }
                }
                PulseFeatureName.ANDROID_FRAGMENT -> {
                    if (on) {
                        config.allowInstrumentation("fragment")
                    } else {
                        config.suppressInstrumentation("fragment")
                    }
                }
                PulseFeatureName.ANDROID_SLOWRENDERING -> {
                    if (on) {
                        config.allowInstrumentation("slowrendering")
                    } else {
                        config.suppressInstrumentation("slowrendering")
                    }
                }
                PulseFeatureName.CUSTOM_EVENTS,
                PulseFeatureName.JS_CRASH,
                PulseFeatureName.CPP_CRASH,
                PulseFeatureName.CPP_ANR,
                PulseFeatureName.RN_SCREEN_LOAD,
                PulseFeatureName.RN_SCREEN_INTERACTIVE,
                PulseFeatureName.RN_SCREEN_SESSION,
                PulseFeatureName.SESSION_REPLAY,
                PulseFeatureName.IOS_CRASH,
                PulseFeatureName.IOS_NETWORK,
                PulseFeatureName.RN_NETWORK,
                PulseFeatureName.IOS_LIFECYCLE,
                PulseFeatureName.UNKNOWN,
                -> Unit
            }
        }
        val isCustomEventEnabled = PulseFeatureName.CUSTOM_EVENTS in enabledFeatures
        return FeatureFlagResult(isCustomEventEnabled = isCustomEventEnabled)
    }
}
