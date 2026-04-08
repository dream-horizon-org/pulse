/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.compose.click

import android.os.SystemClock
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseAttributes.ClickTypeValues
import com.pulse.semconv.PulseDeviceAttributes
import com.pulse.utils.PulseOtelUtils
import io.opentelemetry.android.instrumentation.click.ClickEventBuffer
import io.opentelemetry.android.instrumentation.click.PendingClick
import io.opentelemetry.android.instrumentation.click.RageConfig
import io.opentelemetry.android.instrumentation.click.RageEvent
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import java.util.concurrent.TimeUnit

/**
 * Handles all click event emission for the Compose click instrumentation:
 * buffers taps, detects rage clusters, and emits good / dead / rage events.
 *
 * Kept separate from [ComposeClickEventGenerator] so that Compose-node traversal logic
 * and emission/rage-detection logic have clear, independent boundaries.
 */
internal class ComposeClickEventEmitter(
    private val eventLogger: Logger,
    private val densityScale: Float = 1f,
    rageConfig: RageConfig = RageConfig(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    // Buffer owns onRage and onEmit so the delayed-emission Handler Runnable can fire without a call-site callback.
    private val clickEventBuffer =
        ClickEventBuffer(
            densityScale = densityScale,
            rageConfig = rageConfig,
            onRage = ::emitRageClick,
            onEmit = ::emitIndividualClick,
            clock = clock,
        )

    fun currentTimeMs(): Long = clock()

    /** Records a tap and emits the appropriate event(s). */
    fun process(pending: PendingClick) {
        clickEventBuffer.record(pending)
    }

    /** Flushes buffered clicks and any pending rage event. Call on activity pause. */
    fun flush() {
        clickEventBuffer.flush()
    }

    private fun emitIndividualClick(click: PendingClick): Unit {
        if (click.hasTarget) emitGoodClick(click) else emitDeadClick(click)
    }

    private fun emitGoodClick(click: PendingClick): Unit {
        val record =
            eventLogger
                .logRecordBuilder()
                .setTimestamp(click.tapEpochMs, TimeUnit.MILLISECONDS)
                .setEventName(VIEW_CLICK_EVENT_NAME)
                .setAttribute(APP_WIDGET_NAME, click.widgetName.orEmpty())
                .setAttribute(APP_WIDGET_ID, click.widgetId.orEmpty())
                .setAttribute(APP_SCREEN_COORDINATE_X, click.xInPx.toLong())
                .setAttribute(APP_SCREEN_COORDINATE_Y, click.yInPx.toLong())
                .setAttribute(PulseAttributes.CLICK_TYPE, ClickTypeValues.GOOD)
                .applyViewportAttrs(click.viewportWidthPx, click.viewportHeightPx, click.xInPx, click.yInPx)
        click.clickContext?.let { record.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, it) }
        record.emit()
        PulseOtelUtils.logDebug(LOG_TAG) {
            "click.type=good x=${click.xInPx.toLong()} y=${click.yInPx.toLong()} " +
                "name=${click.widgetName} id=${click.widgetId} context=${click.clickContext}"
        }
    }

    private fun emitDeadClick(click: PendingClick): Unit {
        eventLogger
            .logRecordBuilder()
            .setTimestamp(click.tapEpochMs, TimeUnit.MILLISECONDS)
            .setEventName(VIEW_CLICK_EVENT_NAME)
            .setAttribute(APP_SCREEN_COORDINATE_X, click.xInPx.toLong())
            .setAttribute(APP_SCREEN_COORDINATE_Y, click.yInPx.toLong())
            .setAttribute(PulseAttributes.CLICK_TYPE, ClickTypeValues.DEAD)
            .applyViewportAttrs(click.viewportWidthPx, click.viewportHeightPx, click.xInPx, click.yInPx)
            .emit()
        PulseOtelUtils.logDebug(LOG_TAG) {
            "click.type=dead x=${click.xInPx.toLong()} y=${click.yInPx.toLong()}"
        }
    }

    private fun emitRageClick(rage: RageEvent): Unit {
        val clickType = if (rage.hasTarget) ClickTypeValues.GOOD else ClickTypeValues.DEAD
        val record =
            eventLogger
                .logRecordBuilder()
                .setTimestamp(rage.tapEpochMs, TimeUnit.MILLISECONDS)
                .setEventName(VIEW_CLICK_EVENT_NAME)
                .setAttribute(APP_SCREEN_COORDINATE_X, rage.xInPx.toLong())
                .setAttribute(APP_SCREEN_COORDINATE_Y, rage.yInPx.toLong())
                .setAttribute(PulseAttributes.CLICK_TYPE, clickType)
                .setAttribute(PulseAttributes.CLICK_IS_RAGE, true)
                .setAttribute(PulseAttributes.CLICK_RAGE_COUNT, rage.count.toLong())
                .applyViewportAttrs(rage.viewportWidthPx, rage.viewportHeightPx, rage.xInPx, rage.yInPx)
        rage.widgetName?.let { record.setAttribute(APP_WIDGET_NAME, it) }
        rage.widgetId?.let { record.setAttribute(APP_WIDGET_ID, it) }
        rage.clickContext?.let { record.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, it) }
        record.emit()
        PulseOtelUtils.logDebug(LOG_TAG) {
            "click.type=${if (rage.hasTarget) "good" else "dead"} is_rage=true count=${rage.count} " +
                "x=${rage.xInPx.toLong()} y=${rage.yInPx.toLong()} " +
                "name=${rage.widgetName} id=${rage.widgetId} context=${rage.clickContext}"
        }
    }

    companion object {
        private const val LOG_TAG = "PulseClick"
    }

    private fun LogRecordBuilder.applyViewportAttrs(
        vpWidthPx: Int,
        vpHeightPx: Int,
        x: Float,
        y: Float,
    ): LogRecordBuilder = apply {
        if (vpWidthPx > 0 && vpHeightPx > 0) {
            val effectiveDensity = if (densityScale > 0f) densityScale else 1f
            setAttribute(PulseDeviceAttributes.DEVICE_SCREEN_WIDTH, (vpWidthPx / effectiveDensity).toLong())
            setAttribute(PulseDeviceAttributes.DEVICE_SCREEN_HEIGHT, (vpHeightPx / effectiveDensity).toLong())
            setAttribute(PulseAttributes.APP_SCREEN_COORDINATE_NX, x.toDouble() / vpWidthPx)
            setAttribute(PulseAttributes.APP_SCREEN_COORDINATE_NY, y.toDouble() / vpHeightPx)
        }
    }
}
