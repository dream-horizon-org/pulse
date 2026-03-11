package com.pulse.android.sdk.internal

import com.pulse.sampling.core.exporters.PulseSamplingSignalProcessors
import com.pulse.sampling.models.PulseFeatureName
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.interaction.library.InteractionInstrumentation

/**
 * Applies feature flags from [PulseSamplingSignalProcessors] to [OtelRumConfig].
 *
 * Extracted here so the "which features are disabled and what suppression they trigger" logic
 * can be unit-tested without Android or OpenTelemetryRum.
 */
internal object PulseFeatureFlagUtils {
    /**
     * Holds the result of applying feature flags.
     *
     * @param isCustomEventEnabled whether the CUSTOM_EVENTS feature remains enabled
     */
    internal data class FeatureFlagResult(
        val isCustomEventEnabled: Boolean,
    )

    internal fun apply(
        config: OtelRumConfig,
        samplingProcessors: PulseSamplingSignalProcessors,
    ): FeatureFlagResult {
        var isCustomEventEnabled = true
        val enabledFeatures = samplingProcessors.getEnabledFeatures()
        enumValues<PulseFeatureName>().forEach { feature ->
            if (feature !in enabledFeatures) {
                when (feature) {
                    PulseFeatureName.JAVA_CRASH -> config.suppressInstrumentation("crash")
                    PulseFeatureName.JAVA_ANR -> config.suppressInstrumentation("anr")
                    PulseFeatureName.NETWORK_CHANGE -> config.disableNetworkAttributes()
                    PulseFeatureName.INTERACTION -> config.suppressInstrumentation(InteractionInstrumentation.INSTRUMENTATION_NAME)
                    PulseFeatureName.CUSTOM_EVENTS -> isCustomEventEnabled = false
                    PulseFeatureName.JS_CRASH,
                    PulseFeatureName.CPP_CRASH,
                    PulseFeatureName.CPP_ANR,
                    PulseFeatureName.NETWORK_INSTRUMENTATION,
                    PulseFeatureName.SCREEN_SESSION,
                    PulseFeatureName.RN_SCREEN_LOAD,
                    PulseFeatureName.RN_SCREEN_INTERACTIVE,
                    PulseFeatureName.UNKNOWN,
                    -> Unit
                }
            }
        }
        return FeatureFlagResult(isCustomEventEnabled = isCustomEventEnabled)
    }
}
