package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Polymorphic base for feature-specific config.
 * Each [PulseFeatureName] can have its own config subtype.
 */
@Keep
public sealed class PulseFeatureConfigData {
    /**
     * Session replay feature config (text/image privacy, throttling, etc.).
     */
    @Keep
    @Serializable
    public class SessionReplay(
        @SerialName("featureName") public val featureName: String? = null,
        @SerialName("textAndInputPrivacy") public val textAndInputPrivacy: String? = null,
        @SerialName("imagePrivacy") public val imagePrivacy: String? = null,
        @SerialName("throttleDelayMs") public val throttleDelayMs: Long? = null,
        @SerialName("screenshotScale") public val screenshotScale: Float? = null,
        @SerialName("screenshotQuality") public val screenshotQuality: Int? = null,
        @SerialName("flushIntervalSeconds") public val flushIntervalSeconds: Int? = null,
        /** Max replay batch files per backend upload; also queue size that triggers a flush. */
        @SerialName("flushAt") public val flushAt: Int? = null,
        /** Max replay batch files retained on disk (oldest evicted when exceeded). */
        @SerialName("maxBatchSize") public val maxBatchSize: Int? = null,
        @SerialName("replayApiBaseUrl") public val replayApiBaseUrl: String? = null,
    ) : PulseFeatureConfigData()

    /**
     * Click instrumentation feature config (rage-click detection parameters and context capture).
     */
    @Keep
    @Serializable
    public class ClickInstrumentation(
        @SerialName("featureName") public val featureName: String? = null,
        @SerialName("captureContext") public val captureContext: Boolean? = null,
        @SerialName("rage") public val rage: Rage? = null,
    ) : PulseFeatureConfigData() {
        /**
         * Rage-click detection parameters. All fields are optional; absent fields fall back to
         * SDK hard-coded defaults (field-level override, not all-or-nothing).
         */
        @Keep
        @Serializable
        public class Rage(
            @SerialName("timeWindowMs") public val timeWindowMs: Long? = null,
            @SerialName("threshold") public val threshold: Int? = null,
            @SerialName("radius") public val radiusDp: Float? = null,
        )
    }

    /**
     * Placeholder for features with no config or unknown config structure.
     */
    @Keep
    @Serializable
    public object Unknown : PulseFeatureConfigData()
}
