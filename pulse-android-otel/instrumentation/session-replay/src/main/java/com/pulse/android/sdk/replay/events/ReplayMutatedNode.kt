package com.pulse.android.sdk.replay.events

/**
 * Node added or updated in an incremental snapshot.
 */
public data class ReplayMutatedNode(
    public val wireframe: ReplayWireframe,
    public val parentId: Int? = null,
)
