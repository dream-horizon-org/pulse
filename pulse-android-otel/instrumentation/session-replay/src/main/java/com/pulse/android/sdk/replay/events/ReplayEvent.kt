package com.pulse.android.sdk.replay.events

import com.pulse.android.sdk.replay.ReplayEventEmitter

/**
 * Single replay event. Emitted in batches to [ReplayEventEmitter].
 */
public open class ReplayEvent(
    public val type: ReplayEventType,
    public val timestamp: Long,
    public val data: Any? = null,
)
