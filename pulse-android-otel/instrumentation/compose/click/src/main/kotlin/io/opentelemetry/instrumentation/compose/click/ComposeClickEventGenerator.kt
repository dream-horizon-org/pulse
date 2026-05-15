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
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.android.instrumentation.WindowCallbackUnwrap
import io.opentelemetry.android.instrumentation.click.PendingClick
import io.opentelemetry.android.instrumentation.click.RageConfig
import io.opentelemetry.android.instrumentation.click.common.ClickEventEmitter
import io.opentelemetry.android.instrumentation.click.common.PulseClickGestureTracker
import io.opentelemetry.android.instrumentation.click.common.findScrollOffset
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.common.Clock
import java.lang.ref.WeakReference

internal class ComposeClickEventGenerator(
    eventLogger: Logger,
    private val isContextEnrichmentEnabled: Boolean = true,
    private val composeLayoutNodeUtil: ComposeLayoutNodeUtil = ComposeLayoutNodeUtil(),
    private val composeTapTargetDetector: ComposeTapTargetDetector = ComposeTapTargetDetector(composeLayoutNodeUtil),
    densityScale: Float = 1f,
    rageConfig: RageConfig = RageConfig(),
    clock: Clock = Clock.getDefault(),
) {
    internal val clickEmitter = ClickEventEmitter(eventLogger, VIEW_CLICK_EVENT_NAME, densityScale, rageConfig, clock)

    private var windowRef: WeakReference<Window>? = null
    private val gestureTracker = PulseClickGestureTracker()

    fun startTracking(window: Window) {
        windowRef = WeakReference(window)
        gestureTracker.setTouchSlopPixels(ViewConfiguration.get(window.context).scaledTouchSlop)
        val currentCallback = window.callback ?: return
        window.callback = WindowCallbackWrapper(currentCallback, this)
    }

    fun generateClick(motionEvent: MotionEvent) {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureTracker.onActionDown(motionEvent)
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureTracker.onActionUp(motionEvent)) return
                if (!ComposeClasspathProbe.isComposeUiPresent()) return

                val decorView = windowRef?.get()?.decorView ?: return
                val windowX = motionEvent.x
                val windowY = motionEvent.y

                val findResult = composeTapTargetDetector.findTapResult(decorView, windowX, windowY)
                if (findResult !is ComposeFindResult.Found) return
                val tapTarget = findResult.target

                val tapEpochMs = System.currentTimeMillis()

                val vpWidthPx = decorView.width
                val vpHeightPx = decorView.height
                val (nativeScrollX, nativeScrollY) = findScrollOffset(findResult.ownerView)
                val (composeScrollX, composeScrollY) = composeTapTargetDetector.getScrollOffset(findResult.ownerView, windowX, windowY)
                val scrollXPx = nativeScrollX + composeScrollX
                val scrollYPx = nativeScrollY + composeScrollY

                val contentXPx = windowX + scrollXPx
                val contentYPx = windowY + scrollYPx

                val pending =
                    tapTarget?.let { target ->
                        val node = target.node
                        val clickContext =
                            if (isContextEnrichmentEnabled) {
                                val label =
                                    composeTapTargetDetector.getContextFromSemanticsTree(
                                        target.ownerView,
                                        windowX,
                                        windowY,
                                    ) ?: composeTapTargetDetector.getNodeContext(node)
                                PulseAttributes.AppClickContext.buildContext(label)
                            } else {
                                null
                            }
                        PendingClick(
                            xPx = contentXPx,
                            yPx = contentYPx,
                            timestampMs = clickEmitter.currentMonotonicTimeMs(),
                            tapEpochMs = tapEpochMs,
                            hasTarget = true,
                            widgetName = composeTapTargetDetector.nodeToName(node),
                            widgetId = node.semanticsId.toString(),
                            clickContext = clickContext,
                            viewportWidthPx = vpWidthPx,
                            viewportHeightPx = vpHeightPx,
                        )
                    } ?: PendingClick(
                        xPx = contentXPx,
                        yPx = contentYPx,
                        timestampMs = clickEmitter.currentMonotonicTimeMs(),
                        tapEpochMs = tapEpochMs,
                        hasTarget = false,
                        viewportWidthPx = vpWidthPx,
                        viewportHeightPx = vpHeightPx,
                    )

                clickEmitter.process(pending)
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureTracker.onActionCancel()
            }
        }
    }

    fun stopTracking() {
        // Flush buffered clicks before unwrapping so no taps are silently dropped on pause.
        clickEmitter.flush()
        windowRef?.get()?.run {
            callback = WindowCallbackUnwrap.fullyUnwrap(callback)
        }
        windowRef = null
    }

}
