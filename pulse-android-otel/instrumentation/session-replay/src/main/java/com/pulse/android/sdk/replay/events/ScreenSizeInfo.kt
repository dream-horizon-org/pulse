package com.pulse.android.sdk.replay.events

/**
 * Screen dimensions in density-independent units (matches PostHog).
 */
public data class ScreenSizeInfo(
    public val width: Int,
    public val height: Int,
    public val density: Float,
)
