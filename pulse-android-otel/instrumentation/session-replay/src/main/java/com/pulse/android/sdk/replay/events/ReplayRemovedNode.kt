package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Node removed in an incremental snapshot.
 */
public class ReplayRemovedNode(
    public val id: Int,
    public val parentId: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayRemovedNode) return false
        return id == other.id && parentId == other.parentId
    }

    override fun hashCode(): Int = Objects.hash(id, parentId)
}
