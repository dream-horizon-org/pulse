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
     * Placeholder for features with no config or unknown config structure.
     */
    @Keep
    @Serializable
    public object Unknown : PulseFeatureConfigData()
}
