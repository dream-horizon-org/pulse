package com.pulse.sampling.models

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class PulseSdkConfig internal constructor(
    @SerialName("version")
    public val version: Int = Int.MIN_VALUE,
    @SerialName("description")
    public val description: String = "",
    @SerialName("sampling")
    public val sampling: PulseSamplingConfig = PulseSamplingConfig(),
    @SerialName("signals")
    public val signals: PulseSignalConfig = PulseSignalConfig(),
    @SerialName("interaction")
    public val interaction: PulseInteractionConfig = PulseInteractionConfig(),
    @SerialName("features")
    public val features: Collection<PulseFeatureConfig> = emptySet(),
    @SerialName("batchConfig")
    public val batchConfig: PulseBatchProcessorConfig? = null,
)

@Keep
@Serializable
public class PulseBatchProcessorConfig(
    @SerialName("batchLogs")
    public val batchLogs: PulseBatchProcessorOption? = null,
    @SerialName("batchSpans")
    public val batchSpans: PulseBatchProcessorOption? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as PulseBatchProcessorConfig
        return batchLogs == other.batchLogs && batchSpans == other.batchSpans
    }

    override fun hashCode(): Int {
        var result = batchLogs?.hashCode() ?: 0
        result = 31 * result + (batchSpans?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "PulseBatchProcessorConfig(batchLogs=${batchLogs ?: "null"}, batchSpans=${batchSpans ?: "null"})"
}

@Keep
@Serializable
public class PulseBatchProcessorOption(
    @SerialName("maxExportBatchSize")
    public val maxExportBatchSize: Int = 512,
    @SerialName("scheduleDelay")
    public val scheduleDelay: Int = 10000, // milliseconds
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as PulseBatchProcessorOption
        return maxExportBatchSize == other.maxExportBatchSize && scheduleDelay == other.scheduleDelay
    }

    override fun hashCode(): Int {
        var result = maxExportBatchSize
        result = 31 * result + scheduleDelay
        return result
    }

    override fun toString(): String = "PulseBatchProcessorOption(maxExportBatchSize=$maxExportBatchSize, scheduleDelay=$scheduleDelay)"
}
