/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.instrumentation.compose.click

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.ui.node.LayoutNode
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.android.instrumentation.WindowCallbackUnwrap
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
    private val composeLayoutNodeUtil: ComposeLayoutNodeUtil = ComposeLayoutNodeUtil(),
    private val composeTapTargetDetector: ComposeTapTargetDetector = ComposeTapTargetDetector(composeLayoutNodeUtil),
) {
    private var windowRef: WeakReference<Window>? = null

    fun startTracking(window: Window) {
        windowRef = WeakReference(window)
        val currentCallback: Window.Callback? = window.callback
        window.callback = currentCallback?.let { WindowCallbackWrapper(currentCallback, this) }
    }

    fun generateClick(motionEvent: MotionEvent) {
        windowRef?.get()?.let { window ->
            if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                val (windowX, windowY) = motionEventToWindowCoordinates(window.decorView, motionEvent)
                composeTapTargetDetector.findTapTarget(window.decorView, windowX, windowY)?.let { tapTarget ->
                    // Only emit screen click when we own this screen (Compose-based); avoids duplicate
                    // app.screen.click when both View and Compose instrumentations are active
                    val layoutNode = tapTarget.node
                    val attributes = createNodeAttributes(layoutNode)
                    val label = composeTapTargetDetector.getContextFromSemanticsTree(tapTarget.ownerView, windowX, windowY)
                        ?: composeTapTargetDetector.getNodeContext(layoutNode)
                    val elementHint = composeTapTargetDetector.getElementHintForNode(layoutNode)
                    val ctx = PulseAttributes.AppClickContext
                    val baseScreenContext = label?.let { ctx.build(it, ctx.TYPE_SCREEN, ctx.SOURCE_COMPOSE) }
                        ?: ctx.build(ctx.TYPE_SCREEN, ctx.SOURCE_COMPOSE)
                    val baseWidgetContext = label?.let { ctx.build(it, ctx.TYPE_WIDGET, ctx.SOURCE_COMPOSE) }
                        ?: ctx.build(ctx.TYPE_WIDGET, ctx.SOURCE_COMPOSE)
                    val screenContext = elementHint?.let { ctx.withElement(baseScreenContext, it) } ?: baseScreenContext
                    val widgetContext = elementHint?.let { ctx.withElement(baseWidgetContext, it) } ?: baseWidgetContext
                    createEvent(APP_SCREEN_CLICK_EVENT_NAME)
                        .setAttribute(APP_SCREEN_COORDINATE_X, windowX.toLong())
                        .setAttribute(APP_SCREEN_COORDINATE_Y, windowY.toLong())
                        .setAttribute(PulseAttributes.APP_CLICK_CONTEXT, screenContext)
                        .emit()
                    Log.d(CLICK_LOG_TAG, "app.screen.click: x=$windowX y=$windowY context=$screenContext")

                    createEvent(VIEW_CLICK_EVENT_NAME)
                        .setAllAttributes(attributes)
                        .setAttribute(PulseAttributes.APP_CLICK_CONTEXT, widgetContext)
                        .emit()

                    Log.d(CLICK_LOG_TAG, "app.widget.click: name=${attributes.get(APP_WIDGET_NAME)} context=$widgetContext widgetId=${attributes.get(APP_WIDGET_ID)}")
                }
            }
        }
    }

    /**
     * Converts MotionEvent coordinates to window space for hit-testing.
     * getX/getY are view-relative; boundsInWindow uses window space. Using raw screen coords
     * and subtracting the decor view's screen position yields consistent window coordinates.
     */
    private fun motionEventToWindowCoordinates(decorView: View, event: MotionEvent): Pair<Float, Float> {
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

    private fun createNodeAttributes(node: LayoutNode): Attributes {
        val builder = Attributes.builder()
        builder.put(APP_WIDGET_NAME, composeTapTargetDetector.nodeToName(node))
        builder.put(APP_WIDGET_ID, node.semanticsId.toString())

        composeLayoutNodeUtil.getLayoutNodePositionInWindow(node)?.let {
            builder.put(APP_SCREEN_COORDINATE_X, it.x.toLong())
            builder.put(APP_SCREEN_COORDINATE_Y, it.y.toLong())
        }
        return builder.build()
    }
}
