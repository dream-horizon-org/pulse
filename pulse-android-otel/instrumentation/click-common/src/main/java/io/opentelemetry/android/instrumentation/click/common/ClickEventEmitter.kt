/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click.common

import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseAttributes.ClickTypeValues
import com.pulse.semconv.PulseDeviceAttributes
import io.opentelemetry.android.instrumentation.click.ClickEventBuffer
import io.opentelemetry.android.instrumentation.click.PendingClick
import io.opentelemetry.android.instrumentation.click.RageConfig
import io.opentelemetry.android.instrumentation.click.RageEvent
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import java.util.concurrent.TimeUnit

/**
 * Shared click event emitter: buffers taps, detects rage clusters, and emits good / dead / rage
 * events. Used by both View and Compose click instrumentations.
 *
 * @param eventLogger   OTel logger to emit events against.
 * @param eventName     OTel event name for emitted log records.
 * @param densityScale  [android.util.DisplayMetrics.density] — used for dp → px conversion.
 * @param rageConfig    Rage-detection parameters.
 * @param clock         Injectable clock; defaults to the system clock.
 */
class ClickEventEmitter(
    private val eventLogger: Logger,
    private val eventName: String,
    private val densityScale: Float = 1f,
    rageConfig: RageConfig = RageConfig(),
    private val clock: Clock = Clock.getDefault(),
) {
    private val clickEventBuffer =
        ClickEventBuffer(
            densityScale = densityScale,
            rageConfig = rageConfig,
            onRage = ::emitRageClick,
            onEmit = ::emitIndividualClick,
        )

    /**
     * Returns the current monotonic time in milliseconds. Used for rage-detection timing only —
     * not for OTel event timestamps (those use wall-clock time).
     */
    fun currentMonotonicTimeMs(): Long = clock.nanoTime() / 1_000_000

    /** Records a tap and emits the appropriate event(s). */
    fun process(pending: PendingClick) {
        clickEventBuffer.record(pending)
    }

    /** Flushes buffered clicks and any pending rage event. Call on activity pause. */
    fun flush() {
        clickEventBuffer.flush()
    }

    private fun emitIndividualClick(click: PendingClick) {
        emitClick(
            hasTarget = click.hasTarget,
            xPx = click.xPx,
            yPx = click.yPx,
            tapEpochMs = click.tapEpochMs,
            widgetName = click.widgetName,
            widgetId = click.widgetId,
            clickContext = click.clickContext,
            viewportWidthPx = click.viewportWidthPx,
            viewportHeightPx = click.viewportHeightPx,
        )
    }

    private fun emitRageClick(rage: RageEvent) {
        emitClick(
            hasTarget = rage.hasTarget,
            xPx = rage.xPx,
            yPx = rage.yPx,
            tapEpochMs = rage.tapEpochMs,
            widgetName = rage.widgetName,
            widgetId = rage.widgetId,
            clickContext = rage.clickContext,
            viewportWidthPx = rage.viewportWidthPx,
            viewportHeightPx = rage.viewportHeightPx,
            rageCount = rage.count,
        ) {
            setAttribute(PulseAttributes.CLICK_IS_RAGE, true)
            setAttribute(PulseAttributes.CLICK_RAGE_COUNT, rage.count.toLong())
        }
    }

    private inline fun emitClick(
        hasTarget: Boolean,
        xPx: Float,
        yPx: Float,
        tapEpochMs: Long,
        widgetName: String?,
        widgetId: String?,
        clickContext: String?,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        rageCount: Int? = null,
        extraAttrs: LogRecordBuilder.() -> Unit = {},
    ) {
        val clickType = if (hasTarget) ClickTypeValues.GOOD else ClickTypeValues.DEAD
        val record =
            eventLogger
                .logRecordBuilder()
                .setTimestamp(tapEpochMs, TimeUnit.MILLISECONDS)
                .setEventName(eventName)
                .setAttribute(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.TOUCH)
                .setAttribute(APP_SCREEN_COORDINATE_X, xPx.toLong())
                .setAttribute(APP_SCREEN_COORDINATE_Y, yPx.toLong())
                .setAttribute(PulseAttributes.CLICK_TYPE, clickType)
                .applyViewportAttrs(viewportWidthPx, viewportHeightPx, xPx, yPx)
        widgetName?.let { record.setAttribute(APP_WIDGET_NAME, it) }
        widgetId?.let { record.setAttribute(APP_WIDGET_ID, it) }
        clickContext?.let { record.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, it) }
        record.extraAttrs()
        record.emit()
        PulseWidgetClickLogHelper.logClick(
            clickType = clickType,
            xPx = xPx,
            yPx = yPx,
            widgetName = widgetName,
            widgetId = widgetId,
            clickContext = clickContext,
            rageCount = rageCount,
        )
    }

    private fun LogRecordBuilder.applyViewportAttrs(
        vpWidthPx: Int,
        vpHeightPx: Int,
        xPx: Float,
        yPx: Float,
    ): LogRecordBuilder =
        apply {
            if (vpWidthPx > 0 && vpHeightPx > 0) {
                val effectiveDensity = if (densityScale > 0f) densityScale else 1f
                setAttribute(PulseDeviceAttributes.DEVICE_SCREEN_WIDTH, (vpWidthPx / effectiveDensity).toLong())
                setAttribute(PulseDeviceAttributes.DEVICE_SCREEN_HEIGHT, (vpHeightPx / effectiveDensity).toLong())
                setAttribute(PulseAttributes.APP_SCREEN_COORDINATE_NX, xPx.toDouble() / vpWidthPx)
                setAttribute(PulseAttributes.APP_SCREEN_COORDINATE_NY, yPx.toDouble() / vpHeightPx)
            }
        }
}
