package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Mutation payload for [ReplayIncrementalSnapshotEvent].
 */
public class ReplayIncrementalMutationData(
    public val adds: List<ReplayMutatedNode>? = null,
    public val removes: List<ReplayRemovedNode>? = null,
    public val updates: List<ReplayMutatedNode>? = null,
    public val source: ReplayIncrementalSource = ReplayIncrementalSource.MUTATION,
) : ReplayEventData {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayIncrementalMutationData) return false
        return adds == other.adds &&
            removes == other.removes &&
            updates == other.updates &&
            source == other.source
    }

    override fun hashCode(): Int = Objects.hash(adds, removes, updates, source)
}
