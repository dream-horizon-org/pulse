/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click

import android.os.Handler
import android.os.Looper
import androidx.annotation.UiThread
import com.pulse.utils.PulseOtelUtils

/**
 * Data captured at tap time, held in the buffer until it is safe to emit individually
 * (no rage cluster formed) or discarded (rage detected).
 *
 * Widget fields are non-null only when the tap landed on a clickable target ([hasTarget] = true).
 * [clickContext] is the pre-computed `app.click.context` label string (avoids re-traversal on flush).
 *
 * @property xPx tap x coordinate in pixels
 * @property yPx tap y coordinate in pixels
 * @property timestampMs monotonic (elapsedRealtime) — used for rage detection timing only
 * @property tapEpochMs used as the OTel event timestamp
 * @property hasTarget true when the tap landed on a clickable target; false for a dead click
 * @property widgetName accessibility/semantic name of the hit target, or null for a dead click
 * @property widgetId view/node id of the hit target, or null for a dead click
 * @property clickContext pre-computed `app.click.context` label, or null when enrichment is disabled
 * @property viewportWidthPx decorView.width at tap time — app window, not device screen
 * @property viewportHeightPx decorView.height at tap time — app window, not device screen
 */
class PendingClick(
    val xPx: Float,
    val yPx: Float,
    val timestampMs: Long,
    val tapEpochMs: Long,
    val hasTarget: Boolean,
    val widgetName: String? = null,
    val widgetId: String? = null,
    val clickContext: String? = null,
    val viewportWidthPx: Int = 0,
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
internal class RageEvent(
    val count: Int,
    val hasTarget: Boolean,
    val xPx: Float,
    val yPx: Float,
    val tapEpochMs: Long,
    val widgetName: String? = null,
    val widgetId: String? = null,
    val clickContext: String? = null,
    val viewportWidthPx: Int = 0,
    val viewportHeightPx: Int = 0,
) {
    internal fun copy(
        count: Int = this.count,
        tapEpochMs: Long = this.tapEpochMs,
    ) = RageEvent(
        count = count,
        hasTarget = hasTarget,
        xPx = xPx,
        yPx = yPx,
        tapEpochMs = tapEpochMs,
        widgetName = widgetName,
        widgetId = widgetId,
        clickContext = clickContext,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
    )
}

/**
 * Detects rage-click clusters on the UI thread with zero background threads.
 *
 * ### Algorithm
 * 1. On each tap, emit any clusters whose window has expired inline.
 * 2. Check all active clusters — if the tap lands within [RageConfig.radiusDp] of a cluster and
 *    within [RageConfig.timeWindowMs] of its last tap, extend that cluster (increment count,
 *    reschedule its timer). Nearest cluster wins when a tap overlaps multiple clusters.
 * 3. If no active cluster matches, run [processNormal]:
 *    a. Evict buffer entries older than [RageConfig.timeWindowMs] via [onEmit].
 *    b. Add the tap to the buffer.
 *    c. Count how many buffered taps are within [RageConfig.radiusDp] of this tap.
 *    d. If count >= [RageConfig.threshold], form a new cluster:
 *       - Remove only the nearby taps from the buffer (taps at other locations stay).
 *       - Schedule the cluster's delayed emission after [RageConfig.timeWindowMs] of inactivity.
 * 4. Each cluster emits independently via [onRage] when its window closes — whichever comes first:
 *    - The delayed Runnable fires (no new taps near that cluster within the window).
 *    - The cluster's window is found expired on the next [record] call.
 *    - [flush] is called (activity pause).
 * 5. Call [flush] on activity pause so buffered individual clicks are not dropped.
 *
 * ### Multi-cluster behaviour
 * Multiple rage clusters can be active simultaneously at different screen locations.
 * Each cluster owns its own rage event, last-tap timestamp, and delayed emission Runnable.
 * A tap at location B never extends a rage cluster at location A because the radius check
 * gates every extension. Taps at B remain in the shared buffer and form their own cluster
 * independently.
 *
 * ### Cluster limit
 * At most [MAX_ACTIVE_CLUSTERS] clusters can be active at the same time. If a new cluster would
 * exceed this cap, the oldest cluster (by [RageCluster.lastTapTimeMs]) is emitted immediately
 * before the new one is added. This ensures no rage event is ever silently dropped.
 *
 * ### Threading
 * All methods must be called from the UI thread. Delayed emission Runnables also run on the
 * UI thread (posted to [Looper.getMainLooper]). No synchronization is needed.
 *
 * @param densityScale  [android.util.DisplayMetrics.density] — converts [RageConfig.radiusDp] to px.
 * @param rageConfig    Runtime rage-detection parameters.
 * @param onRage        Called on the UI thread when a rage cluster window closes.
 * @param onEmit        Called synchronously for each buffered click evicted or flushed.
 * @param postDelayed   Schedules a delayed UI-thread action (injectable for tests).
 * @param cancelDelayed Cancels a previously scheduled action (injectable for tests).
 */
internal class ClickEventBuffer(
    densityScale: Float,
    private val rageConfig: RageConfig = RageConfig(),
    private val onRage: (RageEvent) -> Unit = {},
    private val onEmit: (PendingClick) -> Unit = {},
    private val postDelayed: (Runnable, Long) -> Unit = { r, ms -> mainHandler.postDelayed(r, ms) },
    private val cancelDelayed: (Runnable) -> Unit = { r -> mainHandler.removeCallbacks(r) },
) {
    companion object {
        /** Maximum number of simultaneously active rage clusters. Oldest is emitted when exceeded. */
        const val MAX_ACTIVE_CLUSTERS: Int = 5

        internal val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    }

    init {
        require(rageConfig.threshold > 0) { "rageThreshold must be > 0, got ${rageConfig.threshold}" }
        require(rageConfig.timeWindowMs > 0) { "timeWindowMs must be > 0, got ${rageConfig.timeWindowMs}" }
    }

    private val effectiveDensity: Float = if (densityScale > 0f) densityScale else 1f
    private val radiusPxSquared: Float = (rageConfig.radiusDp * effectiveDensity).let { it * it }

    private val buffer = ArrayDeque<PendingClick>()

    private val activeRageClusters = mutableListOf<RageCluster>()

    private inner class RageCluster(
        initialRage: RageEvent,
        tapTimeMs: Long,
    ) {
        var rage: RageEvent = initialRage
        var lastTapTimeMs: Long = tapTimeMs

        val emitRunnable =
            Runnable {
                activeRageClusters.remove(this)
                onRage(rage)
            }

        fun extend(click: PendingClick) {
            lastTapTimeMs = click.timestampMs
            rage = rage.copy(count = rage.count + 1, tapEpochMs = click.tapEpochMs)
        }
    }

    /**
     * Records a tap. Expired clusters are emitted, active clusters extended if the tap is nearby,
     * otherwise the tap is buffered and may form a new cluster.
     */
    @UiThread
    fun record(click: PendingClick) {
        if (PulseOtelUtils.isDebug()) check(Looper.myLooper() == Looper.getMainLooper()) { "record() must be called on the main thread" }
        emitExpiredClusters(click.timestampMs)

        // Find the nearest active cluster within radius — nearest wins when clusters overlap.
        val matchingCluster =
            activeRageClusters
                .filter { withinRadius(click.xPx, click.yPx, it.rage.xPx, it.rage.yPx) }
                .minByOrNull { distanceSquared(click.xPx, click.yPx, it.rage.xPx, it.rage.yPx) }

        if (matchingCluster != null) {
            matchingCluster.extend(click)
            cancelDelayed(matchingCluster.emitRunnable)
            postDelayed(matchingCluster.emitRunnable, rageConfig.timeWindowMs)
            return
        }

        processNormal(click)
    }

    /**
     * Emits all pending rage clusters and all buffered individual clicks, then resets state.
     * Call this when the generator stops tracking (activity pause).
     */
    @UiThread
    fun flush() {
        if (PulseOtelUtils.isDebug()) check(Looper.myLooper() == Looper.getMainLooper()) { "flush() must be called on the main thread" }
        activeRageClusters.forEach { cluster ->
            cancelDelayed(cluster.emitRunnable)
            onRage(cluster.rage)
        }
        activeRageClusters.clear()
        while (buffer.isNotEmpty()) onEmit(buffer.removeFirst())
    }

    private fun processNormal(click: PendingClick) {
        evictStale(click.timestampMs)
        buffer.addLast(click)

        val nearbyCount = buffer.count { withinRadius(it.xPx, it.yPx, click.xPx, click.yPx) }
        if (nearbyCount >= rageConfig.threshold) {
            val cluster =
                RageCluster(
                    initialRage =
                        RageEvent(
                            count = nearbyCount,
                            hasTarget = click.hasTarget,
                            xPx = click.xPx,
                            yPx = click.yPx,
                            tapEpochMs = click.tapEpochMs,
                            widgetName = click.widgetName,
                            widgetId = click.widgetId,
                            clickContext = click.clickContext,
                            viewportWidthPx = click.viewportWidthPx,
                            viewportHeightPx = click.viewportHeightPx,
                        ),
                    tapTimeMs = click.timestampMs,
                )
            // Remove only the taps that belong to this cluster — taps at other locations stay.
            buffer.removeAll { withinRadius(it.xPx, it.yPx, click.xPx, click.yPx) }
            // Enforce cluster cap: emit the oldest cluster immediately if limit is reached.
            if (activeRageClusters.size >= MAX_ACTIVE_CLUSTERS) {
                val oldest = activeRageClusters.minByOrNull { it.lastTapTimeMs } ?: return
                cancelDelayed(oldest.emitRunnable)
                activeRageClusters.remove(oldest)
                onRage(oldest.rage)
            }
            activeRageClusters.add(cluster)
            postDelayed(cluster.emitRunnable, rageConfig.timeWindowMs)
        }
    }

    /** Emits clusters whose window expired before the current tap arrived. */
    private fun emitExpiredClusters(nowMs: Long) {
        activeRageClusters.removeAll { cluster ->
            val isExpired = nowMs - cluster.lastTapTimeMs > rageConfig.timeWindowMs
            if (isExpired) {
                cancelDelayed(cluster.emitRunnable)
                onRage(cluster.rage)
            }
            isExpired
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

    private fun distanceSquared(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }
}
