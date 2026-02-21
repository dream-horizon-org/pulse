/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(Incubating::class)

package io.opentelemetry.android.agent.session

import io.opentelemetry.android.Incubating
import io.opentelemetry.sdk.testing.time.TestClock
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds

class SessionIdTimeoutHandlerTest {
    @Test
    fun shouldNeverTimeOutInForeground() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        assertFalse(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        // never time out in foreground
        clock.advance(Duration.ofHours(4))
        assertFalse(timeoutHandler.hasTimedOut())
    }

    @Test
    fun shouldApply15MinutesTimeoutToAppsInBackground() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        timeoutHandler.onApplicationBackgrounded()
        timeoutHandler.bump()

        assertFalse(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        // do not timeout if <15 minutes have passed
        clock.advance(14, TimeUnit.MINUTES)
        clock.advance(59, TimeUnit.SECONDS)
        assertFalse(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        // restart the timeout counter after bump()
        clock.advance(1, TimeUnit.MINUTES)
        assertFalse(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        // timeout after 15 minutes
        clock.advance(15, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())

        // bump() resets the counter
        timeoutHandler.bump()
        assertFalse(timeoutHandler.hasTimedOut())
    }

    @Test
    fun shouldApplyTimeoutToFirstSpanAfterAppBeingMovedToForeground() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        timeoutHandler.onApplicationBackgrounded()
        timeoutHandler.bump()

        // the first span after app is moved to the foreground gets timed out
        timeoutHandler.onApplicationForegrounded()
        clock.advance(20, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        // after the initial span it's the same as the usual foreground scenario
        clock.advance(Duration.ofHours(4))
        assertFalse(timeoutHandler.hasTimedOut())
    }

    @Test
    fun shouldApplyCustomTimeoutToFirstSpanAfterAppBeingMovedToForeground() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, 5.nanoseconds)

        timeoutHandler.onApplicationBackgrounded()
        timeoutHandler.bump()

        // the first span after app is moved to the foreground gets timed out
        timeoutHandler.onApplicationForegrounded()
        clock.advance(6, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        // after the initial span it's the same as the usual foreground scenario
        clock.advance(Duration.ofHours(4))
        assertFalse(timeoutHandler.hasTimedOut())
    }

    /**
     * Test: getBackgroundStartTime should return the time when app went to background
     *
     * What this tests:
     * - When app goes to background, the handler should record the wall clock time
     * - getBackgroundStartTime() should return this time for use in session.end timestamps
     *
     * How it works:
     * 1. App is in foreground (backgroundStartTime should be null)
     * 2. App goes to background (backgroundStartTime should be set)
     * 3. App comes back to foreground (backgroundStartTime should be cleared)
     */
    @Test
    fun `should track background start time when app goes to background`() {
        // Given: A timeout handler with test clock
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        // When: App is in foreground initially
        // Then: Background start time should be null (app hasn't gone to background yet)
        assertTrue(timeoutHandler.getBackgroundStartTime() == null)

        // When: App goes to background
        val backgroundTime = clock.now()
        timeoutHandler.onApplicationBackgrounded()

        // Then: Background start time should be set to the time when app went to background
        val capturedBackgroundTime = timeoutHandler.getBackgroundStartTime()
        assertTrue(capturedBackgroundTime != null)
        assertTrue(capturedBackgroundTime == backgroundTime)

        // When: App comes back to foreground and first span is processed
        timeoutHandler.onApplicationForegrounded()
        timeoutHandler.bump() // This clears backgroundStartTime

        // Then: Background start time should be cleared (null)
        assertTrue(timeoutHandler.getBackgroundStartTime() == null)
    }

    /**
     * Test: background start time should persist until app returns to foreground
     *
     * What this tests:
     * - Background start time should remain set while app is in background
     * - It should only be cleared when app returns to foreground and bump() is called
     */
    @Test
    fun `should persist background start time until app returns to foreground`() {
        // Given: A timeout handler with app in background
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        val backgroundTime = clock.now()
        timeoutHandler.onApplicationBackgrounded()

        // When: Time passes while app is in background
        clock.advance(10, TimeUnit.MINUTES)

        // Then: Background start time should still be the original background time
        val capturedBackgroundTime = timeoutHandler.getBackgroundStartTime()
        assertTrue(capturedBackgroundTime != null)
        assertTrue(capturedBackgroundTime == backgroundTime) // Should still be the original time

        // When: App returns to foreground and bump() is called
        timeoutHandler.onApplicationForegrounded()
        timeoutHandler.bump()

        // Then: Background start time should be cleared
        assertTrue(timeoutHandler.getBackgroundStartTime() == null)
    }
}
