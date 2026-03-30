/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.view.click

import android.os.SystemClock
import android.util.Log
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseAttributes.ClickTypeValues
import io.opentelemetry.android.instrumentation.click.ClickEventBuffer
import io.opentelemetry.android.instrumentation.click.PendingClick
import io.opentelemetry.android.instrumentation.click.RageConfig
import io.opentelemetry.android.instrumentation.click.RageEvent
import io.opentelemetry.android.instrumentation.view.click.internal.VIEW_CLICK_EVENT_NAME
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
    densityScale: Float = 1f,
    rageConfig: RageConfig = RageConfig(),
    clock: () -> Long = SystemClock::elapsedRealtime,
) {
    // Buffer owns onRage and onEmit so the delayed-emission Handler Runnable can fire without a call-site callback.
    internal val clickEventBuffer =
        ClickEventBuffer(
            densityScale = densityScale,
            rageConfig = rageConfig,
            onRage = ::emitRageClick,
            onEmit = ::emitIndividualClick,
            clock = clock,
        )

    fun currentTimeMs(): Long = clickEventBuffer.currentTimeMs()

    /** Records a tap and emits the appropriate event(s). */
    fun process(pending: PendingClick) {
        clickEventBuffer.record(pending)
    }

    /** Flushes buffered clicks and any pending rage event. Call on activity pause. */
    fun flush() {
        clickEventBuffer.flush()
    }

    // region Private emission helpers

    private fun emitIndividualClick(click: PendingClick) {
        if (click.hasTarget) emitGoodClick(click) else emitDeadClick(click)
    }

    private fun emitGoodClick(click: PendingClick) {
        val record =
            eventLogger
                .logRecordBuilder()
                .setTimestamp(click.tapEpochMs, TimeUnit.MILLISECONDS)
                .setEventName(VIEW_CLICK_EVENT_NAME)
                .setAttribute(APP_WIDGET_NAME, click.widgetName.orEmpty())
                .setAttribute(APP_WIDGET_ID, click.widgetId.orEmpty())
                .setAttribute(APP_SCREEN_COORDINATE_X, click.x.toLong())
                .setAttribute(APP_SCREEN_COORDINATE_Y, click.y.toLong())
                .setAttribute(PulseAttributes.CLICK_TYPE, ClickTypeValues.GOOD)
        click.clickContext?.let { record.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, it) }
        record.emit()
        Log.d(
            CLICK_LOG_TAG,
            "app.widget.click: x=${click.x.toLong()} y=${click.y.toLong()} " +
                "name=${click.widgetName ?: "null"} context=${click.clickContext ?: "null"} id=${click.widgetId ?: "null"}",
        )
    }

    private fun emitDeadClick(click: PendingClick) {
        eventLogger
            .logRecordBuilder()
            .setTimestamp(click.tapEpochMs, TimeUnit.MILLISECONDS)
            .setEventName(VIEW_CLICK_EVENT_NAME)
            .setAttribute(APP_SCREEN_COORDINATE_X, click.x.toLong())
            .setAttribute(APP_SCREEN_COORDINATE_Y, click.y.toLong())
            .setAttribute(PulseAttributes.CLICK_TYPE, ClickTypeValues.DEAD)
            .emit()
        Log.d(CLICK_LOG_TAG, "app.widget.click (dead): x=${click.x.toLong()} y=${click.y.toLong()}")
    }

    private fun emitRageClick(rage: RageEvent) {
        val clickType = if (rage.hasTarget) ClickTypeValues.GOOD else ClickTypeValues.DEAD
        val record =
            eventLogger
                .logRecordBuilder()
                .setTimestamp(rage.tapEpochMs, TimeUnit.MILLISECONDS)
                .setEventName(VIEW_CLICK_EVENT_NAME)
                .setAttribute(APP_SCREEN_COORDINATE_X, rage.x.toLong())
                .setAttribute(APP_SCREEN_COORDINATE_Y, rage.y.toLong())
                .setAttribute(PulseAttributes.CLICK_TYPE, clickType)
                .setAttribute(PulseAttributes.CLICK_IS_RAGE, true)
                .setAttribute(PulseAttributes.CLICK_RAGE_COUNT, rage.count.toLong())
        rage.widgetName?.let { record.setAttribute(APP_WIDGET_NAME, it) }
        rage.widgetId?.let { record.setAttribute(APP_WIDGET_ID, it) }
        rage.clickContext?.let { record.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, it) }
        record.emit()
        Log.d(
            CLICK_LOG_TAG,
            "app.widget.click (rage/$clickType): x=${rage.x.toLong()} y=${rage.y.toLong()} " +
                "count=${rage.count} name=${rage.widgetName ?: "null"} context=${rage.clickContext ?: "null"}",
        )
    }

    // endregion

    private companion object {
        private const val CLICK_LOG_TAG = "PulseClick"
    }
}
