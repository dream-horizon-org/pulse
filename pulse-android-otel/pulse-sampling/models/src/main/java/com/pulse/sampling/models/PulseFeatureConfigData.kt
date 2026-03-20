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
    public data class SessionReplay(
        @SerialName("featureName") val featureName: String? = null,
        @SerialName("textAndInputPrivacy") val textAndInputPrivacy: String? = null,
        @SerialName("imagePrivacy") val imagePrivacy: String? = null,
        @SerialName("throttleDelayMs") val throttleDelayMs: Long? = null,
        @SerialName("screenshotScale") val screenshotScale: Float? = null,
        @SerialName("screenshotQuality") val screenshotQuality: Int? = null,
        @SerialName("flushIntervalSeconds") val flushIntervalSeconds: Int? = null,
        @SerialName("flushAt") val flushAt: Int? = null,
        @SerialName("maxBatchSize") val maxBatchSize: Int? = null,
        @SerialName("replayApiBaseUrl") val replayApiBaseUrl: String? = null,
    ) : PulseFeatureConfigData()

    /**
     * Placeholder for features with no config or unknown config structure.
     */
    @Keep
    @Serializable
    public object Unknown : PulseFeatureConfigData()
}
