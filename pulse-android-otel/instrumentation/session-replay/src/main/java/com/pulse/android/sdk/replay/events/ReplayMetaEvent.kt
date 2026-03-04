package com.pulse.android.sdk.replay.events

/**
 * Meta event: screen title (href), width, height. Sent once per window/screen.
 */
public class ReplayMetaEvent(
    width: Int,
    height: Int,
    timestamp: Long,
    href: String,
) : ReplayEvent(
    type = ReplayEventType.Meta,
    timestamp = timestamp,
    data = mapOf(
        "href" to href,
        "width" to width,
        "height" to height,
    ),
)
