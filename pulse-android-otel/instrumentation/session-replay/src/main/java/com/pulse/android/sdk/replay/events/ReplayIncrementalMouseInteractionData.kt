package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Payload for mouse/touch interaction incremental snapshot.
 */
public class ReplayIncrementalMouseInteractionData(
    public val id: Int,
    public val type: ReplayMouseInteraction,
    public val x: Int,
    public val y: Int,
    public val source: ReplayIncrementalSource = ReplayIncrementalSource.MOUSE_INTERACTION,
    public val pointerType: PointerType = PointerType.TOUCH,
    public val positions: List<ReplayMousePosition>? = null,
) : ReplayEventData {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayIncrementalMouseInteractionData) return false
        return id == other.id &&
            type == other.type &&
            x == other.x &&
            y == other.y &&
            source == other.source &&
            pointerType == other.pointerType &&
            positions == other.positions
    }

    override fun hashCode(): Int = Objects.hash(id, type, x, y, source, pointerType, positions)
}
