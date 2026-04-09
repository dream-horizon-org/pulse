package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Node added or updated in an incremental snapshot.
 */
public class ReplayMutatedNode(
    public val wireframe: ReplayWireframe,
    public val parentId: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayMutatedNode) return false
        return wireframe == other.wireframe && parentId == other.parentId
    }

    override fun hashCode(): Int = Objects.hash(wireframe, parentId)
}
