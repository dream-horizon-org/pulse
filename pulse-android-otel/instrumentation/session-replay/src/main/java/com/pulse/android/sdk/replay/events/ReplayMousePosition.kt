package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Mouse/touch position for interaction events.
 */
public class ReplayMousePosition(
    public val x: Int,
    public val y: Int,
    public val id: Int,
    public val timeOffset: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayMousePosition) return false
        return x == other.x && y == other.y && id == other.id && timeOffset == other.timeOffset
    }

    override fun hashCode(): Int = Objects.hash(x, y, id, timeOffset)
}
