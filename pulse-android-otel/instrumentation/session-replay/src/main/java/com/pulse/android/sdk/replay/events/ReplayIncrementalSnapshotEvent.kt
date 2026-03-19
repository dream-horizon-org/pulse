package com.pulse.android.sdk.replay.events

/**
 * Incremental snapshot: adds/removes/updates since last snapshot.
 */
public class ReplayIncrementalSnapshotEvent(
    mutationData: ReplayIncrementalMutationData,
    timestamp: Long,
) : ReplayEvent(
        type = ReplayEventType.INCREMENTAL_SNAPSHOT,
        timestamp = timestamp,
        data = mutationData,
    )
