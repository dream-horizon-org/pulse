/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click

/**
 * Resolved, immutable rage-detection parameters used by [ClickEventBuffer].
 *
 * All fields default to [ClickEventBuffer] compile-time constants so that a zero-argument
 * constructor gives identical behaviour to the previous hard-coded implementation.
 * Backend or DSL overrides are applied field-by-field before this object is constructed.
 *
 * @property timeWindowMs   Sliding window in ms. Taps outside this window are evicted from the
 *                          cluster and emitted individually.
 * @property threshold  Minimum tap count within [timeWindowMs] and [radiusDp] to trigger rage.
 *                          Rage fires when the cluster count **>= threshold**.
 * @property radiusDp       Radius in dp within which taps are considered the same location.
 */
data class RageConfig(
    val timeWindowMs: Long = ClickEventBuffer.TIME_WINDOW_MS,
    val threshold: Int = ClickEventBuffer.RAGE_THRESHOLD,
    val radiusDp: Float = ClickEventBuffer.RADIUS_DP,
)
