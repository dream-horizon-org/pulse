package com.pulse.android.sdk.replay.internal.capture

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.core.view.isEmpty
import com.pulse.android.sdk.replay.ImagePrivacy
import com.pulse.android.sdk.replay.ReplayConstants
import com.pulse.android.sdk.replay.SessionReplayConfig
import com.pulse.android.sdk.replay.TextAndInputPrivacy
import com.pulse.android.sdk.replay.ui.PulseReplayMaskKey
import com.pulse.android.sdk.replay.ui.hasPrivacyMaskTag
import com.pulse.android.sdk.replay.ui.hasPrivacyUnmaskTag

/**
 * Collects rects that should be masked (drawn over) in screenshot mode.
 *
 * Masking priority (highest to lowest):
 * 1. Per-view instance: tag/extension ([ReplayConstants.MASK_TAG], [ReplayConstants.UNMASK_TAG],
 *    [pulseReplayMask][com.pulse.android.sdk.replay.ui.pulseReplayMask],
 *    [pulseReplayUnmask][com.pulse.android.sdk.replay.ui.pulseReplayUnmask])
 * 2. Per-view class: [SessionReplayConfig.maskViewClasses] / [SessionReplayConfig.unmaskViewClasses]
 * 3. Global config: [SessionReplayConfig.textAndInputPrivacy], [SessionReplayConfig.imagePrivacy]
 *
 * ViewGroup propagation: a force-masked parent masks all children unless a child has an explicit unmask override.
 */
internal object MaskingCollector {

    private val MASK_TAG = ReplayConstants.MASK_TAG
    private val UNMASK_TAG = ReplayConstants.UNMASK_TAG

    /**
     * Fill [maskableWidgets] with window-relative rects for views that should be masked.
     * Returns false if traversal was aborted (e.g. screen changed).
     *
     * All rects added to [maskableWidgets] are in window coordinates (matching the
     * PixelCopy bitmap). View-based rects are converted from screen coordinates using
     * [screenToWindowOffset]; Compose rects from `boundsInWindow` are already window-relative.
     */
    fun findMaskableWidgets(
        view: View,
        config: SessionReplayConfig,
        maskableWidgets: MutableList<Rect>,
        visitedViews: MutableSet<Int> = mutableSetOf(),
        onDrawCalled: () -> Boolean,
        logger: (String) -> Unit,
        parentForcedMask: Boolean = false,
        screenToWindowOffset: IntArray = computeScreenToWindowOffset(view),
    ): Boolean {
        val viewId = System.identityHashCode(view)
        if (viewId in visitedViews) return true
        visitedViews.add(viewId)

        if (view.isComposeView(logger)) {
            findMaskableComposeWidgets(view, config, maskableWidgets, logger)
            return walkChildren(view, config, maskableWidgets, visitedViews, onDrawCalled, logger, parentForcedMask = false, screenToWindowOffset)
        }

        val instanceDecision = view.resolveInstanceDecision()
        val classDecision = view.resolveClassDecision(config)

        val effectiveDecision = if (instanceDecision != MaskDecision.UNDECIDED) instanceDecision else classDecision
        var forceMaskChildren = parentForcedMask

        when {
            effectiveDecision == MaskDecision.UNMASK -> {
                forceMaskChildren = false
            }
            effectiveDecision == MaskDecision.MASK -> {
                view.windowVisibleRectSafe(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                forceMaskChildren = true
            }
            parentForcedMask -> {
                view.windowVisibleRectSafe(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                forceMaskChildren = true
            }
            else -> {
                applyTypeSpecificMasking(view, config, maskableWidgets, screenToWindowOffset, logger)
            }
        }

        return walkChildren(view, config, maskableWidgets, visitedViews, onDrawCalled, logger, forceMaskChildren, screenToWindowOffset)
    }

    private fun walkChildren(
        view: View,
        config: SessionReplayConfig,
        maskableWidgets: MutableList<Rect>,
        visitedViews: MutableSet<Int>,
        onDrawCalled: () -> Boolean,
        logger: (String) -> Unit,
        parentForcedMask: Boolean,
        screenToWindowOffset: IntArray,
    ): Boolean {
        if (view !is ViewGroup || view.isEmpty()) return true
        for (i in 0 until view.childCount) {
            if (onDrawCalled()) {
                logger("Session Replay screenshot discarded due to screen changes")
                return false
            }
            val child = view.getChildAt(i) ?: continue
            if (!child.isVisible(logger)) continue
            if (!findMaskableWidgets(child, config, maskableWidgets, visitedViews, onDrawCalled, logger, parentForcedMask, screenToWindowOffset)) {
                return false
            }
        }
        return true
    }

    // --- Per-view instance decision (priority 1) ---

    private fun View.resolveInstanceDecision(): MaskDecision {
        if (hasPrivacyUnmaskTag()) return MaskDecision.UNMASK
        if (hasPrivacyMaskTag()) return MaskDecision.MASK

        val tagStr = (tag as? String)?.lowercase()
        if (tagStr != null) {
            if (tagStr.contains(UNMASK_TAG)) return MaskDecision.UNMASK
            if (tagStr.contains(MASK_TAG)) return MaskDecision.MASK
        }

        val cd = contentDescription?.toString()?.lowercase()
        if (cd != null) {
            if (cd.contains(UNMASK_TAG)) return MaskDecision.UNMASK
            if (cd.contains(MASK_TAG)) return MaskDecision.MASK
        }

        return MaskDecision.UNDECIDED
    }

    // --- Per-view class decision (priority 2) ---

    private fun View.resolveClassDecision(config: SessionReplayConfig): MaskDecision {
        if (config.unmaskViewClasses.isNotEmpty() && isInstanceOfRegistered(config.unmaskViewClasses)) {
            return MaskDecision.UNMASK
        }
        if (config.maskViewClasses.isNotEmpty() && isInstanceOfRegistered(config.maskViewClasses)) {
            return MaskDecision.MASK
        }
        return MaskDecision.UNDECIDED
    }

    private fun View.isInstanceOfRegistered(classNames: Set<String>): Boolean {
        var clazz: Class<*>? = javaClass
        while (clazz != null) {
            if (clazz.name in classNames) return true
            clazz = clazz.superclass
        }
        return false
    }

    // --- Type-specific masking (priority 3: global config) ---

    private fun applyTypeSpecificMasking(
        view: View,
        config: SessionReplayConfig,
        maskableWidgets: MutableList<Rect>,
        screenToWindowOffset: IntArray,
        logger: (String) -> Unit,
    ) {
        when (view) {
            is EditText -> {
                if (shouldMaskEditText(view, config)) {
                    view.getTextAreaWindowRect(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                        ?: view.windowVisibleRectSafe(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                }
            }
            is TextView -> {
                val hasContent = !view.text.isNullOrEmpty() || !view.hint.isNullOrEmpty()
                if (hasContent && shouldMaskTextView(view, config)) {
                    view.getTextAreaWindowRect(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                        ?: view.windowVisibleRectSafe(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                }
            }
            is Spinner -> {
                if (shouldMaskSpinner(config)) {
                    view.windowVisibleRectSafe(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                }
            }
            is ImageView -> {
                if (shouldMaskImage(view, config)) {
                    view.windowVisibleRectSafe(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                }
            }
            is WebView -> {
                if (shouldMaskWebView(config)) {
                    view.windowVisibleRectSafe(screenToWindowOffset, logger)?.let { maskableWidgets.add(it) }
                }
            }
        }
    }

    private fun shouldMaskEditText(view: EditText, config: SessionReplayConfig): Boolean {
        if (isPasswordInputType(view.inputType)) return true
        return when (config.textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_ALL -> true
            TextAndInputPrivacy.MASK_ALL_INPUTS -> true
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> isSensitiveInputType(view.inputType)
        }
    }

    private fun shouldMaskTextView(view: TextView, config: SessionReplayConfig): Boolean {
        if (isPasswordInputType(view.inputType)) return true
        return when (config.textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_ALL -> true
            TextAndInputPrivacy.MASK_ALL_INPUTS -> false
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> isSensitiveInputType(view.inputType)
        }
    }

    private fun shouldMaskSpinner(config: SessionReplayConfig): Boolean =
        when (config.textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_ALL -> true
            TextAndInputPrivacy.MASK_ALL_INPUTS -> true
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> false
        }

    private fun shouldMaskImage(view: ImageView, config: SessionReplayConfig): Boolean =
        config.imagePrivacy == ImagePrivacy.MASK_ALL && view.drawable != null

    private fun shouldMaskWebView(config: SessionReplayConfig): Boolean =
        config.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS ||
            config.imagePrivacy == ImagePrivacy.MASK_ALL

    // --- Input type classification (delegated to shared utility) ---

    private fun isPasswordInputType(inputType: Int): Boolean =
        InputTypeClassifier.isPasswordInputType(inputType)

    private fun isSensitiveInputType(inputType: Int): Boolean =
        InputTypeClassifier.isSensitiveInputType(inputType)

    // --- Rect helpers ---

    /**
     * Compute the delta between screen coordinates and window coordinates.
     * `getGlobalVisibleRect` returns screen coords; subtracting this offset
     * converts them to window (bitmap) coordinates.
     */
    private fun computeScreenToWindowOffset(view: View): IntArray {
        val screenLoc = IntArray(2)
        val windowLoc = IntArray(2)
        view.getLocationOnScreen(screenLoc)
        view.getLocationInWindow(windowLoc)
        return intArrayOf(screenLoc[0] - windowLoc[0], screenLoc[1] - windowLoc[1])
    }

    /**
     * Returns the visible rect of this view in **window** coordinates
     * (matching the PixelCopy bitmap's coordinate space).
     */
    private fun View.windowVisibleRectSafe(offset: IntArray, logger: (String) -> Unit): Rect? {
        return try {
            if (!isViewStateStableForMatrixOperations(logger)) null
            else {
                val rect = Rect()
                getGlobalVisibleRect(rect, null)
                rect.offset(-offset[0], -offset[1])
                rect
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun TextView.getTextAreaWindowRect(offset: IntArray, logger: (String) -> Unit): Rect? {
        val fullRect = windowVisibleRectSafe(offset, logger) ?: return null
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

    // --- Compose detection ---

    @Suppress("UNUSED_PARAMETER")
    private fun View.isComposeView(logger: (String) -> Unit): Boolean {
        if (!isComposeAvailable) return false
        return javaClass.name.contains("AndroidComposeView")
    }

    private val isComposeAvailable: Boolean by lazy {
        try {
            Class.forName("androidx.compose.ui.platform.AndroidComposeView")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private val isComposeRoleAvailable: Boolean by lazy {
        try {
            Class.forName("androidx.compose.ui.semantics.Role")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Compose: collect mask rects from semantics tree.
     *
     * Masking priority for Compose:
     * 1. [pulseReplayMask(true)][com.pulse.android.sdk.replay.ui.pulseReplayMask] -> mask
     * 2. [pulseReplayMask(false)][com.pulse.android.sdk.replay.ui.pulseReplayMask] -> unmask (skip auto-mask)
     * 3. Auto-mask text/editable text per [TextAndInputPrivacy]
     * 4. Auto-mask images per [ImagePrivacy] using [Role.Image] semantics
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

                when {
                    hasMaskModifier && node.config[maskKey] == true -> {
                        maskableWidgets.add(node.boundsInWindow.toAndroidRect())
                    }
                    hasMaskModifier -> {
                        // pulseReplayMask(false) -> explicit unmask, skip all auto-mask rules
                    }
                    else -> {
                        val hasText = node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)
                        val hasEditableText = node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText)
                        val hasPassword = node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Password)
                        val hasImage = resolveComposeHasImage(node)

                        if ((hasText || hasEditableText) && shouldMaskComposeText(config, hasEditableText, hasPassword)) {
                            maskableWidgets.add(node.boundsInWindow.toAndroidRect())
                        } else if (hasImage && config.imagePrivacy == ImagePrivacy.MASK_ALL) {
                            maskableWidgets.add(node.boundsInWindow.toAndroidRect())
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger("Session Replay findMaskableComposeWidgets failed: $e")
        }
    }

    private fun shouldMaskComposeText(
        config: SessionReplayConfig,
        hasEditableText: Boolean,
        hasPassword: Boolean,
    ): Boolean {
        if (hasPassword) return true
        return when (config.textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_ALL -> true
            TextAndInputPrivacy.MASK_ALL_INPUTS -> hasEditableText
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> hasPassword
        }
    }

    /**
     * Detect images via [Role.Image] semantics (Compose 1.5+).
     * Falls back to [ContentDescription] heuristic only when Role API is unavailable.
     */
    private fun resolveComposeHasImage(node: androidx.compose.ui.semantics.SemanticsNode): Boolean {
        if (isComposeRoleAvailable) {
            try {
                if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Role)) {
                    return node.config[androidx.compose.ui.semantics.SemanticsProperties.Role] ==
                        androidx.compose.ui.semantics.Role.Image
                }
                return false
            } catch (_: Throwable) {
                // fall through to heuristic
            }
        }
        return node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
    }

    private enum class MaskDecision { MASK, UNMASK, UNDECIDED }
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRect(): Rect =
    Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
