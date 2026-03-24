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
import io.opentelemetry.android.instrumentation.view.click.internal.APP_SCREEN_CLICK_EVENT_NAME
import io.opentelemetry.android.instrumentation.view.click.internal.VIEW_CLICK_EVENT_NAME
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_X
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_SCREEN_COORDINATE_Y
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith

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
        ClickContextEnrichmentConfig.viewClickContextEnrichmentEnabled = true
    }

    @Test
    fun capture_view_click() {
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
        every { application.registerActivityLifecycleCallbacks(any()) } returns Unit

        ViewClickInstrumentation().install(installationContext)

        verify {
            application.registerActivityLifecycleCallbacks(capture(callbackCapturingSlot))
        }

        val viewClickActivityCallback = callbackCapturingSlot.captured
        val wrapperCapturingSlot = slot<WindowCallbackWrapper>()
        every { window.callback = any() } returns Unit

        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent)
        every { window.decorView } returns mockView

        viewClickActivityCallback.onActivityResumed(activity)
        verify {
            window.callback = capture(wrapperCapturingSlot)
        }

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(2)

        var event = events[0]
        assertThat(event)
            .hasEventName(APP_SCREEN_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(PulseAttributes.APP_CLICK_CONTEXT, "type=screen; source=view"),
            )

        event = events[1]
        assertThat(event)
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, mockView.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, mockView.y.toLong()),
                equalTo(APP_WIDGET_ID, mockView.id.toString()),
                equalTo(APP_WIDGET_NAME, "10012"),
                equalTo(PulseAttributes.APP_CLICK_CONTEXT, "type=widget; source=view"),
            )
        upEvent.recycle()
    }

    @Test
    fun capture_view_click_omits_app_click_context_when_enrichment_disabled() {
        ClickContextEnrichmentConfig.viewClickContextEnrichmentEnabled = false

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
        every { application.registerActivityLifecycleCallbacks(any()) } returns Unit

        ViewClickInstrumentation().install(installationContext)

        verify {
            application.registerActivityLifecycleCallbacks(capture(callbackCapturingSlot))
        }

        val viewClickActivityCallback = callbackCapturingSlot.captured
        val wrapperCapturingSlot = slot<WindowCallbackWrapper>()
        every { window.callback = any() } returns Unit

        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent)
        every { window.decorView } returns mockView

        viewClickActivityCallback.onActivityResumed(activity)
        verify {
            window.callback = capture(wrapperCapturingSlot)
        }

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(2)

        var event = events[0]
        assertThat(event)
            .hasEventName(APP_SCREEN_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
            )
        assertNull(event.attributes.get(PulseAttributes.APP_CLICK_CONTEXT))

        event = events[1]
        assertThat(event)
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, mockView.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, mockView.y.toLong()),
                equalTo(APP_WIDGET_ID, mockView.id.toString()),
                equalTo(APP_WIDGET_NAME, "10012"),
            )
        assertNull(event.attributes.get(PulseAttributes.APP_CLICK_CONTEXT))
        upEvent.recycle()
    }

    @Test
    fun capture_view_click_in_viewGroup() {
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
        every { application.registerActivityLifecycleCallbacks(any()) } returns Unit

        ViewClickInstrumentation().install(installationContext)
        verify {
            application.registerActivityLifecycleCallbacks(capture(callbackCapturingSlot))
        }

        val viewClickActivityCallback = callbackCapturingSlot.captured
        val wrapperCapturingSlot = slot<WindowCallbackWrapper>()
        every { window.callback = any() } returns Unit

        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent)
        val mockViewGroup =
            mockView<ViewGroup>(10013, motionEvent, clickable = false) {
                every { it.childCount } returns 1
                every { it.getChildAt(any()) } returns mockView
            }

        every { window.decorView } returns mockViewGroup
        viewClickActivityCallback.onActivityResumed(activity)

        verify {
            window.callback = capture(wrapperCapturingSlot)
        }

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(2)

        var event = events[0]
        assertThat(event)
            .hasEventName(APP_SCREEN_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, upEvent.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, upEvent.y.toLong()),
                equalTo(PulseAttributes.APP_CLICK_CONTEXT, "type=screen; source=view"),
            )

        event = events[1]
        assertThat(event)
            .hasEventName(VIEW_CLICK_EVENT_NAME)
            .hasAttributesSatisfying(
                equalTo(APP_SCREEN_COORDINATE_X, mockView.x.toLong()),
                equalTo(APP_SCREEN_COORDINATE_Y, mockView.y.toLong()),
                equalTo(APP_WIDGET_ID, mockView.id.toString()),
                equalTo(APP_WIDGET_NAME, "10012"),
                equalTo(PulseAttributes.APP_CLICK_CONTEXT, "type=widget; source=view"),
            )
        upEvent.recycle()
    }

    @Test
    fun not_captured_view_click_in_viewGroup() {
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
        every { application.registerActivityLifecycleCallbacks(any()) } returns Unit

        ViewClickInstrumentation().install(installationContext)
        verify {
            application.registerActivityLifecycleCallbacks(capture(callbackCapturingSlot))
        }

        val viewClickActivityCallback = callbackCapturingSlot.captured
        val wrapperCapturingSlot = slot<WindowCallbackWrapper>()
        every { window.callback = any() } returns Unit

        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 250f, 50f, 0)
        val mockView = mockView<View>(10012, motionEvent, hitOffset = intArrayOf(50, 30))
        val mockViewGroup =
            mockView<ViewGroup>(10013, motionEvent, clickable = false) {
                every { it.childCount } returns 1
                every { it.getChildAt(any()) } returns mockView
            }

        every { window.decorView } returns mockViewGroup

        viewClickActivityCallback.onActivityResumed(activity)
        verify {
            window.callback = capture(wrapperCapturingSlot)
        }

        val upEvent = dispatchDownThenUp(wrapperCapturingSlot.captured, motionEvent.x, motionEvent.y)
        motionEvent.recycle()
        upEvent.recycle()

        val events = openTelemetryRule.logRecords
        // No events when tap misses widget: screen click is only emitted when a target is found
        // (avoids duplicate app.screen.click when both View and Compose instrumentations are active)
        assertThat(events).isEmpty()
    }

    @Test
    fun not_captured_view_click_for_down_event() {
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
        every { application.registerActivityLifecycleCallbacks(any()) } returns Unit

        ViewClickInstrumentation().install(installationContext)
        verify {
            application.registerActivityLifecycleCallbacks(capture(callbackCapturingSlot))
        }

        val viewClickActivityCallback = callbackCapturingSlot.captured
        val wrapperCapturingSlot = slot<WindowCallbackWrapper>()
        every { window.callback = any() } returns Unit

        val motionEvent =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 250f, 50f, 0)

        viewClickActivityCallback.onActivityResumed(activity)
        verify {
            window.callback = capture(wrapperCapturingSlot)
        }

        wrapperCapturingSlot.captured.dispatchTouchEvent(
            motionEvent,
        )

        val events = openTelemetryRule.logRecords
        assertThat(events).hasSize(0)
    }

    /** Real taps send [ACTION_DOWN] then [ACTION_UP]; the generator requires both for click detection. */
    private fun dispatchDownThenUp(wrapper: WindowCallbackWrapper, x: Float, y: Float): MotionEvent {
        val down = MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        val up =
            MotionEvent.obtain(0L, SystemClock.uptimeMillis() + 10, MotionEvent.ACTION_UP, x, y, 0)
        wrapper.dispatchTouchEvent(down)
        wrapper.dispatchTouchEvent(up)
        down.recycle()
        return up
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
