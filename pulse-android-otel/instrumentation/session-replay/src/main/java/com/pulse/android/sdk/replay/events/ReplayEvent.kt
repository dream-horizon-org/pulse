package com.pulse.android.sdk.replay.events

/**
 * Single replay event. Emitted in batches to [com.pulse.android.sdk.replay.ReplayEventEmitter].
 */
public open class ReplayEvent(
    public val type: ReplayEventType,
    public val timestamp: Long,
    public val data: ReplayEventData? = null,
)
