package com.pulse.android.sdk.replay.internal.capture

import android.graphics.Rect
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.compose.ui.semantics.getAllSemanticsNodes
import com.pulse.android.sdk.replay.ReplayConstants
import com.pulse.android.sdk.replay.ui.PulseReplayMaskKey
import com.pulse.android.sdk.replay.SessionReplayConfig

/**
 * Collects rects that should be masked (drawn over) in screenshot mode.
 * Respects [SessionReplayConfig] (maskAllTextInputs, maskAllImages) and per-view tags:
 * - tag or contentDescription containing [MASK_TAG] → mask this view
 * - [UNMASK_TAG] → do not mask (override global for this view)
 */
internal object MaskingCollector {

    private val MASK_TAG = ReplayConstants.MASK_TAG
    private val UNMASK_TAG = ReplayConstants.UNMASK_TAG

    private val passwordInputTypes = listOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    )

    /**
     * Fill [maskableWidgets] with global visible rects for views that should be masked.
     * Returns false if traversal was aborted (e.g. screen changed); [onDrawCalled] can be checked by caller.
     */
    fun findMaskableWidgets(
        view: View,
        config: SessionReplayConfig,
        maskableWidgets: MutableList<Rect>,
        visitedViews: MutableSet<Int> = mutableSetOf(),
        onDrawCalled: () -> Boolean,
        logger: (String) -> Unit,
    ): Boolean {
        val viewId = System.identityHashCode(view)
        if (viewId in visitedViews) return true
        visitedViews.add(viewId)

        var walkChildren = false

        when {
            view.isComposeView(logger) -> {
                findMaskableComposeWidgets(view, config, maskableWidgets, logger)
                walkChildren = true
            }
            view.isNoCapture(config) -> {
                view.globalVisibleRectSafe(logger)?.let { maskableWidgets.add(it) }
            }
            view is TextView -> {
                var maskIt = false
                val viewText = view.text?.toString()
                if (!viewText.isNullOrEmpty()) maskIt = view.shouldMaskTextView(config)
                val hint = view.hint?.toString()
                if (!maskIt && !hint.isNullOrEmpty()) maskIt = view.shouldMaskTextView(config)
                if (maskIt) {
                    view.getTextAreaGlobalVisibleRect(logger)?.let { maskableWidgets.add(it) }
                        ?: view.globalVisibleRectSafe(logger)?.let { maskableWidgets.add(it) }
                }
            }
            view is Spinner -> {
                if (view.shouldMaskSpinner(config)) {
                    view.globalVisibleRectSafe(logger)?.let { maskableWidgets.add(it) }
                }
            }
            view is ImageView -> {
                if (view.shouldMaskImage(config)) {
                    view.globalVisibleRectSafe(logger)?.let { maskableWidgets.add(it) }
                }
            }
            view is WebView -> {
                if (view.isAnyInputSensitive(config)) {
                    view.globalVisibleRectSafe(logger)?.let { maskableWidgets.add(it) }
                }
            }
            view is ViewGroup && view.childCount > 0 -> walkChildren = true
        }

        if (walkChildren && view is ViewGroup && view.childCount > 0) {
            for (i in 0 until view.childCount) {
                if (onDrawCalled()) {
                    logger("Session Replay screenshot discarded due to screen changes")
                    return false
                }
                val child = view.getChildAt(i) ?: continue
                if (!child.isVisible(logger)) continue
                if (!findMaskableWidgets(child, config, maskableWidgets, visitedViews, onDrawCalled, logger)) {
                    return false
                }
            }
        }
        return true
    }

    private fun View.isNoCapture(config: SessionReplayConfig): Boolean =
        (tag as? String)?.lowercase()?.contains(MASK_TAG) == true ||
            contentDescription?.toString()?.lowercase()?.contains(MASK_TAG) == true

    private fun View.isTextInputSensitive(config: SessionReplayConfig): Boolean =
        config.maskAllTextInputs || (tag as? String)?.lowercase()?.contains(MASK_TAG) == true ||
            contentDescription?.toString()?.lowercase()?.contains(MASK_TAG) == true

    private fun View.isAnyInputSensitive(config: SessionReplayConfig): Boolean =
        isTextInputSensitive(config) || config.maskAllImages

    private fun TextView.shouldMaskTextView(config: SessionReplayConfig): Boolean =
        isTextInputSensitive(config) || passwordInputTypes.contains(inputType - 1)

    private fun ImageView.shouldMaskImage(config: SessionReplayConfig): Boolean =
        isTextInputSensitive(config) && drawable != null

    private fun Spinner.shouldMaskSpinner(config: SessionReplayConfig): Boolean =
        isTextInputSensitive(config)

    private fun View.globalVisibleRectSafe(logger: (String) -> Unit): Rect? {
        return try {
            if (!isViewStateStableForMatrixOperations(logger)) null
            else {
                val rect = Rect()
                getGlobalVisibleRect(rect, null)
                rect
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun TextView.getTextAreaGlobalVisibleRect(logger: (String) -> Unit): Rect? {
        val fullRect = globalVisibleRectSafe(logger) ?: return null
        val shouldAdjust = this is EditText || this is android.widget.Button
        if (!shouldAdjust) return fullRect
        val left = fullRect.left + compoundPaddingLeft
        val top = fullRect.top + compoundPaddingTop
        val right = fullRect.right - compoundPaddingRight
        val bottom = fullRect.bottom - compoundPaddingBottom
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else fullRect
    }

    private fun View.isVisible(logger: (String) -> Unit): Boolean = ScreenshotCapture.isVisible(this, logger)

    private fun View.isViewStateStableForMatrixOperations(logger: (String) -> Unit): Boolean =
        ScreenshotCapture.isViewStateStable(this, logger)

    private fun View.isComposeView(logger: (String) -> Unit): Boolean {
        if (!isComposeAvailable) return false
        return javaClass.name.contains("AndroidComposeView")
    }

    private val isComposeAvailable: Boolean by lazy {
        try {
            Class.forName("androidx.compose.ui.platform.AndroidComposeView")
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Compose: collect mask rects from semantics. Must be called on main thread.
     */
    private fun findMaskableComposeWidgets(
        view: View,
        config: SessionReplayConfig,
        maskableWidgets: MutableList<Rect>,
        logger: (String) -> Unit,
    ) {
        try {
            val semanticsOwner = (view as? androidx.compose.ui.node.RootForTest)?.semanticsOwner ?: run {
                logger("View is not a RootForTest: $view")
                return
            }
            val semanticsNodes = semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
            val maskKey = PulseReplayMaskKey
            for (node in semanticsNodes) {
                val hasMaskModifier = node.config.contains(maskKey)
                val isNoCapture = hasMaskModifier && node.config[maskKey] == true
                when {
                    isNoCapture -> maskableWidgets.add(node.boundsInWindow.toAndroidRect())
                    !hasMaskModifier -> {
                        val hasText = node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)
                        val hasEditableText = node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText)
                        val hasPassword = node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Password)
                        val hasImage = node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
                        when {
                            (hasText || hasEditableText) && (config.maskAllTextInputs || hasPassword) ->
                                maskableWidgets.add(node.boundsInWindow.toAndroidRect())
                            hasImage && config.maskAllImages ->
                                maskableWidgets.add(node.boundsInWindow.toAndroidRect())
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger("Session Replay findMaskableComposeWidgets failed: $e")
        }
    }
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRect(): Rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
