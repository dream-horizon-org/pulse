/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.opentelemetry.instrumentation.compose.click

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import android.view.Window.Callback
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.semconv.PulseAttributes
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule
import io.opentelemetry.sdk.testing.time.TestClock
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class ComposeClickEventGeneratorTest {
    private lateinit var openTelemetryRule: OpenTelemetryRule

    private lateinit var composeClickEventGenerator: ComposeClickEventGenerator

    @MockK
    lateinit var composeLayoutNodeUtil: ComposeLayoutNodeUtil

    @MockK
    lateinit var window: Window

    @MockK
    lateinit var callback: Callback

    @MockK
    internal lateinit var composeView: AndroidComposeView

    @MockK
    lateinit var semanticsModifier: SemanticsModifier

    @MockK
    lateinit var modifier: Modifier

    @MockK
    lateinit var semanticsConfiguration: SemanticsConfiguration

    @Before
    fun setup() {
        openTelemetryRule = OpenTelemetryRule.create()
        MockKAnnotations.init(this, relaxUnitFun = true)
        composeClickEventGenerator =
            ComposeClickEventGenerator(
                openTelemetryRule.openTelemetry.logsBridge
                    .loggerBuilder("io.opentelemetry.android.instrumentation.compose")
                    .build(),
                isContextEnrichmentEnabled = true,
                composeLayoutNodeUtil = composeLayoutNodeUtil,
            )

        every { window.callback } returns callback
        every { window.decorView } returns composeView
        every { window.callback = any() } returns Unit
        every { window.context } returns ApplicationProvider.getApplicationContext<Context>()
        // isPointInComposeView calls getLocationInWindow (relaxed → {0,0}) and width/height.
        every { composeView.width } returns 1000
        every { composeView.height } returns 1000

        composeClickEventGenerator.startTracking(window)
    }

    @Test
    fun `capture click for a single hit target`() {
        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)

        every { composeView.childCount } returns 0

        buildMockLayoutNodeTree(
            targetX = motionEvent.x,
            targetY = motionEvent.y,
            hitIndexes = listOf(2),
            clickableIndexes = listOf(2, 3, 4),
        )

        val upEvent = dispatchDownThenUpOnGenerator(composeClickEventGenerator, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        composeClickEventGenerator.stopTracking()

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)

        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(APP_WIDGET_ID, "2"),
                equalTo(APP_WIDGET_NAME, "click"),
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
            )
        upEvent.recycle()
    }

    @Test
    fun `capture click when there are two valid targets but the top target wins`() {
        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)

        every { composeView.childCount } returns 0

        buildMockLayoutNodeTree(
            targetX = motionEvent.x,
            targetY = motionEvent.y,
            hitIndexes = listOf(3, 4),
            clickableIndexes = listOf(2, 3, 4),
        )

        val upEvent = dispatchDownThenUpOnGenerator(composeClickEventGenerator, motionEvent.x, motionEvent.y)
        motionEvent.recycle()
        composeClickEventGenerator.stopTracking()

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)

        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(APP_WIDGET_ID, "3"),
                equalTo(APP_WIDGET_NAME, "click"),
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
            )
        upEvent.recycle()
    }

    @Test
    fun `capture click when there are two valid targets but the top target wins and use content description for name`() {
        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)

        every { composeView.childCount } returns 0

        buildMockLayoutNodeTree(
            targetX = motionEvent.x,
            targetY = motionEvent.y,
            hitIndexes = listOf(3, 4),
            clickableIndexes = listOf(2, 3, 4),
            describableIndexes = listOf(3, 4),
        )

        val upEvent = dispatchDownThenUpOnGenerator(composeClickEventGenerator, motionEvent.x, motionEvent.y)
        motionEvent.recycle()
        composeClickEventGenerator.stopTracking()

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)

        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(APP_WIDGET_ID, "3"),
                equalTo(APP_WIDGET_NAME, "clickMe"),
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
            )
        upEvent.recycle()
    }

    @Test
    fun `capture click without app click context when enrichment disabled`() {
        val localRule = OpenTelemetryRule.create()
        val generator =
            ComposeClickEventGenerator(
                localRule.openTelemetry.logsBridge
                    .loggerBuilder("io.opentelemetry.android.instrumentation.compose")
                    .build(),
                isContextEnrichmentEnabled = false,
                composeLayoutNodeUtil = composeLayoutNodeUtil,
            )
        every { window.callback } returns callback
        every { window.decorView } returns composeView
        every { window.callback = any() } returns Unit
        every { window.context } returns ApplicationProvider.getApplicationContext<Context>()
        generator.startTracking(window)

        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)

        every { composeView.childCount } returns 0

        buildMockLayoutNodeTree(
            targetX = motionEvent.x,
            targetY = motionEvent.y,
            hitIndexes = listOf(2),
            clickableIndexes = listOf(2, 3, 4),
        )

        val upEvent = dispatchDownThenUpOnGenerator(generator, motionEvent.x, motionEvent.y)
        motionEvent.recycle()
        generator.stopTracking()

        val events = localRule.logRecords
        assertThat(events).hasSize(1)
        assertNull(events[0].attributes.get(PulseAttributes.APP_CLICK_CONTEXT))
        upEvent.recycle()
    }

    @Test
    fun `dead click emits screen click event with dead type`() {
        // No hit nodes → dead click.
        every { composeView.childCount } returns 0
        buildMockLayoutNodeTree(targetX = 999f, targetY = 999f) // nodes placed far from tap

        val motionEvent = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val upEvent = dispatchDownThenUpOnGenerator(composeClickEventGenerator, motionEvent.x, motionEvent.y)
        motionEvent.recycle()
        composeClickEventGenerator.stopTracking()

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)
        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.DEAD),
            )
        upEvent.recycle()
    }

    @Test
    fun `rage click emits single rage event and suppresses individual clicks`() {
        val testClock = TestClock.create()
        val generator =
            ComposeClickEventGenerator(
                openTelemetryRule.openTelemetry.logsBridge
                    .loggerBuilder("test")
                    .build(),
                isContextEnrichmentEnabled = false,
                composeLayoutNodeUtil = composeLayoutNodeUtil,
                clock = testClock,
            )
        every { window.callback } returns callback
        every { window.callback = any() } returns Unit
        every { window.context } returns ApplicationProvider.getApplicationContext<Context>()
        generator.startTracking(window)

        val x = 250f
        val y = 50f
        every { composeView.childCount } returns 0
        buildMockLayoutNodeTree(targetX = x, targetY = y, hitIndexes = listOf(2), clickableIndexes = listOf(2))

        // 3 taps → rage threshold crossed, window still open, nothing emitted yet.
        repeat(3) {
            testClock.advance(50, TimeUnit.MILLISECONDS)
            dispatchDownThenUpOnGenerator(generator, x, y)
        }
        assertThat(openTelemetryRule.logRecords).hasSize(0)

        // Clicks 4-6 are suppressed, count accumulates to 6.
        repeat(3) {
            testClock.advance(50, TimeUnit.MILLISECONDS)
            dispatchDownThenUpOnGenerator(generator, x, y)
        }
        assertThat(openTelemetryRule.logRecords).hasSize(0)

        // stopTracking (flush) closes the window → rage emitted with count=6.
        generator.stopTracking()
        assertThat(openTelemetryRule.logRecords).hasSize(1)
        assertThat(openTelemetryRule.logRecords[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
                equalTo(PulseAttributes.CLICK_IS_RAGE, true),
                equalTo(PulseAttributes.CLICK_RAGE_COUNT, 6L),
            )
    }

    /** Real taps send ACTION_DOWN then ACTION_UP; the generator requires both for click detection. */
    private fun dispatchDownThenUpOnGenerator(
        generator: ComposeClickEventGenerator,
        x: Float,
        y: Float,
    ): MotionEvent {
        val down = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        val up =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis() + 10, MotionEvent.ACTION_UP, x, y, 0)
        generator.generateClick(down)
        generator.generateClick(up)
        down.recycle()
        return up
    }

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

        every { composeLayoutNodeUtil.getLayoutNodeBoundsInWindow(mockNode) } returns bounds
        every { composeLayoutNodeUtil.getLayoutNodePositionInWindow(mockNode) } returns
            Offset(
                x = bounds.left,
                y = bounds.top,
            )

        return mockNode
    }

    private fun buildMockLayoutNodeTree(
        targetX: Float,
        targetY: Float,
        hitIndexes: List<Int> = emptyList(),
        clickableIndexes: List<Int> = emptyList(),
        describableIndexes: List<Int> = emptyList(),
    ) {
        val nodeList = mutableListOf<LayoutNode>()
        for (i in 0 until 5) {
            nodeList.add(
                createMockLayoutNode(
                    targetX = targetX,
                    targetY = targetY,
                    id = i,
                    hit = hitIndexes.contains(i),
                    clickable = clickableIndexes.contains(i),
                    useDescription = describableIndexes.contains(i),
                ),
            )
        }

        every { nodeList[0].zSortedChildren } returns mutableVectorOf(nodeList[1], nodeList[2])
        every { nodeList[1].zSortedChildren } returns mutableVectorOf(nodeList[4], nodeList[3])
        every { nodeList[2].zSortedChildren } returns mutableVectorOf()

        every { nodeList[3].zSortedChildren } returns mutableVectorOf()
        every { nodeList[4].zSortedChildren } returns mutableVectorOf()
        every { composeView.root } returns nodeList[0]
    }
}
