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

        clock.advance(14, TimeUnit.MINUTES)
        clock.advance(59, TimeUnit.SECONDS)
        assertFalse(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        clock.advance(1, TimeUnit.MINUTES)
        assertFalse(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        clock.advance(15, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())

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

        timeoutHandler.onApplicationForegrounded()
        clock.advance(20, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

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

        timeoutHandler.onApplicationForegrounded()
        clock.advance(6, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())
        timeoutHandler.bump()

        clock.advance(Duration.ofHours(4))
        assertFalse(timeoutHandler.hasTimedOut())
    }

    @Test
    fun `should track background start time when app goes to background`() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == null)

        val backgroundTimeNanos = clock.now()
        timeoutHandler.onApplicationBackgrounded()

        val capturedBackgroundTimeNanos = timeoutHandler.getBackgroundStartTimeNanos()
        assertTrue(capturedBackgroundTimeNanos != null)
        assertTrue(capturedBackgroundTimeNanos == backgroundTimeNanos)

        timeoutHandler.onApplicationForegrounded()
        timeoutHandler.bump()

        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == null)
    }

    @Test
    fun `should persist background start time until app returns to foreground`() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        val backgroundTimeNanos = clock.now()
        timeoutHandler.onApplicationBackgrounded()

        clock.advance(10, TimeUnit.MINUTES)

        val capturedBackgroundTimeNanos = timeoutHandler.getBackgroundStartTimeNanos()
        assertTrue(capturedBackgroundTimeNanos != null)
        assertTrue(capturedBackgroundTimeNanos == backgroundTimeNanos)

        timeoutHandler.onApplicationForegrounded()
        timeoutHandler.bump()

        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == null)
    }

    @Test
    fun `should track background start time and timeout correctly together`() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        assertFalse(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == null)

        val backgroundTimeNanos = clock.now()
        timeoutHandler.onApplicationBackgrounded()
        timeoutHandler.bump()

        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)
        assertFalse(timeoutHandler.hasTimedOut())

        clock.advance(10, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)
        assertFalse(timeoutHandler.hasTimedOut())

        clock.advance(6, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)

        timeoutHandler.bump()
        assertFalse(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)

        timeoutHandler.onApplicationForegrounded()
        timeoutHandler.bump()
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == null)
        assertFalse(timeoutHandler.hasTimedOut())
    }

    @Test
    fun `should not timeout in foreground even if background start time is set`() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        timeoutHandler.onApplicationBackgrounded()
        val backgroundTimeNanos = clock.now()
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)

        clock.advance(20, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())

        timeoutHandler.onApplicationForegrounded()
        timeoutHandler.bump()

        assertFalse(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == null)
    }

    @Test
    fun `should persist background start time through multiple timeout checks`() {
        val clock: TestClock = TestClock.create()
        val timeoutHandler =
            SessionIdTimeoutHandler(clock, SessionConfig.withDefaults().backgroundInactivityTimeout!!)

        val backgroundTimeNanos = clock.now()
        timeoutHandler.onApplicationBackgrounded()
        timeoutHandler.bump()

        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)

        assertFalse(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)

        clock.advance(5, TimeUnit.MINUTES)
        assertFalse(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)

        clock.advance(10, TimeUnit.MINUTES)
        assertTrue(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)

        timeoutHandler.bump()
        assertFalse(timeoutHandler.hasTimedOut())
        assertTrue(timeoutHandler.getBackgroundStartTimeNanos() == backgroundTimeNanos)
    }
}
