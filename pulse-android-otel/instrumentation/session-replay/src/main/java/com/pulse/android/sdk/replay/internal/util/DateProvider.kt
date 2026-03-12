package com.pulse.android.sdk.replay.internal.util

/**
 * Abstraction for time to allow testing and consistent units.
 */
internal interface DateProvider {
    fun currentTimeMillis(): Long
    fun nanoTime(): Long
}

internal class DefaultDateProvider : DateProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun nanoTime(): Long = System.nanoTime()
}
