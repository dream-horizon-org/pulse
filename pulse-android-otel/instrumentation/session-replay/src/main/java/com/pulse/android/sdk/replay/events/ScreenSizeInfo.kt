package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Screen dimensions in density-independent units (matches PostHog).
 */
public class ScreenSizeInfo(
    public val width: Int,
    public val height: Int,
    public val density: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScreenSizeInfo) return false
        return width == other.width && height == other.height && density == other.density
    }

    override fun hashCode(): Int = Objects.hash(width, height, density)
}
