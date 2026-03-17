package com.pulse.android.sdk.internal

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
internal class PulseSessionReplayFeatureConfig(
    @SerialName("featureName")
    val featureName: String? = null,
    @SerialName("textAndInputPrivacy")
    val textAndInputPrivacy: String? = null,
    @SerialName("imagePrivacy")
    val imagePrivacy: String? = null,
    @SerialName("throttleDelayMs")
    val throttleDelayMs: Long? = null,
    @SerialName("screenshotScale")
    val screenshotScale: Float? = null,
    @SerialName("screenshotQuality")
    val screenshotQuality: Int? = null,
    @SerialName("flushIntervalSeconds")
    val flushIntervalSeconds: Int? = null,
    @SerialName("flushAt")
    val flushAt: Int? = null,
    @SerialName("maxBatchSize")
    val maxBatchSize: Int? = null,
    @SerialName("replayApiBaseUrl")
    val replayApiBaseUrl: String? = null,
)
