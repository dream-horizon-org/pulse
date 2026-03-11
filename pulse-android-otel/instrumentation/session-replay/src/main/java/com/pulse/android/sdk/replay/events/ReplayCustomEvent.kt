package com.pulse.android.sdk.replay.events

/**
 * Custom event (e.g. keyboard open/close) with tag and payload.
 */
public class ReplayCustomEvent(
    tag: String,
    payload: Map<String, Any>,
    timestamp: Long,
) : ReplayEvent(
    type = ReplayEventType.Custom,
    timestamp = timestamp,
    data = ReplayCustomEventData(tag = tag, payload = payload),
)
