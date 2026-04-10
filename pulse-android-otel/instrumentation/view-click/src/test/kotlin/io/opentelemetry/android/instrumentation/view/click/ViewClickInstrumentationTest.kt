/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.view.click

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.Window.Callback
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.semconv.PulseAttributes
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.slot
import io.mockk.verify
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.instrumentation.click.ClickContextEnrichmentConfig
import io.opentelemetry.android.instrumentation.view.click.internal.VIEW_CLICK_EVENT_NAME
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule
import io.opentelemetry.sdk.testing.time.TestClock
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@ExtendWith(MockKExtension::class)
class ViewClickInstrumentationTest {
    private lateinit var openTelemetryRule: OpenTelemetryRule

    @MockK
    lateinit var window: Window

    @MockK
    lateinit var callback: Callback

    @MockK
    lateinit var activity: Activity

    @MockK
    lateinit var application: Application

    @Before
    fun setUp() {
        openTelemetryRule = OpenTelemetryRule.create()
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { window.context } returns ApplicationProvider.getApplicationContext<Context>()
    }

    @After
    fun tearDown() {
        ClickContextEnrichmentConfig.isViewClickContextEnrichmentEnabled = true
    }

    @Test
    fun capture_view_click() {
        val (viewClickActivityCallback, wrapperCapturingSlot) = setupInstrumentation()

        val motionEvent = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent)
        every { window.decorView } returns mockView

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        viewClickActivityCallback.onActivityPaused(activity)

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)
        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(APP_WIDGET_ID, mockView.id.toString()),
                equalTo(APP_WIDGET_NAME, "10012"),
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
            )
        assertNull(events[0].attributes.get(PulseAttributes.APP_CLICK_CONTEXT))
        upEvent.recycle()
    }

    @Test
    fun capture_view_click_omits_app_click_context_when_enrichment_disabled() {
        ClickContextEnrichmentConfig.isViewClickContextEnrichmentEnabled = false

        val (viewClickActivityCallback, wrapperCapturingSlot) = setupInstrumentation()

        val motionEvent = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent)
        every { window.decorView } returns mockView

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        viewClickActivityCallback.onActivityPaused(activity)

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)
        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(APP_WIDGET_ID, mockView.id.toString()),
                equalTo(APP_WIDGET_NAME, "10012"),
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
            )
        assertNull(events[0].attributes.get(PulseAttributes.APP_CLICK_CONTEXT))
        upEvent.recycle()
    }

    @Test
    fun capture_view_click_in_viewGroup() {
        val (viewClickActivityCallback, wrapperCapturingSlot) = setupInstrumentation()

        val motionEvent = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent)
        val mockViewGroup =
            mockView<ViewGroup>(10013, motionEvent, clickable = false) {
                every { it.childCount } returns 1
                every { it.getChildAt(any()) } returns mockView
            }
        every { window.decorView } returns mockViewGroup

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        viewClickActivityCallback.onActivityPaused(activity)

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)
        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(APP_WIDGET_ID, mockView.id.toString()),
                equalTo(APP_WIDGET_NAME, "10012"),
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
            )
        assertNull(events[0].attributes.get(PulseAttributes.APP_CLICK_CONTEXT))
        upEvent.recycle()
    }

    @Test
    fun dead_click_emits_screen_click_event_with_dead_type() {
        val (viewClickActivityCallback, wrapperCapturingSlot) = setupInstrumentation()

        val motionEvent = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent, hitOffset = intArrayOf(50, 30))
        val mockViewGroup =
            mockView<ViewGroup>(10013, motionEvent, clickable = false) {
                every { it.childCount } returns 1
                every { it.getChildAt(any()) } returns mockView
            }
        every { window.decorView } returns mockViewGroup

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        viewClickActivityCallback.onActivityPaused(activity)

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(1)
        assertThat(events[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.DEAD),
            )
        upEvent.recycle()
    }

    @Test
    fun rage_click_emits_single_rage_event_and_suppresses_individuals() {
        val testClock = TestClock.create()
        val generator =
            ViewClickEventGenerator(
                eventLogger =
                    openTelemetryRule.openTelemetry.logsBridge
                        .loggerBuilder("test")
                        .build(),
                isContextEnrichmentEnabled = false,
                clock = testClock,
            )

        every { window.callback } returns callback
        every { window.callback = any() } returns Unit
        generator.startTracking(window)
        every { window.decorView } returns
            mockView<View>(
                10012,
                MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 100f, 100f, 0),
            )

        repeat(3) {
            testClock.advance(50, TimeUnit.MILLISECONDS)
            dispatchDownThenUpOnGenerator(generator, 100f, 100f)
        }
        assertThat(openTelemetryRule.logRecords).hasSize(0)

        repeat(3) {
            testClock.advance(50, TimeUnit.MILLISECONDS)
            dispatchDownThenUpOnGenerator(generator, 100f, 100f)
        }
        assertThat(openTelemetryRule.logRecords).hasSize(0)

        // stopTracking (flush) closes the window → rage emitted with full count=6.
        generator.stopTracking()
        assertThat(openTelemetryRule.logRecords).hasSize(1)
        assertThat(openTelemetryRule.logRecords[0])
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(PulseAttributes.CLICK_TYPE, PulseAttributes.ClickTypeValues.GOOD),
                equalTo(PulseAttributes.CLICK_IS_RAGE, true),
                equalTo(PulseAttributes.CLICK_RAGE_COUNT, 6L),
                equalTo(APP_SCREEN_COORDINATE_X, 100L),
                equalTo(APP_SCREEN_COORDINATE_Y, 100L),
            )
    }

    @Test
    fun rage_suppression_resets_after_window_expires() {
        val testClock = TestClock.create()
        val generator =
            ViewClickEventGenerator(
                eventLogger =
                    openTelemetryRule.openTelemetry.logsBridge
                        .loggerBuilder("test")
                        .build(),
                isContextEnrichmentEnabled = false,
                clock = testClock,
            )

        every { window.callback } returns callback
        every { window.callback = any() } returns Unit
        generator.startTracking(window)
        every { window.decorView } returns
            mockView<View>(
                10012,
                MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 100f, 100f, 0),
            )

        repeat(3) {
            testClock.advance(50, TimeUnit.MILLISECONDS)
            dispatchDownThenUpOnGenerator(generator, 100f, 100f)
        }
        assertThat(openTelemetryRule.logRecords).hasSize(0)

        testClock.advance(2100, TimeUnit.MILLISECONDS)
        dispatchDownThenUpOnGenerator(generator, 100f, 100f)
        assertThat(openTelemetryRule.logRecords).hasSize(1) // rage event emitted on window expiry
        generator.stopTracking()

        assertThat(openTelemetryRule.logRecords).hasSize(2)
    }

    @Test
    fun not_captured_view_click_for_down_event() {
        val (_, wrapperCapturingSlot) = setupInstrumentation()

        val motionEvent = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 250f, 50f, 0)
        wrapperCapturingSlot.captured.dispatchTouchEvent(motionEvent)

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(0)
    }

    /**
     * Installs [ViewClickInstrumentation], captures the activity callback and window wrapper,
     * and calls [onActivityResumed] so the window callback is registered.
     */
    private fun setupInstrumentation(): Pair<ViewClickActivityCallback, CapturingSlot<WindowCallbackWrapper>> {
        val installationContext =
            InstallationContext(
                application,
                openTelemetryRule.openTelemetry,
                mockk<SessionProvider>(),
            )

        val callbackCapturingSlot = slot<ViewClickActivityCallback>()
        every { window.callback } returns callback
        every { callback.dispatchTouchEvent(any()) } returns false
        every { activity.window } returns window
        every { application.resources } returns ApplicationProvider.getApplicationContext<Context>().resources
        every { application.registerActivityLifecycleCallbacks(any()) } returns Unit

        ViewClickInstrumentation().install(installationContext)
        verify { application.registerActivityLifecycleCallbacks(capture(callbackCapturingSlot)) }

        val viewClickActivityCallback = callbackCapturingSlot.captured
        val wrapperCapturingSlot = slot<WindowCallbackWrapper>()
        every { window.callback = any() } returns Unit

        viewClickActivityCallback.onActivityResumed(activity)
        verify { window.callback = capture(wrapperCapturingSlot) }

        return viewClickActivityCallback to wrapperCapturingSlot
    }

    /** Real taps send [ACTION_DOWN] then [ACTION_UP]; the generator requires both for click detection. */
    private fun dispatchDownThenUp(
        wrapper: WindowCallbackWrapper,
        x: Float,
        y: Float,
    ): MotionEvent {
        val down = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0L, SystemClock.uptimeMillis() + 10, MotionEvent.ACTION_UP, x, y, 0)
        wrapper.dispatchTouchEvent(down)
        wrapper.dispatchTouchEvent(up)
        down.recycle()
        return up
    }

    private fun dispatchDownThenUpOnGenerator(
        generator: ViewClickEventGenerator,
        x: Float,
        y: Float,
    ) {
        val down = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0L, SystemClock.uptimeMillis() + 10, MotionEvent.ACTION_UP, x, y, 0)
        generator.generateClick(down)
        generator.generateClick(up)
        down.recycle()
        up.recycle()
    }

    private inline fun <reified T : View> mockView(
        id: Int,
        motionEvent: MotionEvent,
        hitOffset: IntArray = intArrayOf(0, 0),
        clickable: Boolean = true,
        visibility: Int = View.VISIBLE,
        applyOthers: (T) -> Unit = {},
    ): T {
        val mockView = mockkClass(T::class)
        every { mockView.visibility } returns visibility
        every { mockView.isClickable } returns clickable
        every { mockView.isLongClickable } returns false
        every { mockView.id } returns id

        val location = IntArray(2)
        location[0] = (motionEvent.x + hitOffset[0]).toInt()
        location[1] = (motionEvent.y + hitOffset[1]).toInt()

        val arrayCapturingSlot = slot<IntArray>()
        every { mockView.getLocationInWindow(capture(arrayCapturingSlot)) } answers {
            arrayCapturingSlot.captured[0] = location[0]
            arrayCapturingSlot.captured[1] = location[1]
        }

        every { mockView.x } returns location[0].toFloat()
        every { mockView.y } returns location[1].toFloat()
        every { mockView.width } returns location[0] + hitOffset[0]
        every { mockView.height } returns location[1] + hitOffset[1]
        applyOthers.invoke(mockView)

        return mockView
    }
}
