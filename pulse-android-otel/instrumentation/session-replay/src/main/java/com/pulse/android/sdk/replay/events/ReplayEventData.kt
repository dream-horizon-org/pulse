package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Type-safe payload for [ReplayEvent]. Each event type has a concrete data class.
 * The encoder uses exhaustive `when` to guarantee all types are handled at compile time.
 */
public sealed interface ReplayEventData

public class ReplayMetaData(
    public val href: String,
    public val width: Int,
    public val height: Int,
) : ReplayEventData {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayMetaData) return false
        return href == other.href && width == other.width && height == other.height
    }

    override fun hashCode(): Int = Objects.hash(href, width, height)
}

public class ReplayFullSnapshotData(
    public val wireframes: List<ReplayWireframe>,
    public val initialOffsetTop: Int,
    public val initialOffsetLeft: Int,
) : ReplayEventData {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayFullSnapshotData) return false
        return wireframes == other.wireframes &&
            initialOffsetTop == other.initialOffsetTop &&
            initialOffsetLeft == other.initialOffsetLeft
    }

    override fun hashCode(): Int = Objects.hash(wireframes, initialOffsetTop, initialOffsetLeft)
}

public class ReplayCustomEventData(
    public val tag: String,
    public val payload: Map<String, Any>,
) : ReplayEventData {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayCustomEventData) return false
        return tag == other.tag && payload == other.payload
    }

    override fun hashCode(): Int = Objects.hash(tag, payload)
}
