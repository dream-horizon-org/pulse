/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.instrumentation.compose.click

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import androidx.compose.ui.node.LayoutNode
import io.opentelemetry.android.instrumentation.WindowCallbackUnwrap
import io.opentelemetry.android.instrumentation.click.common.PulseClickGestureTracker
import io.opentelemetry.android.instrumentation.click.common.PulseWidgetClickLogHelper
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import java.lang.ref.WeakReference
import kotlin.let

internal class ComposeClickEventGenerator(
    private val eventLogger: Logger,
    private val isContextEnrichmentEnabled: Boolean = true,
    private val composeLayoutNodeUtil: ComposeLayoutNodeUtil = ComposeLayoutNodeUtil(),
    private val composeTapTargetDetector: ComposeTapTargetDetector = ComposeTapTargetDetector(composeLayoutNodeUtil),
) {
    private var windowRef: WeakReference<Window>? = null
    private val gestureTracker = PulseClickGestureTracker()

    fun startTracking(window: Window) {
        windowRef = WeakReference(window)
        gestureTracker.setTouchSlopPixels(ViewConfiguration.get(window.context).scaledTouchSlop)
        val currentCallback: Window.Callback? = window.callback
        window.callback = currentCallback?.let { WindowCallbackWrapper(currentCallback, this) }
    }

    fun generateClick(motionEvent: MotionEvent) {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureTracker.onActionDown(motionEvent)
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureTracker.onActionUp(motionEvent)) return
                windowRef?.get()?.let { window ->
                    val (windowX, windowY) = motionEventToWindowCoordinates(window.decorView, motionEvent)
                    composeTapTargetDetector.findTapTarget(window.decorView, windowX, windowY)?.let { tapTarget ->
                        val layoutNode = tapTarget.node
                        val tapX = windowX.toLong()
                        val tapY = windowY.toLong()
                        val attributes = createNodeAttributes(layoutNode, tapX, tapY)
                        val widgetClickRecord =
                            createEvent(VIEW_CLICK_EVENT_NAME)
                                .setAllAttributes(attributes)
                        val label =
                            if (isContextEnrichmentEnabled) {
                                composeTapTargetDetector.getContextFromSemanticsTree(
                                    tapTarget.ownerView,
                                    windowX,
                                    windowY,
                                ) ?: composeTapTargetDetector.getNodeContext(layoutNode)
                            } else {
                                null
                            }
                        PulseWidgetClickLogHelper.applyContextAndLogDebug(
                            record = widgetClickRecord,
                            attributes = attributes,
                            logCoordX = windowX.toString(),
                            logCoordY = windowY.toString(),
                            isContextEnrichmentEnabled = isContextEnrichmentEnabled,
                            label = label,
                            logTag = CLICK_LOG_TAG,
                        )
                        widgetClickRecord.emit()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureTracker.onActionCancel()
            }
        }
    }

    /**
     * Converts MotionEvent coordinates to window space for hit-testing.
     * getX/getY are view-relative; boundsInWindow uses window space. Using raw screen coords
     * and subtracting the decor view's screen position yields consistent window coordinates.
     */
    private fun motionEventToWindowCoordinates(
        decorView: View,
        event: MotionEvent,
    ): Pair<Float, Float> {
        val location = IntArray(2)
        decorView.getLocationOnScreen(location)
        return event.rawX - location[0] to event.rawY - location[1]
    }

    private companion object {
        private const val CLICK_LOG_TAG = "PulseClick"
    }

    fun stopTracking() {
        windowRef?.get()?.run {
            callback = WindowCallbackUnwrap.fullyUnwrap(callback)
        }
        windowRef = null
    }

    private fun createEvent(name: String): LogRecordBuilder =
        eventLogger
            .logRecordBuilder()
            .setEventName(name)

    private fun createNodeAttributes(
        node: LayoutNode,
        tapX: Long,
        tapY: Long,
    ): Attributes {
        val builder = Attributes.builder()
        builder.put(APP_WIDGET_NAME, composeTapTargetDetector.nodeToName(node))
        builder.put(APP_WIDGET_ID, node.semanticsId.toString())
        builder.put(APP_SCREEN_COORDINATE_X, tapX)
        builder.put(APP_SCREEN_COORDINATE_Y, tapY)
        return builder.build()
    }
}
