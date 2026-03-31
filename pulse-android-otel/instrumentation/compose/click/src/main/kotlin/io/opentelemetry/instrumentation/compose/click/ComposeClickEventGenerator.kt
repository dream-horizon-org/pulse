/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.instrumentation.compose.click

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Window
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.android.instrumentation.WindowCallbackUnwrap
import io.opentelemetry.android.instrumentation.click.PendingClick
import io.opentelemetry.android.instrumentation.click.RageConfig
import io.opentelemetry.android.instrumentation.click.common.PulseClickGestureTracker
import io.opentelemetry.api.logs.Logger
import java.lang.ref.WeakReference

internal class ComposeClickEventGenerator(
    eventLogger: Logger,
    private val isContextEnrichmentEnabled: Boolean = true,
    private val composeLayoutNodeUtil: ComposeLayoutNodeUtil = ComposeLayoutNodeUtil(),
    private val composeTapTargetDetector: ComposeTapTargetDetector = ComposeTapTargetDetector(composeLayoutNodeUtil),
    densityScale: Float = 1f,
    rageConfig: RageConfig = RageConfig(),
    clock: () -> Long = SystemClock::elapsedRealtime,
) {
    // All buffering, rage detection, and event emission is handled here.
    internal val clickEmitter = ComposeClickEventEmitter(eventLogger, densityScale, rageConfig, clock)

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

                val decorView = windowRef?.get()?.decorView ?: return
                // getX()/getY() from dispatchTouchEvent and boundsInWindow() both use window
                // coordinates, so no raw→window conversion is needed.
                val windowX = motionEvent.x
                val windowY = motionEvent.y

                // Single traversal: owns the tap only when it lands inside a ComposeView.
                // Returns NoCompose for taps outside Compose — ViewClickEventGenerator handles those.
                val findResult = composeTapTargetDetector.findTapResult(decorView, windowX, windowY)
                if (findResult is ComposeFindResult.NoCompose) return
                val tapTarget = (findResult as ComposeFindResult.Found).target

                // Capture wall-clock time once at tap time so all PendingClick paths share it.
                val tapEpochMs = System.currentTimeMillis()

                // Build PendingClick — widgetName/widgetId/clickContext only populated on a hit.
                val vpWidthPx = decorView.width
                val vpHeightPx = decorView.height
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
                            x = windowX,
                            y = windowY,
                            timestampMs = clickEmitter.currentTimeMs(),
                            tapEpochMs = tapEpochMs,
                            hasTarget = true,
                            widgetName = composeTapTargetDetector.nodeToName(node),
                            widgetId = node.semanticsId.toString(),
                            clickContext = clickContext,
                            viewportWidthPx = vpWidthPx,
                            viewportHeightPx = vpHeightPx,
                        )
                    } ?: PendingClick(
                        x = windowX,
                        y = windowY,
                        timestampMs = clickEmitter.currentTimeMs(),
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
