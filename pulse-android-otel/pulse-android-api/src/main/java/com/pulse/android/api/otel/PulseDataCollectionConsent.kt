package com.pulse.android.api.otel

import androidx.annotation.Keep

/**
 * Represents the user's consent state for telemetry data collection.
 */
@Keep
public enum class PulseDataCollectionConsent {
    /**
     * Data collection is awaiting user consent.
     *
     * Telemetry signals are buffered in memory until the state transitions to [ALLOWED] or [DENIED].
     * Each signal type is buffered up to 5000 entries; additional signals are dropped once the limit is reached.
     *
     * Calling [PENDING] after [ALLOWED] or [DENIED] has no effect.
     * Repeated calls to [PENDING] have no effect.
     */
    PENDING,

    /**
     * Data collection is permitted.
     *
     * Any buffered signals are flushed, and subsequent signals are exported normally.
     * Repeated calls to [ALLOWED] have no effect.
     */
    ALLOWED,

    /**
     * Data collection is denied.
     *
     * All buffered data is cleared and the OpenTelemetry instance is shut down.
     * Calling [PENDING] or [ALLOWED] after [DENIED] will not restart the SDK
     * within the same app instance.
     *
     * Repeated calls to [DENIED] have no effect.
     */
    DENIED,
}
