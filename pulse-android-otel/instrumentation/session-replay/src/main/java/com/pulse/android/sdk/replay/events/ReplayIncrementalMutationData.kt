package com.pulse.android.sdk.replay.events

/**
 * Mutation payload for [ReplayIncrementalSnapshotEvent].
 */
public data class ReplayIncrementalMutationData(
    public val adds: List<ReplayMutatedNode>? = null,
    public val removes: List<ReplayRemovedNode>? = null,
    public val updates: List<ReplayMutatedNode>? = null,
    public val source: ReplayIncrementalSource = ReplayIncrementalSource.Mutation,
)
