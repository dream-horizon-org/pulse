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
     * Registration names of supported instrumentations
     */
    private enum class InstrumentationRegistrationName(
        val registrationName: String,
    ) {
        CRASH("crash"),
        ANR("anr"),
        ACTIVITY("activity"),
        FRAGMENT("fragment"),
        SLOW_RENDERING("slowrendering"),
        VIEW_CLICK("view.click"),
        COMPOSE_CLICK("compose.click"),
    }

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
                configureInstrumentation(
                    config,
                    isEnabled,
                    InstrumentationRegistrationName.CRASH.registrationName,
                )
            }
            PulseFeatureName.JAVA_ANR -> {
                configureInstrumentation(
                    config,
                    isEnabled,
                    InstrumentationRegistrationName.ANR.registrationName,
                )
            }
            PulseFeatureName.NETWORK_CHANGE -> {
                setNetworkAttributes(config, isEnabled)
            }
            PulseFeatureName.INTERACTION -> {
                configureInstrumentation(
                    config,
                    isEnabled,
                    InteractionInstrumentation.INSTRUMENTATION_NAME,
                )
            }
            PulseFeatureName.CLICK -> {
                configureInstrumentation(
                    config,
                    isEnabled,
                    InstrumentationRegistrationName.VIEW_CLICK.registrationName,
                )
                configureInstrumentation(
                    config,
                    isEnabled,
                    InstrumentationRegistrationName.COMPOSE_CLICK.registrationName,
                )
            }
            PulseFeatureName.ANDROID_ACTIVITY -> {
                configureInstrumentation(
                    config,
                    isEnabled,
                    InstrumentationRegistrationName.ACTIVITY.registrationName,
                )
            }
            PulseFeatureName.ANDROID_FRAGMENT -> {
                configureInstrumentation(
                    config,
                    isEnabled,
                    InstrumentationRegistrationName.FRAGMENT.registrationName,
                )
            }
            PulseFeatureName.ANDROID_SLOWRENDERING -> {
                configureInstrumentation(
                    config,
                    isEnabled,
                    InstrumentationRegistrationName.SLOW_RENDERING.registrationName,
                )
            }
            PulseFeatureName.CUSTOM_EVENTS,
            PulseFeatureName.JS_CRASH,
            PulseFeatureName.CPP_CRASH,
            PulseFeatureName.CPP_ANR,
            PulseFeatureName.RN_SCREEN_LOAD,
            PulseFeatureName.RN_SCREEN_INTERACTIVE,
            PulseFeatureName.RN_SCREEN_SESSION,
            // SESSION_REPLAY is applied elsewhere (SessionReplayBootstrap / replay config), not via OtelRumConfig toggles.
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

    private fun configureInstrumentation(
        config: OtelRumConfig,
        isEnabled: Boolean,
        registrationName: String,
    ) {
        if (isEnabled) {
            config.allowInstrumentation(registrationName)
        } else {
            config.suppressInstrumentation(registrationName)
        }
    }

    private fun setNetworkAttributes(
        config: OtelRumConfig,
        isEnabled: Boolean,
    ) {
        if (isEnabled) {
            config.enableNetworkAttributes()
        } else {
            config.disableNetworkAttributes()
        }
    }
}
