/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.view.click

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.android.instrumentation.WindowCallbackUnwrap
import io.opentelemetry.android.instrumentation.view.click.internal.VIEW_CLICK_EVENT_NAME
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import java.lang.ref.WeakReference
import java.util.LinkedList

class ViewClickEventGenerator(
    private val eventLogger: Logger,
    private val isContextEnrichmentEnabled: Boolean = true,
) {
    private var windowRef: WeakReference<Window>? = null
    private var touchSlopPx: Int = 0
    private var lastDownX: Float = 0f
    private var lastDownY: Float = 0f
    private var hasValidDown: Boolean = false

    private val viewCoordinates = IntArray(2)

    fun startTracking(window: Window) {
        windowRef = WeakReference(window)
        touchSlopPx = ViewConfiguration.get(window.context).scaledTouchSlop
        val currentCallback: Window.Callback? = window.callback
        window.callback = currentCallback?.let { WindowCallbackWrapper(currentCallback, this) }
    }

    fun generateClick(motionEvent: MotionEvent) {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownX = motionEvent.x
                lastDownY = motionEvent.y
                hasValidDown = true
            }
            MotionEvent.ACTION_UP -> {
                if (!hasValidDown) return
                hasValidDown = false
                val dx = motionEvent.x - lastDownX
                val dy = motionEvent.y - lastDownY
                val distanceSq = dx * dx + dy * dy
                if (distanceSq > touchSlopPx * touchSlopPx) return // scroll: movement exceeds touch slop
                windowRef?.get()?.let { window ->
                    findTargetForTap(window.decorView, motionEvent.x, motionEvent.y)?.let { view ->
                        val tapX = motionEvent.x.toLong()
                        val tapY = motionEvent.y.toLong()
                        val attributes = createViewAttributes(view, tapX, tapY)
                        val widgetClickRecord =
                            createEvent(VIEW_CLICK_EVENT_NAME)
                                .setAllAttributes(attributes)
                        if (isContextEnrichmentEnabled) {
                            val label = getViewContextLabel(view)
                            PulseAttributes.AppClickContext.buildContext(label)?.let { ctxStr ->
                                widgetClickRecord.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, ctxStr)
                            }
                            val widgetNameForLog = attributes.get(APP_WIDGET_NAME).orEmpty()
                            val widgetIdForLog = attributes.get(APP_WIDGET_ID).orEmpty()
                            Log.d(
                                CLICK_LOG_TAG,
                                "app.widget.click: x=$tapX y=$tapY name=$widgetNameForLog " +
                                    "context=${label.orEmpty()} widgetId=$widgetIdForLog",
                            )
                        } else {
                            val widgetNameForLog = attributes.get(APP_WIDGET_NAME).orEmpty()
                            val widgetIdForLog = attributes.get(APP_WIDGET_ID).orEmpty()
                            Log.d(
                                CLICK_LOG_TAG,
                                "app.widget.click: x=$tapX y=$tapY name=$widgetNameForLog widgetId=$widgetIdForLog (no app.click.context)",
                            )
                        }
                        widgetClickRecord.emit()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                hasValidDown = false
            }
        }
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

    private fun createViewAttributes(
        view: View,
        tapX: Long,
        tapY: Long,
    ): Attributes {
        val builder = Attributes.builder()
        builder.put(APP_WIDGET_NAME, viewToName(view))
        builder.put(APP_WIDGET_ID, view.id.toString())
        builder.put(APP_SCREEN_COORDINATE_X, tapX)
        builder.put(APP_SCREEN_COORDINATE_Y, tapY)
        return builder.build()
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
            if (view is ViewGroup) {
                getLabelFromCard(view)
            } else {
                getLabelFromView(view)
            }
        } catch (_: Throwable) {
            null
        }

    /**
     * For EditText: never use text (contains typed PII/passwords). Use contentDescription or hint only.
     * For other TextViews: use text, then contentDescription.
     */
    private fun getLabelFromView(view: View): String? =
        if (view is EditText) {
            view.contentDescription.nonBlankOrNull()
                ?: view.hint.nonBlankOrNull()
        } else {
            (view as? TextView)?.text.nonBlankOrNull()
                ?: view.contentDescription.nonBlankOrNull()
        }

    private fun CharSequence?.nonBlankOrNull(): String? {
        val s = this?.toString() ?: return null
        return s.takeIf { it.isNotBlank() }
    }

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
        return trimSegmentsToMaxLength(segments.take(MAX_CARD_LABEL_SEGMENTS), MAX_CARD_LABEL_LENGTH)
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Truncates by dropping whole segments from the end until under maxLength.
     * Avoids cutting mid-word or mid-segment (e.g. "Match: Ayodhya... | Ayodhya Pr").
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
            if (isJetpackComposeView(child)) continue
            getLabelFromView(child)?.let { label ->
                if (label.isNotBlank() && label !in out) out.add(label)
            }
            (child as? ViewGroup)?.let {
                collectLabelsFromDescendants(it, out, maxSegments, depth + 1, maxDepth)
            }
        }
    }

    private companion object {
        private const val CLICK_LOG_TAG = "PulseClick"
        private const val MAX_CARD_LABEL_SEGMENTS = 5
        private const val MAX_CARD_LABEL_LENGTH = 200
        private const val CARD_LABEL_DELIMITER = " | "
    }

    private fun findTargetForTap(
        decorView: View,
        x: Float,
        y: Float,
    ): View? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var target: View? = null

        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (isJetpackComposeView(view)) {
                return null
            }

            if (isValidClickTarget(view)) {
                target = view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, queue)
            }
        }
        return target
    }

    private fun isValidClickTarget(view: View): Boolean = view.isClickable && view.isVisible

    private fun handleViewGroup(
        view: ViewGroup,
        x: Float,
        y: Float,
        stack: LinkedList<View>,
    ) {
        if (!view.isVisible) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (hitTest(child, x, y) && !isJetpackComposeView(child)) {
                stack.add(child)
            }
        }
    }

    private fun hitTest(
        view: View,
        x: Float,
        y: Float,
    ): Boolean {
        view.getLocationInWindow(viewCoordinates)
        val vx = viewCoordinates[0]
        val vy = viewCoordinates[1]

        val w = view.width
        val h = view.height
        return !(x < vx || x > vx + w || y < vy || y > vy + h)
    }

    private fun isJetpackComposeView(view: View): Boolean = view::class.java.name.startsWith("androidx.compose.ui.platform.ComposeView")

    private val View.isVisible: Boolean
        get() = visibility == View.VISIBLE
}
