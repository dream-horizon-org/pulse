/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.instrumentation.click.ClickEventBuffer
import io.opentelemetry.android.instrumentation.click.RageConfig

/**
 * DSL builder for rage-click detection parameters.
 *
 * Values set here are used as local defaults. Any field sent by the backend will override
 * the corresponding local value at config resolution time.
 *
 * Usage (inside `viewClick { }` block):
 * ```kotlin
 * viewClick {
 *     rage {
 *         timeWindowMs(1000)
 *         rageThreshold(3)
 *         radiusDp(50f)
 *     }
 * }
 * ```
 */
@OpenTelemetryDslMarker
class RageConfiguration internal constructor() {
    private var timeWindowMs: Long = ClickEventBuffer.TIME_WINDOW_MS
    private var rageThreshold: Int = ClickEventBuffer.RAGE_THRESHOLD
    private var radiusDp: Float = ClickEventBuffer.RADIUS_DP

    /** Sliding window in ms. Taps outside this window are not counted in the same rage cluster. */
    fun timeWindowMs(value: Long) {
        timeWindowMs = value
    }

    /**
     * Minimum number of taps within [timeWindowMs] and [radiusDp] to trigger a rage event.
     * Rage fires when tap count **>= rageThreshold**.
     */
    fun rageThreshold(value: Int) {
        rageThreshold = value
    }

    /** Radius in dp within which taps are considered the same location for rage detection. */
    fun radiusDp(value: Float) {
        radiusDp = value
    }

    internal fun build(): RageConfig =
        RageConfig(
            timeWindowMs = timeWindowMs,
            rageThreshold = rageThreshold,
            radiusDp = radiusDp,
        )
}
