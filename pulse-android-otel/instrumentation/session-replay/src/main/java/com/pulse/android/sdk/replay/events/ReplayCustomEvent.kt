package com.pulse.android.sdk.replay.events

/**
 * Custom event (e.g. keyboard open/close) with tag and payload.
 */
public class ReplayCustomEvent(
    tag: String,
    payload: Any,
    timestamp: Long,
) : ReplayEvent(
    type = ReplayEventType.Custom,
    timestamp = timestamp,
    data = mapOf(
        "tag" to tag,
        "payload" to payload,
    ),
)
