package com.pulse.android.api.otel.models

import io.opentelemetry.api.common.Attributes

/**
 * Common interface for Pulse telemetry data types.
 * Implemented by [PulseSpanData],
 * [PulseLogRecordData], and
 * [PulseMetricData].
 */
public sealed interface PulseSignalData {
    /** Attributes for this signal. */
    public fun getAttributes(): Attributes
}
