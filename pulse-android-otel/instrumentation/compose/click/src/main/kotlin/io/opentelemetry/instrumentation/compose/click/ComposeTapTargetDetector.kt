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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.pulse.semconv.PulseAttributes
import java.util.LinkedList
import kotlin.sequences.generateSequence

/** Result of finding a tap target, includes the LayoutNode and the Owner view for semantics lookup. */
internal data class TapTarget(
    val node: LayoutNode,
    val ownerView: View,
)

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
     * Returns element hint (image, button, chip) when the composable type can be inferred from
     * semantics Role or modifier class names.
     */
    fun getElementHintForNode(node: LayoutNode): String? =
        try {
            var roleHint: String? = null
            var hasChipModifier = false
            for (info in node.getModifierInfo()) {
                val modifier = info.modifier
                if (modifier is SemanticsModifier) {
                    modifier.semanticsConfiguration.getOrNull(SemanticsProperties.Role)?.let { role ->
                        roleHint =
                            when (role) {
                                Role.Image -> PulseAttributes.AppClickContext.ELEMENT_IMAGE
                                Role.Button -> PulseAttributes.AppClickContext.ELEMENT_BUTTON
                                else -> roleHint
                            }
                    }
                }
                val className = modifier::class.qualifiedName ?: ""
                if (className.contains("Chip")) hasChipModifier = true
            }
            when {
                hasChipModifier -> PulseAttributes.AppClickContext.ELEMENT_CHIP
                roleHint != null -> roleHint
                else -> null
            }
        } catch (_: Throwable) {
            null
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

    fun findTapTarget(
        decorView: View,
        x: Float,
        y: Float,
    ): TapTarget? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)

        var target: TapTarget? = null
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.add(view.getChildAt(index))
                }
                if (view is Owner) {
                    try {
                        findTapTargetNode(view as Owner, x, y)?.let { node ->
                            target = TapTarget(node, view)
                        }
                    } catch (_: Throwable) {
                        // We rely on visibility suppression to access internal fields and
                        // classes any runtime exception must be caught here.
                    }
                }
            }
        }
        return target
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

    private fun extractLabelFromSemanticsNode(node: androidx.compose.ui.semantics.SemanticsNode): String? {
        val config = node.config
        config
            .getOrNull(SemanticsActions.OnClick)
            ?.label
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        config
            .getOrNull(SemanticsProperties.Text)
            ?.firstOrNull()
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
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
                    className == CLASS_NAME_TOGGLEABLE_ELEMENT
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
                with(modifier.semanticsConfiguration) {
                    val onClickLabel = getOrNull(SemanticsActions.OnClick)?.label
                    if (!onClickLabel.isNullOrBlank()) return onClickLabel

                    val contentDesc = getOrNull(SemanticsProperties.ContentDescription)?.getOrNull(0)
                    if (contentDesc != null) {
                        val contentStr = contentDesc.toString().trim()
                        if (contentStr.isNotBlank()) return contentStr
                    }

                    val textList = getOrNull(SemanticsProperties.Text)
                    if (!textList.isNullOrEmpty()) {
                        val firstStr = textList.first().toString().trim()
                        if (firstStr.isNotBlank()) return firstStr
                    }
                }
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
    }
}
