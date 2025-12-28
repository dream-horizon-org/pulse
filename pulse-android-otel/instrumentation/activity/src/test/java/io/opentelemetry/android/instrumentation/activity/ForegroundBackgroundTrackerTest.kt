/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class ForegroundBackgroundTrackerTest {
    private companion object {
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    private lateinit var tracker: ForegroundBackgroundTracker
    private lateinit var activity1: Activity
    private lateinit var activity2: Activity

    @BeforeEach
    fun setup() {
        val logger =
            otelTesting.openTelemetry.logsBridge
                .loggerBuilder("test")
                .build()
        tracker = ForegroundBackgroundTracker(logger)
        activity1 = mockk<Activity>(relaxed = true)
        activity2 = mockk<Activity>(relaxed = true)
        otelTesting.clearLogRecords()
    }

    @Test
    fun `emits created event on first activity resume`() {
        tracker.onActivityStarted(activity1)

        val logRecords = otelTesting.logRecords
        assertThat(logRecords).hasSize(1)
        val log = logRecords[0]
        assertThat(log.eventName).isEqualTo("device.app.lifecycle")
        assertThat(log.attributes.get(RumConstants.Android.APP_STATE)).isEqualTo("created")
    }

    @Test
    fun `emits foreground event when app transitions from background`() {
        // First activity lifecycle - creates app
        tracker.onActivityStarted(activity1)

        // Activity goes to background (this emits background event)
        tracker.onActivityStopped(activity1)

        // Clear the background event so we only check for foreground
        otelTesting.clearLogRecords()

        // Activity comes back to foreground
        tracker.onActivityStarted(activity1)

        val logRecords = otelTesting.logRecords
        assertThat(logRecords).hasSize(1)
        val log = logRecords[0]
        assertThat(log.eventName).isEqualTo("device.app.lifecycle")
        assertThat(log.attributes.get(RumConstants.Android.APP_STATE)).isEqualTo("foreground")
    }

    @Test
    fun `emits background event when last activity is stopped`() {
        // First activity lifecycle - creates app
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Activity goes to background (not a configuration change)
        every { activity1.isChangingConfigurations } returns false
        tracker.onActivityStopped(activity1)

        val logRecords = otelTesting.logRecords
        assertThat(logRecords).hasSize(1)
        val log = logRecords[0]
        assertThat(log.eventName).isEqualTo("device.app.lifecycle")
        assertThat(log.attributes.get(RumConstants.Android.APP_STATE)).isEqualTo("background")
    }

    @Test
    fun `does not emit background event if multiple activities are running`() {
        // First activity lifecycle - creates app
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Second activity starts
        tracker.onActivityStarted(activity2)

        // First activity pauses and stops (but second is still running)
        tracker.onActivityStopped(activity1)

        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `tracks multiple activities correctly`() {
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Second activity starts
        tracker.onActivityStarted(activity2)

        // First activity pauses and stops
        tracker.onActivityStopped(activity1)

        // No background event yet
        assertThat(otelTesting.logRecords).isEmpty()

        // Second activity pauses and stops - now should emit background
        tracker.onActivityStopped(activity2)

        val logRecords = otelTesting.logRecords
        assertThat(logRecords).hasSize(1)
        val log = logRecords[0]
        assertThat(log.eventName).isEqualTo("device.app.lifecycle")
        assertThat(log.attributes.get(RumConstants.Android.APP_STATE)).isEqualTo("background")
    }

    @Test
    fun `does not emit event when transitioning between activities A to B`() {
        // Start with activity A
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Navigate to activity B (app is still in foreground)
        tracker.onActivityStarted(activity2)

        // No event should be emitted since app state hasn't changed
        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `does not emit event when navigating back from B to A`() {
        // Start with activity A
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Navigate to activity B
        tracker.onActivityStarted(activity2)

        // Navigate back to activity A (B is stopped, A is resumed)
        tracker.onActivityStopped(activity2)
        tracker.onActivityStarted(activity1)

        // No event should be emitted since app state hasn't changed (still in foreground)
        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `does not emit foreground event when activity resumes while app is already in foreground`() {
        // Start with activity A
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Start activity B (app is still in foreground)
        tracker.onActivityStarted(activity2)

        // Activity A is stopped (but B is still running, so app is still in foreground)
        tracker.onActivityStopped(activity1)

        // Activity A is started and resumed again (but app was already in foreground)
        tracker.onActivityStarted(activity1)

        // No event should be emitted since app was already in foreground (B was still running)
        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `created event is only emitted once`() {
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Second activity resumes
        tracker.onActivityStarted(activity2)

        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `does not emit event when single activity is rotated`() {
        // Initial activity start
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Activity rotation: old instance stops with isChangingConfigurations=true, new instance starts
        // The new activity starts before the timeout, cancelling the delayed background message
        every { activity1.isChangingConfigurations } returns true
        val rotatedActivity1 = mockk<Activity>(relaxed = true)
        tracker.onActivityStopped(activity1)
        tracker.onActivityStarted(rotatedActivity1)

        // No event should be emitted since app state hasn't changed (still in foreground)
        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `does not emit event when activity is rotated with one activity in backstack`() {
        // Start with activity A
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Navigate to activity B
        tracker.onActivityStarted(activity2)

        // Activity B is rotated: old instance stops with isChangingConfigurations=true, new instance starts
        // The new activity starts before the timeout, cancelling the delayed background message
        every { activity2.isChangingConfigurations } returns true
        val rotatedActivity2 = mockk<Activity>(relaxed = true)
        tracker.onActivityStopped(activity2)
        tracker.onActivityStarted(rotatedActivity2)

        // No event should be emitted since app state hasn't changed (still in foreground)
        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `does not emit event when activity is rotated with two activities in backstack`() {
        // Start with activity A
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Navigate to activity B
        tracker.onActivityStarted(activity2)

        // Activity A is stopped (but B is still running)
        every { activity1.isChangingConfigurations } returns false
        tracker.onActivityStopped(activity1)

        // Activity B is rotated: old instance stops with isChangingConfigurations=true, new instance starts
        // The new activity starts before the timeout, cancelling the delayed background message
        every { activity2.isChangingConfigurations } returns true
        val rotatedActivity2 = mockk<Activity>(relaxed = true)
        tracker.onActivityStopped(activity2)
        tracker.onActivityStarted(rotatedActivity2)

        // No event should be emitted since app state hasn't changed (still in foreground)
        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `does not emit event when multiple activities are rotated`() {
        // Start with activity A
        tracker.onActivityStarted(activity1)
        otelTesting.clearLogRecords()

        // Navigate to activity B
        tracker.onActivityStarted(activity2)

        // Activity A is rotated: old instance stops with isChangingConfigurations=true, new instance starts
        // The new activity starts before the timeout, cancelling the delayed background message
        every { activity1.isChangingConfigurations } returns true
        val rotatedActivity1 = mockk<Activity>(relaxed = true)
        tracker.onActivityStopped(activity1)
        tracker.onActivityStarted(rotatedActivity1)

        // Activity B is rotated: old instance stops with isChangingConfigurations=true, new instance starts
        // The new activity starts before the timeout, cancelling the delayed background message
        every { activity2.isChangingConfigurations } returns true
        val rotatedActivity2 = mockk<Activity>(relaxed = true)
        tracker.onActivityStopped(activity2)
        tracker.onActivityStarted(rotatedActivity2)

        // No event should be emitted since app state hasn't changed (still in foreground)
        val logRecords = otelTesting.logRecords
        assertThat(logRecords).isEmpty()
    }
}
