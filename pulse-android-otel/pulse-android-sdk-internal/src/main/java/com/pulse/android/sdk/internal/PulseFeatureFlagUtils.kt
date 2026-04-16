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
            applySingleFeature(config, feature, feature in enabledFeatures)
        }
        val isCustomEventEnabled = PulseFeatureName.CUSTOM_EVENTS in enabledFeatures
        return FeatureFlagResult(isCustomEventEnabled = isCustomEventEnabled)
    }

    private fun applySingleFeature(
        config: OtelRumConfig,
        feature: PulseFeatureName,
        isEnabled: Boolean,
    ) {
        when (feature) {
            PulseFeatureName.JAVA_CRASH -> {
                setCrashInstrumentation(config, isEnabled)
            }
            PulseFeatureName.JAVA_ANR -> {
                setAnrInstrumentation(config, isEnabled)
            }
            PulseFeatureName.NETWORK_CHANGE -> {
                setNetworkAttributes(config, isEnabled)
            }
            PulseFeatureName.INTERACTION -> {
                setInteractionInstrumentation(config, isEnabled)
            }
            PulseFeatureName.CLICK -> {
                setClickInstrumentation(config, isEnabled)
            }
            PulseFeatureName.ANDROID_ACTIVITY -> {
                setActivityInstrumentation(config, isEnabled)
            }
            PulseFeatureName.ANDROID_FRAGMENT -> {
                setFragmentInstrumentation(config, isEnabled)
            }
            PulseFeatureName.ANDROID_SLOWRENDERING -> {
                setSlowRenderingInstrumentation(config, isEnabled)
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
            -> {
                // Not driven by OtelRumConfig in this path.
            }
        }
    }

    private fun setCrashInstrumentation(config: OtelRumConfig, isEnabled: Boolean) {
        if (isEnabled) {
            config.allowInstrumentation("crash")
        } else {
            config.suppressInstrumentation("crash")
        }
    }

    private fun setAnrInstrumentation(config: OtelRumConfig, isEnabled: Boolean) {
        if (isEnabled) {
            config.allowInstrumentation("anr")
        } else {
            config.suppressInstrumentation("anr")
        }
    }

    private fun setNetworkAttributes(config: OtelRumConfig, isEnabled: Boolean) {
        if (isEnabled) {
            config.enableNetworkAttributes()
        } else {
            config.disableNetworkAttributes()
        }
    }

    private fun setInteractionInstrumentation(config: OtelRumConfig, isEnabled: Boolean) {
        val name = InteractionInstrumentation.INSTRUMENTATION_NAME
        if (isEnabled) {
            config.allowInstrumentation(name)
        } else {
            config.suppressInstrumentation(name)
        }
    }

    private fun setClickInstrumentation(config: OtelRumConfig, isEnabled: Boolean) {
        if (isEnabled) {
            config.allowInstrumentation("view.click")
            config.allowInstrumentation("compose.click")
        } else {
            config.suppressInstrumentation("view.click")
            config.suppressInstrumentation("compose.click")
        }
    }

    private fun setActivityInstrumentation(config: OtelRumConfig, isEnabled: Boolean) {
        if (isEnabled) {
            config.allowInstrumentation("activity")
        } else {
            config.suppressInstrumentation("activity")
        }
    }

    private fun setFragmentInstrumentation(config: OtelRumConfig, isEnabled: Boolean) {
        if (isEnabled) {
            config.allowInstrumentation("fragment")
        } else {
            config.suppressInstrumentation("fragment")
        }
    }

    private fun setSlowRenderingInstrumentation(config: OtelRumConfig, isEnabled: Boolean) {
        if (isEnabled) {
            config.allowInstrumentation("slowrendering")
        } else {
            config.suppressInstrumentation("slowrendering")
        }
    }
}
