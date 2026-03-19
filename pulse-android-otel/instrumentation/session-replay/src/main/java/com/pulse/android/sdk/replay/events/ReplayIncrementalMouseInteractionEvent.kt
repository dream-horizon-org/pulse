package com.pulse.android.sdk.replay.events

/**
 * Incremental snapshot event for mouse/touch interactions (ACTION_DOWN/UP).
 */
public class ReplayIncrementalMouseInteractionEvent(
    mouseInteractionData: ReplayIncrementalMouseInteractionData,
    timestamp: Long,
) : ReplayEvent(
        type = ReplayEventType.INCREMENTAL_SNAPSHOT,
        timestamp = timestamp,
        data = mouseInteractionData,
    )
