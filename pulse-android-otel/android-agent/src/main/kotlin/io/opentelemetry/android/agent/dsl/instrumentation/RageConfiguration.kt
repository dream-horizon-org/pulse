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
 *         timeWindowMs = 1000
 *         threshold = 3
 *         radiusDp = 50f
 *     }
 * }
 * ```
 */
@OpenTelemetryDslMarker
class RageConfiguration internal constructor() {
    /** Sliding window in ms. Taps outside this window are not counted in the same rage cluster. */
    var timeWindowMs: Long = ClickEventBuffer.TIME_WINDOW_MS

    /**
     * Minimum number of taps within [timeWindowMs] and [radiusDp] to trigger a rage event.
     * Rage fires when tap count **>= threshold**.
     */
    var threshold: Int = ClickEventBuffer.RAGE_THRESHOLD

    /** Radius in dp within which taps are considered the same location for rage detection. */
    var radiusDp: Float = ClickEventBuffer.RADIUS_DP

    internal fun build(): RageConfig =
        RageConfig(
            timeWindowMs = timeWindowMs,
            threshold = threshold,
            radiusDp = radiusDp,
        )
}
