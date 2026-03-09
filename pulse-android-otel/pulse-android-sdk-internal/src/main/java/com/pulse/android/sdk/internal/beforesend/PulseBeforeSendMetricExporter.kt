package com.pulse.android.sdk.internal.beforesend

import com.pulse.android.api.otel.PulseBeforeSendData
import com.pulse.android.api.otel.models.PulseMetricData
import com.pulse.android.api.otel.models.copy
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector
import io.opentelemetry.sdk.metrics.export.MetricExporter

internal class PulseBeforeSendMetricExporter(
    private val beforeSendData: PulseBeforeSendData,
    private val delegate: MetricExporter,
) : MetricExporter by delegate {
    override fun getDefaultAggregation(instrumentType: InstrumentType): Aggregation? = delegate.getDefaultAggregation(instrumentType)

    override fun getMemoryMode(): MemoryMode? = delegate.memoryMode

    override fun export(metrics: Collection<MetricData>): CompletableResultCode {
        val pulseMetrics =
            metrics
                .mapNotNull { metric ->
                    val pulseMetric =
                        metric as? PulseMetricData ?: metric.copy()
                    val afterGeneric = beforeSendData.beforeSend(pulseMetric) ?: return@mapNotNull null
                    if (afterGeneric !is PulseMetricData) return@mapNotNull null
                    beforeSendData.beforeSendMetric(afterGeneric)
                }
        return if (pulseMetrics.isEmpty()) {
            CompletableResultCode.ofSuccess()
        } else {
            delegate.export(pulseMetrics)
        }
    }

    override fun close() {
        delegate.close()
    }

    override fun with(
        instrumentType: InstrumentType,
        aggregation: Aggregation,
    ): DefaultAggregationSelector? = delegate.with(instrumentType, aggregation)
}
