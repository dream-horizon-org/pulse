/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.view.click

import android.os.SystemClock
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseAttributes.ClickTypeValues
import com.pulse.semconv.PulseDeviceAttributes
import io.opentelemetry.android.instrumentation.click.ClickEventBuffer
import io.opentelemetry.android.instrumentation.click.PendingClick
import io.opentelemetry.android.instrumentation.click.RageConfig
import io.opentelemetry.android.instrumentation.click.RageEvent
import io.opentelemetry.android.instrumentation.click.common.PulseWidgetClickLogHelper
import io.opentelemetry.android.instrumentation.view.click.internal.VIEW_CLICK_EVENT_NAME
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import java.util.concurrent.TimeUnit

/**
 * Handles all click event emission for the View click instrumentation:
 * buffers taps, detects rage clusters, and emits good / dead / rage events.
 *
 * Kept separate from [ViewClickEventGenerator] so that view-traversal logic
 * and emission/rage-detection logic have clear, independent boundaries.
 */
internal class ViewClickEventEmitter(
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

    private fun emitIndividualClick(click: PendingClick) {
        val clickType = if (click.hasTarget) ClickTypeValues.GOOD else ClickTypeValues.DEAD
        val record =
            eventLogger
                .logRecordBuilder()
                .setTimestamp(click.tapEpochMs, TimeUnit.MILLISECONDS)
                .setEventName(VIEW_CLICK_EVENT_NAME)
                .setAttribute(APP_SCREEN_COORDINATE_X, click.xInPx.toLong())
                .setAttribute(APP_SCREEN_COORDINATE_Y, click.yInPx.toLong())
                .setAttribute(PulseAttributes.CLICK_TYPE, clickType)
                .applyViewportAttrs(click.viewportWidthPx, click.viewportHeightPx, click.xInPx, click.yInPx)
        click.widgetName?.let { record.setAttribute(APP_WIDGET_NAME, it) }
        click.widgetId?.let { record.setAttribute(APP_WIDGET_ID, it) }
        click.clickContext?.let { record.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, it) }
        record.emit()
        PulseWidgetClickLogHelper.logClick(
            clickType = clickType,
            xInPx = click.xInPx,
            yInPx = click.yInPx,
            widgetName = click.widgetName,
            widgetId = click.widgetId,
            clickContext = click.clickContext,
        )
    }

    private fun emitRageClick(rage: RageEvent) {
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
        PulseWidgetClickLogHelper.logClick(
            clickType = clickType,
            xInPx = rage.xInPx,
            yInPx = rage.yInPx,
            widgetName = rage.widgetName,
            widgetId = rage.widgetId,
            clickContext = rage.clickContext,
            isRage = true,
            rageCount = rage.count,
        )
    }

    private fun LogRecordBuilder.applyViewportAttrs(
        vpWidthPx: Int,
        vpHeightPx: Int,
        x: Float,
        y: Float,
    ): LogRecordBuilder =
        apply {
            if (vpWidthPx > 0 && vpHeightPx > 0) {
                val effectiveDensity = if (densityScale > 0f) densityScale else 1f
                setAttribute(PulseDeviceAttributes.DEVICE_SCREEN_WIDTH, (vpWidthPx / effectiveDensity).toLong())
                setAttribute(PulseDeviceAttributes.DEVICE_SCREEN_HEIGHT, (vpHeightPx / effectiveDensity).toLong())
                setAttribute(PulseAttributes.APP_SCREEN_COORDINATE_NX, x.toDouble() / vpWidthPx)
                setAttribute(PulseAttributes.APP_SCREEN_COORDINATE_NY, y.toDouble() / vpHeightPx)
            }
        }
}
