package com.pulse.android.sdk.replay.events

/**
 * Full snapshot: complete list of wireframes (single screenshot node or full view tree).
 */
public class ReplayFullSnapshotEvent(
    wireframes: List<ReplayWireframe>,
    initialOffsetTop: Int,
    initialOffsetLeft: Int,
    timestamp: Long,
) : ReplayEvent(
    type = ReplayEventType.FullSnapshot,
    timestamp = timestamp,
    data = mapOf(
        "wireframes" to wireframes,
        "initialOffset" to mapOf(
            "top" to initialOffsetTop,
            "left" to initialOffsetLeft,
        ),
    ),
)
