package com.pulse.android.sdk.replay

import com.pulse.android.sdk.replay.events.ReplayEvent

/**
 * Extension point: receives batches of replay events with session context.
 * Implement to buffer, upload, or forward to your pipeline.
 * Called on a background thread; do not block longer than necessary.
 *
 * [sessionId] is typically from the RUM session (e.g. [SessionProvider]); when not set, a single UUID is used per integration instance.
 * Consumers should send the envelope (event + properties.session_id + properties.snapshot_data + properties.snapshot_source) as built by the SDK or build their own.
 */
public fun interface ReplayEventEmitter {
    /**
     * Emit a batch of replay events for the given session.
     * @param sessionId Session identifier (UUID). Same for the lifetime of a replay session until start(resumeCurrent = false) is called again.
     * @param events Batch of replay events (e.g. Meta + FullSnapshot, or IncrementalSnapshot + Custom).
     */
    public fun emit(sessionId: String, events: List<ReplayEvent>)
}
