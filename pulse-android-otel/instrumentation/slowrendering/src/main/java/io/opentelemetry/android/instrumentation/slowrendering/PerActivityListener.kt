/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.slowrendering

import android.app.Activity
import android.os.Build
import android.view.FrameMetrics
import android.view.Window
import android.view.Window.OnFrameMetricsAvailableListener
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import java.util.concurrent.TimeUnit

private val NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1).toInt()

// rounding value adds half a millisecond, for rounding to nearest ms
private val NANOS_ROUNDING_VALUE: Int = NANOS_PER_MS / 2

@RequiresApi(api = Build.VERSION_CODES.N)
internal class PerActivityListener(
    private val activity: Activity,
    private val onDataAvailable: (FrameData) -> Unit,
) : OnFrameMetricsAvailableListener {
    private val lock = Any()

    @GuardedBy("lock")
    private val drawDurationHistogram: MutableMap<Int, Int> = HashMap()

    fun getActivityName(): String = activity.componentName.flattenToShortString()

    override fun onFrameMetricsAvailable(
        window: Window?,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        val firstDrawFrame = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)
        if (firstDrawFrame == 1L) {
            return
        }

        val drawDurationsNs = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
        // ignore values < 0; something must have gone wrong
        if (drawDurationsNs >= 0) {
            synchronized(lock) {
                // calculation copied from FrameMetricsAggregator
                val durationMs = ((drawDurationsNs + NANOS_ROUNDING_VALUE) / NANOS_PER_MS).toInt()
                var frameType: Byte = FrameData.NORMAL
                if (durationMs > FROZEN_THRESHOLD_MS) {
                    frameType = FrameData.FROZEN
                } else if (durationMs > SLOW_THRESHOLD_MS) {
                    frameType = FrameData.SLOW
                }
                val oldValue: Int = drawDurationHistogram.getOrDefault(durationMs, 0)
                drawDurationHistogram[durationMs] = oldValue + 1
                onDataAvailable(FrameData(dropCountSinceLastInvocation, frameType))
            }
        }
    }

    fun resetMetrics(): Map<Int, Int> {
        synchronized(lock) {
            val metrics = HashMap(drawDurationHistogram)
            drawDurationHistogram.clear()
            return metrics
        }
    }

    internal data class FrameData(
        val unanalysedFrameSinceLastCall: Int,
        val type: Byte,
    ) {
        companion object Companion {
            internal const val FROZEN: Byte = 0
            internal const val SLOW: Byte = 1
            internal const val NORMAL: Byte = 2
        }
    }
}
