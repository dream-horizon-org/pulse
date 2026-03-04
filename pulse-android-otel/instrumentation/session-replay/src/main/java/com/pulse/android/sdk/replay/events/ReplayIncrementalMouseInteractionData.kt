package com.pulse.android.sdk.replay.events

/**
 * Payload for mouse/touch interaction incremental snapshot.
 */
public data class ReplayIncrementalMouseInteractionData(
    public val id: Int,
    public val type: ReplayMouseInteraction,
    public val x: Int,
    public val y: Int,
    public val source: ReplayIncrementalSource = ReplayIncrementalSource.MouseInteraction,
    public val pointerType: Int = 2, // Touch
    public val positions: List<ReplayMousePosition>? = null,
)
