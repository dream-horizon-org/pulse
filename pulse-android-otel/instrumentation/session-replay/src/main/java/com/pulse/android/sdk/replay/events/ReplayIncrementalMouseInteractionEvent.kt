package com.pulse.android.sdk.replay.events

/**
 * Incremental snapshot event for mouse/touch interactions (ACTION_DOWN/UP).
 */
public class ReplayIncrementalMouseInteractionEvent(
    mouseInteractionData: ReplayIncrementalMouseInteractionData?,
    timestamp: Long,
) : ReplayEvent(
    type = ReplayEventType.IncrementalSnapshot,
    timestamp = timestamp,
    data = mouseInteractionData,
)
