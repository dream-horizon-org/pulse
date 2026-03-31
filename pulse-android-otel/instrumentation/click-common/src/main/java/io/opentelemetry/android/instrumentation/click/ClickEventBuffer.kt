/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Data captured at tap time, held in the buffer until it is safe to emit individually
 * (no rage cluster formed) or discarded (rage detected).
 *
 * Widget fields are non-null only when the tap landed on a clickable target ([hasTarget] = true).
 * [clickContext] is the pre-computed `app.click.context` label string (avoids re-traversal on flush).
 */
public data class PendingClick(
    val x: Float,
    val y: Float,
    val timestampMs: Long, // monotonic (elapsedRealtime) — used for rage detection timing only
    val tapEpochMs: Long, // wall-clock ms at tap time — used as the OTel event timestamp
    val hasTarget: Boolean,
    val widgetName: String? = null,
    val widgetId: String? = null,
    val clickContext: String? = null,
    val viewportWidthPx: Int = 0, // decorView.width at tap time — app window, not device screen
    val viewportHeightPx: Int = 0,
)

/**
 * Rage event emitted once the rage window closes with full accumulated tap count.
 * Delivered via the `onRage` callback passed to the [ClickEventBuffer] constructor.
 *
 * [count] reflects ALL taps in the cluster including those suppressed after the initial threshold.
 * [hasTarget] mirrors the triggering [PendingClick.hasTarget] — reliable because the rage radius
 * constraint means all buffered taps are near the same point, so they share the same target state.
 */
public data class RageEvent(
    val count: Int,
    val hasTarget: Boolean,
    val x: Float,
    val y: Float,
    val tapEpochMs: Long,
    val widgetName: String? = null,
    val widgetId: String? = null,
    val clickContext: String? = null,
    val viewportWidthPx: Int = 0,
    val viewportHeightPx: Int = 0,
)

/**
 * Detects rage-click clusters on the UI thread with zero background threads.
 *
 * ### Algorithm
 * 1. On each tap, evict buffer entries older than [RageConfig.timeWindowMs] via [onEmit].
 * 2. Add the current tap to the buffer and count how many buffered taps are within [RageConfig.radiusDp] dp.
 * 3. If the count reaches [RageConfig.rageThreshold], clear the buffer, activate rage-suppression,
 *    store a pending [RageEvent], and schedule delayed emission after [RageConfig.timeWindowMs] of inactivity.
 * 4. While rage-suppression is active (within [RageConfig.timeWindowMs] of the last rage tap):
 *    - Each new tap extends the suppression window, increments the pending count, and
 *      reschedules the delayed emission.
 * 5. The [RageEvent] is delivered via [onRage] as soon as the window closes — whichever comes first:
 *    - The [RageConfig.timeWindowMs] delayed emission fires (no new taps within the window).
 *    - The next tap arrives after the window expires.
 *    - [flush] is called (activity pause).
 * 6. Call [flush] on activity pause so that buffered individual clicks are not dropped.
 *
 * ### Threading
 * All methods must be called from the UI thread. The delayed emission Runnable also runs on the
 * UI thread (posted to [Looper.getMainLooper]). No synchronization is needed.
 *
 * @param densityScale  [android.util.DisplayMetrics.density] — converts [RageConfig.radiusDp] to px.
 * @param rageConfig    Runtime rage-detection parameters. Defaults to [RageConfig] which uses the
 *                      companion-object constants, preserving existing behaviour when not configured.
 * @param onRage        Called on the UI thread when a rage cluster window closes.
 * @param onEmit        Called synchronously for each buffered click evicted or flushed.
 * @param clock         Monotonic clock in ms (injectable for tests).
 * @param postDelayed   Schedules a delayed UI-thread action (injectable for tests).
 * @param cancelDelayed Cancels a previously scheduled action (injectable for tests).
 */
class ClickEventBuffer(
    densityScale: Float,
    private val rageConfig: RageConfig = RageConfig(),
    private val onRage: (RageEvent) -> Unit = {},
    private val onEmit: (PendingClick) -> Unit = {},
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val postDelayed: (Runnable, Long) -> Unit = { r, ms -> mainHandler.postDelayed(r, ms) },
    private val cancelDelayed: (Runnable) -> Unit = { r -> mainHandler.removeCallbacks(r) },
) {
    companion object {
        const val TIME_WINDOW_MS: Long = 2000L
        const val RAGE_THRESHOLD: Int = 3
        const val RADIUS_DP: Float = 50f

        // Shared Handler — avoids allocating one per ClickEventBuffer instance.
        internal val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    }

    // Pre-squared so rage radius check avoids sqrt on every tap.
    private val radiusPxSquared: Float = (rageConfig.radiusDp * densityScale).let { it * it }

    // Pre-sized to rageThreshold + 1 so no resize occurs during normal rage detection.
    private val buffer = ArrayDeque<PendingClick>(rageConfig.rageThreshold + 1)

    private var isRageActive = false
    private var lastRageTimeMs = 0L
    private var pendingRage: RageEvent? = null

    // Runnable posted to the main handler to emit rage after TIME_WINDOW_MS of inactivity.
    private val emitPendingRageRunnable =
        Runnable {
            pendingRage?.let { onRage(it) }
            pendingRage = null
            isRageActive = false
        }

    /**
     * Returns the current monotonic timestamp in ms. Generators use this to stamp [PendingClick]
     * so that buffer timing tests work with an injected [clock].
     */
    public fun currentTimeMs(): Long = clock()

    /**
     * Records a tap. Stale buffered clicks are emitted via [onEmit]. Returns Unit.
     *
     * @param click   The tap to record (coordinates in px).
     */
    fun record(click: PendingClick) {
        if (isRageActive) {
            if (click.timestampMs - lastRageTimeMs <= rageConfig.timeWindowMs) {
                // Extend suppression window: reschedule timer and accumulate count.
                lastRageTimeMs = click.timestampMs
                pendingRage = pendingRage?.let { it.copy(count = it.count + 1) }
                cancelDelayed(emitPendingRageRunnable)
                postDelayed(emitPendingRageRunnable, rageConfig.timeWindowMs)
            } else {
                // Rage window already closed inline (tap arrived after delay fired, or clock jumped).
                // The delayed Runnable may have already fired; cancel it to be safe.
                cancelDelayed(emitPendingRageRunnable)
                isRageActive = false
                val completed = pendingRage
                pendingRage = null
                completed?.let { onRage(it) }
                processNormal(click)
            }
            return
        }
        processNormal(click)
    }

    /**
     * Emits any pending rage event and all buffered individual clicks, then resets state.
     * Call this when the generator stops tracking (activity pause).
     */
    fun flush() {
        cancelDelayed(emitPendingRageRunnable)
        pendingRage?.let { onRage(it) }
        pendingRage = null
        isRageActive = false
        while (buffer.isNotEmpty()) onEmit(buffer.removeFirst())
    }

    private fun processNormal(click: PendingClick) {
        evictStale(click.timestampMs)
        buffer.addLast(click)

        val nearbyCount = buffer.count { withinRadius(it.x, it.y, click.x, click.y) }
        if (nearbyCount >= rageConfig.rageThreshold) {
            pendingRage =
                RageEvent(
                    count = buffer.size,
                    hasTarget = click.hasTarget,
                    x = click.x,
                    y = click.y,
                    tapEpochMs = click.tapEpochMs,
                    widgetName = click.widgetName,
                    widgetId = click.widgetId,
                    clickContext = click.clickContext,
                    viewportWidthPx = click.viewportWidthPx,
                    viewportHeightPx = click.viewportHeightPx,
                )
            buffer.clear()
            isRageActive = true
            lastRageTimeMs = click.timestampMs
            postDelayed(emitPendingRageRunnable, rageConfig.timeWindowMs)
        }
    }

    private fun evictStale(nowMs: Long) {
        val cutoff = nowMs - rageConfig.timeWindowMs
        while (buffer.isNotEmpty() && buffer.first().timestampMs < cutoff) {
            onEmit(buffer.removeFirst())
        }
    }

    private fun withinRadius(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Boolean {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy <= radiusPxSquared
    }
}
