package com.pulse.android.sdk.replay.events

/**
 * Node removed in an incremental snapshot.
 */
public data class ReplayRemovedNode(
    public val id: Int,
    public val parentId: Int? = null,
)
