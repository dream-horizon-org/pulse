/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.slowrendering

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.internal.services.visiblescreen.activities.DefaultingActivityLifecycleCallbacks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RequiresApi(api = Build.VERSION_CODES.N)
internal class SlowRenderListener(
    private val jankReporter: JankReporter,
    private val executorService: ScheduledExecutorService,
    private val frameMetricsHandler: Handler,
    private val pollInterval: Duration,
) : DefaultingActivityLifecycleCallbacks {
    private val activities: ConcurrentMap<Activity, PerActivityListener> = ConcurrentHashMap()
    private var lastNormalFrameDataTimeInMs: Long = 0

    constructor(jankReporter: JankReporter, pollInterval: Duration) : this(
        jankReporter,
        Executors.newScheduledThreadPool(1),
        Handler(startFrameMetricsLoop()),
        pollInterval,
    )

    // the returned future is very unlikely to fail
    fun start() {
        executorService.scheduleWithFixedDelay(
            { this.reportSlowRenders() },
            pollInterval.toMillis(),
            pollInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    fun shutdown() {
        executorService.shutdownNow()
        for (entry in activities.entries) {
            val activity: Activity = entry.key!!
            val listener = entry.value
            activity.window.removeOnFrameMetricsAvailableListener(listener)
        }
        activities.clear()
    }

    override fun onActivityResumed(activity: Activity) {
        if (executorService.isShutdown) {
            return
        }

        fun ensureMaxCountNotExceeded() {
            if (FrameDataHelper.frameDataEvents.size > FrameDataHelper.FRAME_EVENTS_MAX_COUNT) {
                FrameDataHelper.frameDataEvents.removeFirst()
            }
        }
        val listener =
            PerActivityListener(activity) { frameData ->
                if (frameData.type != PerActivityListener.FrameData.NORMAL) {
                    ensureMaxCountNotExceeded()
                    val lastEvent = FrameDataHelper.frameDataEvents.lastOrNull()
                    FrameDataHelper.frameDataEvents.add(
                        FrameDataHelper.CumulativeFrameData(
                            timeInMs = System.currentTimeMillis(),
                            analysedFrameCount = FrameDataHelper.totalAnalysedFrames,
                            unanalysedFrameCount = FrameDataHelper.totalUnanalysedDroppedFrames,
                            slowFrameCount =
                                (
                                    lastEvent?.slowFrameCount ?: 0
                                ) + if (frameData.type == PerActivityListener.FrameData.SLOW) 1 else 0,
                            frozenFrameCount =
                                (
                                    lastEvent?.frozenFrameCount ?: 0
                                ) + if (frameData.type == PerActivityListener.FrameData.FROZEN) 1 else 0,
                        ),
                    )
                } else {
                    FrameDataHelper.totalAnalysedFrames += 1
                    FrameDataHelper.totalUnanalysedDroppedFrames += frameData.unanalysedFrameSinceLastCall

                    val currentTimeInMs = System.currentTimeMillis()
                    if (lastNormalFrameDataTimeInMs == 0L || currentTimeInMs - lastNormalFrameDataTimeInMs >= THRESHOLD_ADD_DATA_IN_MS) {
                        ensureMaxCountNotExceeded()
                        val lastEvent = FrameDataHelper.frameDataEvents.lastOrNull()
                        FrameDataHelper.frameDataEvents.add(
                            FrameDataHelper.CumulativeFrameData(
                                timeInMs = currentTimeInMs,
                                analysedFrameCount = FrameDataHelper.totalAnalysedFrames,
                                unanalysedFrameCount = FrameDataHelper.totalUnanalysedDroppedFrames,
                                slowFrameCount = lastEvent?.slowFrameCount ?: 0,
                                frozenFrameCount = lastEvent?.frozenFrameCount ?: 0,
                            ),
                        )
                        lastNormalFrameDataTimeInMs = currentTimeInMs
                    }
                }
            }
        val existing = activities.putIfAbsent(activity, listener)
        if (existing == null) {
            activity.window.addOnFrameMetricsAvailableListener(listener, frameMetricsHandler)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (executorService.isShutdown) {
            return
        }
        val listener = activities.remove(activity)
        if (listener != null) {
            activity.window.removeOnFrameMetricsAvailableListener(listener)
            val durationToCountHistogram = listener.resetMetrics()
            jankReporter.reportSlow(durationToCountHistogram, pollInterval.toSeconds().toDouble(), listener.getActivityName())
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reportSlowRenders() {
        try {
            activities.forEach { (_: Activity?, listener: PerActivityListener) ->
                val durationToCountHistogram = listener.resetMetrics()
                jankReporter.reportSlow(durationToCountHistogram, pollInterval.toSeconds().toDouble(), listener.getActivityName())
            }
        } catch (e: Exception) {
            Log.w(RumConstants.OTEL_RUM_LOG_TAG, "Exception while processing frame metrics", e)
        }
    }

    companion object {
        private const val THRESHOLD_ADD_DATA_IN_MS = 1000L
        private val frameMetricsThread = HandlerThread("FrameMetricsCollector")

        private fun startFrameMetricsLoop(): Looper {
            // just a precaution: this is supposed to be called only once, and the thread should always
            // be not started here
            if (!frameMetricsThread.isAlive) {
                frameMetricsThread.start()
            }
            return frameMetricsThread.looper
        }
    }
}
