package com.pulse.android.sdk.replay.internal.scheduling

import android.os.Handler
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Throttles execution: runs at most once per [throttleDelayMs] per window.
 * If called again before the delay has passed, schedules the run for the remaining time on [handler].
 */
internal class Throttler(
    private val handler: Handler,
    private val clock: Clock,
    throttleDelayMs: Long,
) {
    @Volatile private var lastCall = 0L
    private val delayNs = TimeUnit.MILLISECONDS.toNanos(throttleDelayMs)
    private val isThrottling = AtomicBoolean(false)

    fun throttle(runnable: Runnable) {
        val currentTime = clock.nanoTime()
        val timeSinceLastExecution = currentTime - lastCall

        if (timeSinceLastExecution >= delayNs) {
            if (!isThrottling.getAndSet(true)) {
                executeAndReleaseThrottle(runnable)
            }
        } else {
            if (!isThrottling.getAndSet(true)) {
                val remainingDelayMs = TimeUnit.NANOSECONDS.toMillis(delayNs - timeSinceLastExecution)
                handler.postDelayed(
                    { executeAndReleaseThrottle(runnable) },
                    remainingDelayMs.coerceAtLeast(1L),
                )
            }
        }
    }

    private fun executeAndReleaseThrottle(runnable: Runnable) {
        try {
            lastCall = clock.nanoTime()
            runnable.run()
        } finally {
            isThrottling.set(false)
        }
    }
}
