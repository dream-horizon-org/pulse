@file:Suppress("RedundantVisibilityModifier", "unused")

package com.pulse.utils

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData
import io.opentelemetry.sdk.testing.logs.TestLogRecordData
import io.opentelemetry.sdk.testing.metrics.TestMetricData
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData

public fun createSpanData(
    name: String = "test-span",
    attributes: Map<String, Any?> = emptyMap(),
): SpanData =
    TestSpanData
        .builder()
        .setName(name)
        .setKind(SpanKind.INTERNAL)
        .setStatus(StatusData.unset())
        .setHasEnded(true)
        .setStartEpochNanos(0)
        .setEndEpochNanos(123)
        .setAttributes(attributes.toAttributes())
        .build()

public fun createLogRecordData(
    body: String,
    attributes: Map<String, Any?> = emptyMap(),
    eventName: String? = null,
): LogRecordData {
    val attrs = attributes.toAttributes()
    val builder =
        TestLogRecordData
            .builder()
            .setBody(body)
            .setAttributes(attrs)
            .setTotalAttributeCount(attrs.size())
    if (eventName != null) {
        builder.setEventName(eventName)
    }
    return builder.build()
}

public fun createMetricData(
    name: String,
    attributes: Map<String, Any?> = emptyMap(),
): MetricData =
    TestMetricData
        .builder()
        .setName(name)
        .setDescription("")
        .setUnit("")
        .setLongSumData(
            ImmutableSumData.create(
                true,
                AggregationTemporality.CUMULATIVE,
                listOf(ImmutableLongPointData.create(0, 123, attributes.toAttributes(), 1L)),
            ),
        ).build()
