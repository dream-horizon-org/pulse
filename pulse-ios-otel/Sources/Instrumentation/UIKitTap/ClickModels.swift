/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Data captured at tap time, held in the buffer until it is safe to emit individually
/// (no rage cluster formed) or discarded (rage detected).
///
/// Widget fields are non-null only when the tap landed on a clickable target (hasTarget == true).
/// clickContext is the pre-computed "app.click.context" label string (avoids re-traversal on flush).
internal struct PendingClick {
    let x: Float
    let y: Float
    let timestampMs: Int64  // monotonic (elapsedRealtime equivalent - for eviction)
    let tapEpochMs: Int64   // wall-clock ms (for OTel event timestamp)
    let hasTarget: Bool
    let widgetName: String?
    let widgetId: String?
    let clickContext: String?
    let viewportWidthPt: Int
    let viewportHeightPt: Int
}

/// Rage event emitted once the rage window closes with full accumulated tap count.
/// Delivered via the onRage callback passed to the ClickEventBuffer constructor.
///
/// count reflects ALL taps in the cluster including those suppressed after the initial threshold.
/// hasTarget mirrors the triggering PendingClick.hasTarget — reliable because the rage radius
/// constraint means all buffered taps are near the same point, so they share the same target state.
internal struct RageEvent {
    var count: Int
    let hasTarget: Bool
    let x: Float
    let y: Float
    let tapEpochMs: Int64
    let widgetName: String?
    let widgetId: String?
    let clickContext: String?
    let viewportWidthPt: Int
    let viewportHeightPt: Int
}
