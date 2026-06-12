package com.pulse.android.api.otel

import com.pulse.android.api.otel.models.PulseLogRecordData
import com.pulse.android.api.otel.models.PulseMetricData
import com.pulse.android.api.otel.models.PulseSignalData
import com.pulse.android.api.otel.models.PulseSpanData

/**
 * Callbacks invoked before telemetry signals are sent.
 * Override the methods you need.
 *
 * Callback will be dispatched in order: [beforeSend] (generic) → `beforeSend{Signal}` (specific method [beforeSendSpan], [beforeSendLog] & [beforeSendMetric]).
 * Use specific `beforeSend{Signal}` to get more type safe way to update the signal.
 * Return updated value from the callback to modify the data or
 * return null from any callback to drop the signal.
 */
public open class PulseBeforeSendData {
    /**
     * Generic callback for all signal types. Called before specific signals callbacks.
     * Return updated value from the callback to modify the data or
     * return null from any callback to drop the signal.
     * Changing the type of [data] from one signal to another will drop the data
     */
    public open fun beforeSend(data: PulseSignalData): PulseSignalData? = data

    /**
     * Called for span data after [beforeSend] returns non-null.
     */
    public open fun beforeSendSpan(data: PulseSpanData): PulseSpanData? = data

    /**
     * Called for log data after [beforeSend] returns non-null.
     */
    public open fun beforeSendLog(data: PulseLogRecordData): PulseLogRecordData? = data

    /**
     * Called for metric data after [beforeSend] returns non-null.
     */
    public open fun beforeSendMetric(data: PulseMetricData): PulseMetricData? = data
}
