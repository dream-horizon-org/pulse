/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.instrumentation.compose.click

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ComposeTapTargetDetectorTest {
    lateinit var composeTapTargetDetector: ComposeTapTargetDetector

    @MockK
    lateinit var composeLayoutNodeUtil: ComposeLayoutNodeUtil

    @MockK
    lateinit var composeView: AndroidComposeView

    @MockK
    lateinit var semanticsModifier: SemanticsModifier

    @MockK
    lateinit var modifier: Modifier

    @MockK
    lateinit var semanticsConfiguration: SemanticsConfiguration

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        composeTapTargetDetector = ComposeTapTargetDetector(composeLayoutNodeUtil)
        // viewContainsPoint stubs — composeView is the root Owner passed as decorView in tests.
        every { composeView.getLocationInWindow(any()) } just runs
        every { composeView.width } returns 1000
        every { composeView.height } returns 1000
    }

    @Test
    fun `name from onClick label`() {
        val name = composeTapTargetDetector.nodeToName(createMockLayoutNode(clickable = true))
        assertThat(name).isEqualTo("click")
    }

    @Test
    fun `name from description`() {
        val name =
            composeTapTargetDetector.nodeToName(
                createMockLayoutNode(
                    clickable = true,
                    useDescription = true,
                ),
            )
        assertThat(name).isEqualTo("clickMe")
    }

    @Test
    fun `name from id on exception`() {
        val mockNode = mockkClass(LayoutNode::class)
        every { mockNode.semanticsId } returns 41
        every { mockNode.getModifierInfo() } throws RuntimeException("test")

        val name =
            composeTapTargetDetector.nodeToName(
                mockNode,
            )
        assertThat(name).isEqualTo("41")
    }

    @Test
    fun `name from id on null`() {
        val mockNode = mockkClass(LayoutNode::class)
        every { mockNode.semanticsId } returns 41
        every { mockNode.getModifierInfo() } returns emptyList()

        val name =
            composeTapTargetDetector.nodeToName(
                mockNode,
            )
        assertThat(name).isEqualTo("41")
    }

    @Test
    fun `return tap target when hit`() {
        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        every { composeView.childCount } returns 0

        val mockLayoutNode =
            createMockLayoutNode(
                targetX = motionEvent.x,
                targetY = motionEvent.y,
                hit = true,
                clickable = true,
                useDescription = true,
            )
        every { composeView.root } returns mockLayoutNode

        val result =
            composeTapTargetDetector.findTapResult(composeView, motionEvent.x, motionEvent.y)
        assertThat(result).isInstanceOf(ComposeFindResult.Found::class.java)
        val found = result as ComposeFindResult.Found
        assertThat(found.target?.node).isEqualTo(mockLayoutNode)
        assertThat(found.target?.ownerView).isEqualTo(composeView)
    }

    @Test
    fun `return null when no hit`() {
        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        every { composeView.childCount } returns 0

        val mockLayoutNode =
            createMockLayoutNode(
                targetX = motionEvent.x,
                targetY = motionEvent.y,
                hit = false,
                clickable = true,
                useDescription = true,
            )
        every { composeView.root } returns mockLayoutNode

        val result =
            composeTapTargetDetector.findTapResult(composeView, motionEvent.x, motionEvent.y)
        assertThat(result).isInstanceOf(ComposeFindResult.Found::class.java)
        assertThat((result as ComposeFindResult.Found).target).isNull()
    }

    // region scroll offset tests

    @Test
    fun `getScrollOffset returns 0,0 when ownerView is not RootForTest`() {
        val plainView = mockkClass(android.view.View::class)
        val result = composeTapTargetDetector.getScrollOffset(plainView, 100f, 100f)
        assertThat(result).isEqualTo(0 to 0)
    }

    @Test
    fun `resolveScrollValue returns exact offset when ScrollAxisRange value is positive`() {
        val scrollNode = mockkClass(androidx.compose.ui.semantics.SemanticsNode::class)
        val config = mockkClass(SemanticsConfiguration::class)
        every { scrollNode.config } returns config
        every { scrollNode.boundsInWindow } returns Rect(0f, 0f, 1000f, 1000f)

        val range = androidx.compose.ui.semantics.ScrollAxisRange(value = { 964f }, maxValue = { 2000f })
        every { config.contains(SemanticsProperties.VerticalScrollAxisRange) } returns true
        every { config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) } returns range

        val result = composeTapTargetDetector.resolveScrollValue(
            allNodes = listOf(scrollNode),
            point = Offset(100f, 100f),
            axisProperty = SemanticsProperties.VerticalScrollAxisRange,
            itemIndexSelector = { it.rowIndex },
        )
        assertThat(result).isEqualTo(964)
    }

    @Test
    fun `resolveScrollValue returns sentinel 1 when at LazyList item boundary but rowIndex above zero`() {
        val scrollNode = mockkClass(androidx.compose.ui.semantics.SemanticsNode::class)
        val scrollConfig = mockkClass(SemanticsConfiguration::class)
        every { scrollNode.config } returns scrollConfig
        every { scrollNode.boundsInWindow } returns Rect(0f, 0f, 1000f, 1000f)

        val rangeAtBoundary = androidx.compose.ui.semantics.ScrollAxisRange(value = { 0f }, maxValue = { 2000f })
        every { scrollConfig.contains(SemanticsProperties.VerticalScrollAxisRange) } returns true
        every { scrollConfig.getOrNull(SemanticsProperties.VerticalScrollAxisRange) } returns rangeAtBoundary

        // Visible list item with rowIndex = 2 → items above have been scrolled past
        val itemNode = mockkClass(androidx.compose.ui.semantics.SemanticsNode::class)
        val itemConfig = mockkClass(SemanticsConfiguration::class)
        every { itemNode.config } returns itemConfig
        every { itemNode.boundsInWindow } returns Rect(0f, 100f, 1000f, 200f)
        // itemNode is a list item, not a scroll container — filter passes it through.
        every { itemConfig.contains(SemanticsProperties.VerticalScrollAxisRange) } returns false
        every { itemConfig.getOrNull(SemanticsProperties.CollectionItemInfo) } returns
            CollectionItemInfo(rowIndex = 2, rowSpan = 1, columnIndex = 0, columnSpan = 1)

        val result = composeTapTargetDetector.resolveScrollValue(
            allNodes = listOf(scrollNode, itemNode),
            point = Offset(100f, 100f),
            axisProperty = SemanticsProperties.VerticalScrollAxisRange,
            itemIndexSelector = { it.rowIndex },
        )
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `resolveScrollValue returns 0 when no scroll container at tap point`() {
        val result = composeTapTargetDetector.resolveScrollValue(
            allNodes = emptyList(),
            point = Offset(100f, 100f),
            axisProperty = SemanticsProperties.VerticalScrollAxisRange,
            itemIndexSelector = { it.rowIndex },
        )
        assertThat(result).isEqualTo(0)
    }

    // endregion

    private fun createMockLayoutNode(
        targetX: Float = 0f,
        targetY: Float = 0f,
        hitOffset: IntArray = intArrayOf(10, 20),
        id: Int = 100,
        hit: Boolean = false,
        clickable: Boolean = false,
        useDescription: Boolean = false,
    ): LayoutNode {
        val mockNode = mockkClass(LayoutNode::class)
        every { mockNode.isPlaced } returns true

        val bounds =
            if (hit) {
                Rect(
                    left = targetX - hitOffset[0],
                    right = targetX + hitOffset[0],
                    top = targetY - hitOffset[1],
                    bottom = targetY + hitOffset[1],
                )
            } else {
                Rect(
                    left = targetX + hitOffset[0],
                    right = targetX + hitOffset[0],
                    top = targetY + hitOffset[1],
                    bottom = targetY + hitOffset[1],
                )
            }

        val mockModifierInfo = mockkClass(ModifierInfo::class)
        every { mockNode.getModifierInfo() } returns listOf(mockModifierInfo)
        if (clickable) {
            every { mockModifierInfo.modifier } returns semanticsModifier

            every { semanticsModifier.semanticsConfiguration } returns semanticsConfiguration
            every { semanticsConfiguration.contains(eq(SemanticsActions.OnClick)) } returns true

            if (useDescription) {
                every { semanticsConfiguration.getOrNull(eq(SemanticsActions.OnClick)) } returns null
                every { semanticsConfiguration.getOrNull(eq(SemanticsProperties.ContentDescription)) } returns
                    listOf(
                        "clickMe",
                    )
            } else {
                every { semanticsConfiguration.getOrNull(eq(SemanticsActions.OnClick)) } returns
                    AccessibilityAction<() -> Boolean>("click") { true }
            }

            every { mockNode.semanticsId } returns id
        } else {
            every { mockModifierInfo.modifier } returns modifier
        }

        every { mockNode.zSortedChildren } returns mutableVectorOf()
        every { composeLayoutNodeUtil.getLayoutNodeBoundsInWindow(mockNode) } returns bounds
        every { composeLayoutNodeUtil.getLayoutNodePositionInWindow(mockNode) } returns
            Offset(
                x = bounds.left,
                y = bounds.top,
            )

        return mockNode
    }
}
