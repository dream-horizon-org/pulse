/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.slowrendering

import android.util.Log
import com.pulse.utils.PulseLogger
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger

// TODO: Replace with semconv constants
internal val FRAME_COUNT: AttributeKey<Long> = AttributeKey.longKey("app.jank.frame_count")
internal val PERIOD: AttributeKey<Double> = AttributeKey.doubleKey("app.jank.period")
internal val THRESHOLD: AttributeKey<Double> = AttributeKey.doubleKey("app.jank.threshold")

internal class EventJankReporter(
    private val eventLogger: Logger,
    private val threshold: Double,
    private val minDurationMsExclusive: Int,
    private val maxDurationMsInclusive: Int? = null,
    private val isDebugVerbose: Boolean = false,
) : JankReporter {
    override fun reportSlow(
        durationToCountHistogram: Map<Int, Int>,
        periodSeconds: Double,
        activityName: String,
    ) {
        val frameCount =
            countFramesInDurationRange(
                durationToCountHistogram,
                minDurationMsExclusive,
                maxDurationMsInclusive,
            )
        if (isDebugVerbose && frameCount > 0) {
            for (entry in durationToCountHistogram) {
                val durationMillis = entry.key
                if (durationMillis > minDurationMsExclusive &&
                    (maxDurationMsInclusive == null || durationMillis <= maxDurationMsInclusive)
                ) {
                    Log.d(
                        RumConstants.OTEL_RUM_LOG_TAG,
                        "* Jank frame detected: $durationMillis ms. ${entry.value} times",
                    )
                }
            }
        }

        if (frameCount > 0) {
            val thresholdMs = (threshold * 1000.0).toLong()
            PulseLogger.logWarn(RumConstants.OTEL_RUM_LOG_TAG) {
                "sdk.jank.detected frame_count=$frameCount period_s=$periodSeconds threshold_ms=$thresholdMs activity=$activityName"
            }
            val eventBuilder = eventLogger.logRecordBuilder()
            val attributes =
                Attributes
                    .builder()
                    .put(FRAME_COUNT, frameCount)
                    .put(PERIOD, periodSeconds)
                    .put(THRESHOLD, threshold)
                    .build()
            eventBuilder
                .setEventName("app.jank")
                .setAllAttributes(attributes)
                .emit()
        }
    }
}

/** Frames in (minExclusive, maxInclusive]; maxInclusive null means no upper bound (frozen only). */
private fun countFramesInDurationRange(
    histogram: Map<Int, Int>,
    minDurationMsExclusive: Int,
    maxDurationMsInclusive: Int?,
): Long =
    histogram.entries.sumOf { (durationMillis, count) ->
        if (durationMillis > minDurationMsExclusive &&
            (maxDurationMsInclusive == null || durationMillis <= maxDurationMsInclusive)
        ) {
            count.toLong()
        } else {
            0L
        }
    }
