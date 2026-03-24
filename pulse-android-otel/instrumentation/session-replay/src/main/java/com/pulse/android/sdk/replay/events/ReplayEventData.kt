package com.pulse.android.sdk.replay.events

/**
 * Type-safe payload for [ReplayEvent]. Each event type has a concrete data class.
 * The encoder uses exhaustive `when` to guarantee all types are handled at compile time.
 */
public sealed interface ReplayEventData

public data class ReplayMetaData(
    val href: String,
    val width: Int,
    val height: Int,
) : ReplayEventData

public data class ReplayFullSnapshotData(
    val wireframes: List<ReplayWireframe>,
    val initialOffsetTop: Int,
    val initialOffsetLeft: Int,
) : ReplayEventData

public data class ReplayCustomEventData(
    val tag: String,
    val payload: Map<String, Any>,
) : ReplayEventData
