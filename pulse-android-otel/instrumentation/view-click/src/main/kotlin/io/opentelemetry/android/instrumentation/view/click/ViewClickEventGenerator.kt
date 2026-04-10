/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.view.click

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.android.instrumentation.WindowCallbackUnwrap
import io.opentelemetry.android.instrumentation.click.PendingClick
import io.opentelemetry.android.instrumentation.click.RageConfig
import io.opentelemetry.android.instrumentation.click.common.ClickEventEmitter
import io.opentelemetry.android.instrumentation.click.common.PulseClickGestureTracker
import io.opentelemetry.android.instrumentation.view.click.internal.VIEW_CLICK_EVENT_NAME
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.common.Clock
import java.lang.ref.WeakReference
import java.util.LinkedList

internal class ViewClickEventGenerator(
    eventLogger: Logger,
    private val isContextEnrichmentEnabled: Boolean = true,
    densityScale: Float = 1f,
    rageConfig: RageConfig = RageConfig(),
    clock: Clock = Clock.getDefault(),
) {
    internal val clickEmitter = ClickEventEmitter(eventLogger, VIEW_CLICK_EVENT_NAME, densityScale, rageConfig, clock)

    private var windowRef: WeakReference<Window>? = null
    private val gestureTracker = PulseClickGestureTracker()

    private val viewCoordinates = IntArray(2)

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

                val tapX = motionEvent.x
                val tapY = motionEvent.y
                val window = windowRef?.get() ?: return
                val decorView = window.decorView
                val hitResult = findTargetForTap(decorView, tapX, tapY)

                // ComposeView found during traversal — let ComposeClickEventGenerator handle it.
                if (hitResult === HitResult.DeferToCompose) return

                val target = (hitResult as? HitResult.Hit)?.view

                val clickContext =
                    if (isContextEnrichmentEnabled && target != null) {
                        PulseAttributes.AppClickContext.buildContext(getViewContextLabel(target))
                    } else {
                        null
                    }

                clickEmitter.process(
                    PendingClick(
                        xPx = tapX,
                        yPx = tapY,
                        timestampMs = clickEmitter.currentMonotonicTimeMs(),
                        tapEpochMs = System.currentTimeMillis(),
                        hasTarget = target != null,
                        widgetName = target?.let { viewToName(it) },
                        widgetId = target?.run { id.toString() },
                        clickContext = clickContext,
                        viewportWidthPx = decorView.width,
                        viewportHeightPx = decorView.height,
                    ),
                )
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureTracker.onActionCancel()
            }
        }
    }

    /** Flushes buffered clicks and unwraps the window callback. Call on activity pause. */
    fun stopTracking() {
        clickEmitter.flush()
        windowRef?.get()?.run {
            callback = WindowCallbackUnwrap.fullyUnwrap(callback)
        }
        windowRef = null
    }

    private sealed class HitResult {
        /**
         * [view] == null means dead click (miss).
         */
        class Hit(
            val view: View?,
        ) : HitResult()

        object DeferToCompose : HitResult()
    }

    private fun findTargetForTap(
        decorView: View,
        x: Float,
        y: Float,
    ): HitResult {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var target: View? = null

        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            // ComposeView found — Compose instrumentation owns this tap.
            if (isJetpackComposeView(view)) return HitResult.DeferToCompose
            if (isValidClickTarget(view)) target = view
            if (view is ViewGroup && addChildrenToQueue(view, x, y, queue)) return HitResult.DeferToCompose
        }
        return HitResult.Hit(target)
    }

    private fun isValidClickTarget(view: View): Boolean = (view.isClickable || view.isLongClickable) && view.isVisible

    /**
     * Adds hit-tested children to the queue. Returns true if a ComposeView child was hit,
     * meaning the tap belongs to Compose instrumentation and traversal should stop.
     */
    private fun addChildrenToQueue(
        view: ViewGroup,
        x: Float,
        y: Float,
        stack: LinkedList<View>,
    ): Boolean {
        if (!view.isVisible) return false
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (!hitTest(child, x, y)) continue
            if (isJetpackComposeView(child)) return true // tap lands inside ComposeView → defer
            stack.add(child)
        }
        return false
    }

    private fun hitTest(
        view: View,
        x: Float,
        y: Float,
    ): Boolean {
        view.getLocationInWindow(viewCoordinates)
        val vx = viewCoordinates[0]
        val vy = viewCoordinates[1]
        return !(x < vx || x > vx + view.width || y < vy || y > vy + view.height)
    }

    private fun viewToName(view: View): String =
        try {
            view.resources?.getResourceEntryName(view.id) ?: view.id.toString()
        } catch (_: Throwable) {
            view.id.toString()
        }

    /**
     * Extracts human-readable label/context for the clicked View (button text, content description, etc.).
     * For ViewGroups (Card, etc.): merges segments with truncation at segment boundaries.
     * For ImageView/ImageButton: uses contentDescription only (no sibling heuristic).
     */
    private fun getViewContextLabel(view: View): String? =
        try {
            if (view is ViewGroup) getLabelFromCard(view) else getLabelFromView(view)
        } catch (_: Throwable) {
            null
        }

    /**
     * For EditText: never use text (contains typed PII/passwords). Use contentDescription or hint only.
     * For other TextViews: use text, then contentDescription.
     */
    private fun getLabelFromView(view: View): String? =
        if (view is EditText) {
            view.contentDescription.nonBlankOrNull() ?: view.hint.nonBlankOrNull()
        } else {
            (view as? TextView)?.text.nonBlankOrNull() ?: view.contentDescription.nonBlankOrNull()
        }

    private fun CharSequence?.nonBlankOrNull(): String? = this?.run { toString().takeIf { it.isNotBlank() } }

    /**
     * For cards (ViewGroups): collects and merges up to [MAX_CARD_LABEL_SEGMENTS] text segments.
     * Truncates at segment boundaries (drops segments from end) to avoid cutting mid-word.
     */
    private fun getLabelFromCard(card: ViewGroup): String? {
        val segments = mutableListOf<String>()
        getLabelFromView(card)?.let { segments.add(it) }
        collectLabelsFromDescendants(
            group = card,
            out = segments,
            maxSegments = MAX_CARD_LABEL_SEGMENTS,
            depth = 0,
            maxDepth = 4,
        )
        return trimSegmentsToMaxLength(segments.take(MAX_CARD_LABEL_SEGMENTS), MAX_CARD_LABEL_CHAR_LENGTH)
            ?.takeIf { it.isNotBlank() }
    }

    private fun collectLabelsFromDescendants(
        group: ViewGroup,
        out: MutableList<String>,
        maxSegments: Int,
        depth: Int,
        maxDepth: Int,
    ) {
        if (depth >= maxDepth || out.size >= maxSegments) return
        for (i in 0 until group.childCount) {
            if (out.size >= maxSegments) return
            val child = group.getChildAt(i)
            if (isJetpackComposeView(child) || !isViewActiveInHierarchyForLabel(child)) continue
            getLabelFromView(child)?.let { label ->
                if (label.isNotBlank() && label !in out) out.add(label)
            }
            (child as? ViewGroup)?.let {
                collectLabelsFromDescendants(it, out, maxSegments, depth + 1, maxDepth)
            }
        }
    }

    private fun isViewActiveInHierarchyForLabel(view: View): Boolean =
        view.isAttachedToWindow &&
            view.isShown &&
            view.isLaidOut &&
            view.width > 0 &&
            view.height > 0

    /**
     * Truncates by dropping whole segments from the end until under maxLength.
     */
    private fun trimSegmentsToMaxLength(
        segments: List<String>,
        maxLength: Int,
    ): String? {
        if (segments.isEmpty()) return null
        val result = segments.joinToString(CARD_LABEL_DELIMITER)
        if (result.length <= maxLength) return result
        var dropFrom = segments.size
        while (dropFrom > 1) {
            val trimmed = segments.take(dropFrom - 1).joinToString(CARD_LABEL_DELIMITER)
            if (trimmed.length <= maxLength) return trimmed
            dropFrom--
        }
        return segments.first().take(maxLength)
    }

    private fun isJetpackComposeView(view: View): Boolean = view::class.java.name.startsWith("androidx.compose.ui.platform.ComposeView")

    private val View.isVisible: Boolean get() = visibility == View.VISIBLE

    private companion object {
        private const val MAX_CARD_LABEL_SEGMENTS = 5
        private const val MAX_CARD_LABEL_CHAR_LENGTH = 200
        private const val CARD_LABEL_DELIMITER = " | "
    }
}
