/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.instrumentation.compose.click

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import java.util.LinkedList
import kotlin.sequences.generateSequence

/** Result of finding a tap target, includes the LayoutNode and the Owner view for semantics lookup. */
internal data class TapTarget(
    val node: LayoutNode,
    val ownerView: View,
)

/**
 * Result of [ComposeTapTargetDetector.findTapResult] — one view-tree traversal instead of two.
 *
 * - [NotFound] — no ComposeView (Owner) contains the tap point; belongs to ViewClickEventGenerator.
 * - [Found]     — tap landed inside a ComposeView; [target] is the hit node or null (dead click).
 */
internal sealed class ComposeFindResult {
    object NotFound : ComposeFindResult()

    class Found(
        val target: TapTarget?,
    ) : ComposeFindResult()
}

internal class ComposeTapTargetDetector(
    private val composeLayoutNodeUtil: ComposeLayoutNodeUtil,
) {
    fun nodeToName(node: LayoutNode): String =
        try {
            getNodeName(node) ?: node.semanticsId.toString()
        } catch (_: Throwable) {
            node.semanticsId.toString()
        }

    /**
     * Extracts human-readable label/context for the clicked element (button text, content description, etc.).
     * Checks the node, its descendants, then ancestors (Material3 uses ChildSemanticsNodeElement internally;
     * the parent often has merged semantics like ContentDescription from the Text child).
     */
    fun getNodeContext(node: LayoutNode): String? =
        try {
            getNodeContextFromNode(node)
                ?: getTextFromDescendants(node)
                ?: getTextFromAncestors(node)
        } catch (_: Throwable) {
            null
        }

    // Reused across viewContainsPoint calls to avoid IntArray allocation per hit-test.
    // Safe: all methods run on the UI thread.
    private val tempLocation = IntArray(2)

    /**
     * Single view-tree traversal that both checks ownership and finds the tap target.
     *
     * Returns [ComposeFindResult.NotFound] when no ComposeView (Owner) contains [x, y] — the tap
     * belongs to ViewClickEventGenerator. Returns [ComposeFindResult.Found] otherwise, with a
     * non-null [TapTarget] for good clicks and null for dead clicks inside Compose.
     */
    fun findTapResult(
        decorView: View,
        x: Float,
        y: Float,
    ): ComposeFindResult {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var isTapInCompose = false
        var target: TapTarget? = null
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.add(view.getChildAt(index))
                }
                if (view is Owner && viewContainsPoint(view, x, y)) {
                    isTapInCompose = true
                    try {
                        findTapTargetNode(view as Owner, x, y)?.let { node ->
                            target = TapTarget(node, view)
                        }
                    } catch (_: Throwable) {
                        // Relies on visibility suppression to access internal fields/classes;
                        // any runtime exception must be caught here.
                    }
                }
            }
        }
        return if (isTapInCompose) ComposeFindResult.Found(target) else ComposeFindResult.NotFound
    }

    private fun viewContainsPoint(
        view: View,
        x: Float,
        y: Float,
    ): Boolean {
        view.getLocationInWindow(tempLocation)
        return x >= tempLocation[0] && x <= tempLocation[0] + view.width &&
            y >= tempLocation[1] && y <= tempLocation[1] + view.height
    }

    private fun findTapTargetNode(
        owner: Owner,
        x: Float,
        y: Float,
    ): LayoutNode? {
        val queue = LinkedList<LayoutNode>()
        queue.addFirst(owner.root)
        var target: LayoutNode? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isPlaced && hitTest(node, x, y)) {
                target = node
            }

            queue.addAll(node.zSortedChildren.asMutableList())
        }
        return target
    }

    /**
     * Uses the Compose SemanticsNode tree with coordinate-based hit test.
     * The semantics tree has merged accessibility labels (e.g. Button's ContentDescription from Text child).
     * This is more reliable than LayoutNode traversal for Material3 Button/Card.
     */
    fun getContextFromSemanticsTree(
        ownerView: View,
        x: Float,
        y: Float,
    ): String? {
        return try {
            val semanticsOwner = (ownerView as? RootForTest)?.semanticsOwner ?: return null
            val semanticsNodes = semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            val point = Offset(x, y)
            semanticsNodes
                .filter { it.config.contains(SemanticsActions.OnClick) && it.boundsInWindow.contains(point) }
                .minByOrNull { it.boundsInWindow.area() }
                ?.let { extractLabelFromSemanticsNode(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun Rect.area(): Float = width * height

    private fun extractLabelFromSemanticsNode(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        node.config.prioritizedClickLabel()

    private fun SemanticsConfiguration.prioritizedClickLabel(): String? =
        listOf(
            getOrNull(SemanticsActions.OnClick)?.label?.takeIf { it.isNotBlank() },
            firstNonBlankListString(getOrNull(SemanticsProperties.ContentDescription)),
            firstNonBlankListString(getOrNull(SemanticsProperties.Text)),
        ).firstOrNull { it != null }

    private fun firstNonBlankListString(list: List<*>?): String? {
        val first = list?.firstOrNull() ?: return null
        return first.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun isValidClickTarget(node: LayoutNode): Boolean {
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is SemanticsModifier) {
                with(modifier.semanticsConfiguration) {
                    if (contains(SemanticsActions.OnClick)) {
                        return true
                    }
                }
            } else {
                val className = modifier::class.qualifiedName
                if (
                    className == CLASS_NAME_CLICKABLE_ELEMENT ||
                    className == CLASS_NAME_COMBINED_CLICKABLE_ELEMENT ||
                    className == CLASS_NAME_TOGGLEABLE_ELEMENT ||
                    className == CLASS_NAME_SELECTABLE_ELEMENT ||
                    className == CLASS_NAME_TRI_STATE_TOGGLEABLE_ELEMENT
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun getNodeName(node: LayoutNode): String? {
        var className: String? = null
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is SemanticsModifier) {
                with(modifier.semanticsConfiguration) {
                    val onClickSemanticsConfiguration = getOrNull(SemanticsActions.OnClick)
                    if (onClickSemanticsConfiguration != null) {
                        val accessibilityActionLabel = onClickSemanticsConfiguration.label
                        if (accessibilityActionLabel != null) {
                            return accessibilityActionLabel
                        }
                    }

                    val contentDescriptionSemanticsConfiguration =
                        getOrNull(SemanticsProperties.ContentDescription)
                    if (contentDescriptionSemanticsConfiguration != null) {
                        val contentDescription =
                            contentDescriptionSemanticsConfiguration.getOrNull(0)
                        if (contentDescription != null) {
                            return contentDescription
                        }
                    }
                }
            } else {
                className = modifier::class.qualifiedName
            }
        }

        return className
    }

    private fun getNodeContextFromNode(node: LayoutNode): String? {
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is SemanticsModifier) {
                modifier.semanticsConfiguration.prioritizedClickLabel()?.let { return it }
            }
        }
        return null
    }

    private fun getTextFromDescendants(node: LayoutNode): String? {
        for (child in node.zSortedChildren.asMutableList()) {
            getNodeContextFromNode(child)?.let { return it }
            getTextFromDescendants(child)?.let { return it }
        }
        return null
    }

    /**
     * Material3 Button uses ChildSemanticsNodeElement as an internal wrapper; the actual
     * Text (e.g. "Add to Cart", "Go shopping") lives in its subtree. The direct parent
     * is usually the Button; searching its descendants finds the button text.
     * For grandparents+, only use direct semantics to avoid sibling content (e.g. product name).
     */
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private fun getTextFromAncestors(node: LayoutNode): String? {
        val firstParent = node.parent ?: return null
        getNodeContextFromNode(firstParent)?.let { return it }
        getTextFromDescendants(firstParent)?.let { return it }
        return generateSequence(firstParent.parent) { it.parent }
            .firstNotNullOfOrNull { getNodeContextFromNode(it) }
    }

    private fun hitTest(
        node: LayoutNode,
        x: Float,
        y: Float,
    ): Boolean {
        val isBounded =
            composeLayoutNodeUtil.getLayoutNodeBoundsInWindow(node)?.let { bounds ->
                x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
            } == true

        return isBounded && isValidClickTarget(node)
    }

    companion object {
        private const val CLASS_NAME_CLICKABLE_ELEMENT =
            "androidx.compose.foundation.ClickableElement"
        private const val CLASS_NAME_COMBINED_CLICKABLE_ELEMENT =
            "androidx.compose.foundation.CombinedClickableElement"
        private const val CLASS_NAME_TOGGLEABLE_ELEMENT =
            "androidx.compose.foundation.selection.ToggleableElement"

        // Modifier.selectable() — used by Material3 Tab and NavigationBarItem
        private const val CLASS_NAME_SELECTABLE_ELEMENT =
            "androidx.compose.foundation.selection.SelectableElement"

        // Modifier.triStateToggleable() — used by indeterminate checkboxes
        private const val CLASS_NAME_TRI_STATE_TOGGLEABLE_ELEMENT =
            "androidx.compose.foundation.selection.TriStateToggleableElement"
    }
}
