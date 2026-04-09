/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click

/**
 * Resolved, immutable rage-detection parameters.
 *
 * All fields default to the compile-time constants so that a zero-argument constructor gives
 * identical behaviour to the previous hard-coded implementation. Backend or DSL overrides are
 * applied field-by-field before this object is constructed.
 *
 * @property timeWindowMs   Sliding window in ms. Taps outside this window are evicted from the
 *                          cluster and emitted individually.
 * @property threshold  Minimum tap count within [timeWindowMs] and [radiusDp] to trigger rage.
 *                          Rage fires when the cluster count **>= threshold**.
 * @property radiusDp       Radius in dp within which taps are considered the same location.
 */
class RageConfig(
    val timeWindowMs: Long = TIME_WINDOW_MS,
    val threshold: Int = RAGE_THRESHOLD,
    val radiusDp: Float = RADIUS_DP,
) {
    companion object {
        const val TIME_WINDOW_MS: Long = 2000L
        const val RAGE_THRESHOLD: Int = 3
        const val RADIUS_DP: Float = 50f
    }
}
