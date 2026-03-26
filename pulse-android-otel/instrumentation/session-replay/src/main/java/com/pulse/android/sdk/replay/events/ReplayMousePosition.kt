package com.pulse.android.sdk.replay.events

/**
 * Mouse/touch position for interaction events.
 */
public data class ReplayMousePosition(
    public val x: Int,
    public val y: Int,
    public val id: Int,
    public val timeOffset: Long? = null,
)
