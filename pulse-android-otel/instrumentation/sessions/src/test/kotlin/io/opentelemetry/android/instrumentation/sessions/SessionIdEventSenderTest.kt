/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.sessions

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import io.opentelemetry.android.common.RumConstants.Events.EVENT_SESSION_END
import io.opentelemetry.android.common.RumConstants.Events.EVENT_SESSION_START
import io.opentelemetry.android.session.Session
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_ID
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_PREVIOUS_ID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Test class for [SessionIdEventSender] which is responsible for emitting session.start and session.end events.
 *
 * This class tests:
 * - session.start event creation with correct attributes
 * - session.end event creation with expiration timestamp
 * - Correct session ID usage (ending session, not next session)
 */
class SessionIdEventSenderTest {
    // @MockK annotation creates a mock object that we can control in tests
    // This mock will simulate the OpenTelemetry Logger that emits events
    @MockK
    lateinit var eventLogger: Logger

    // Mock for the LogRecordBuilder - this is what we use to build log records
    @MockK
    lateinit var logRecordBuilder: LogRecordBuilder

    // The class under test - this is what we're testing
    private lateinit var sessionIdEventSender: SessionIdEventSender

    // Test data - sample session IDs for testing
    private val sessionId1 = "session-id-12345678901234567890123456789012"
    private val sessionId2 = "session-id-98765432109876543210987654321098"
    private val expirationTimestampNanos = 1_000_000_000L // 1 second in nanoseconds

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { eventLogger.logRecordBuilder() } returns logRecordBuilder
        every { logRecordBuilder.setEventName(any()) } returns logRecordBuilder
        every { logRecordBuilder.setAttribute(any<AttributeKey<String>>(), any<String>()) } returns
            logRecordBuilder
        every { logRecordBuilder.setTimestamp(any(), any()) } returns logRecordBuilder
        every { logRecordBuilder.emit() } returns Unit
        sessionIdEventSender = SessionIdEventSender(eventLogger)
    }

    @Test
    fun `should emit session start event with new session ID`() {
        val newSession = Session.DefaultSession(sessionId1, System.nanoTime())
        val previousSession = Session.NONE

        sessionIdEventSender.onSessionStarted(newSession, previousSession)

        verify { logRecordBuilder.setEventName(EVENT_SESSION_START) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId1) }
        verify { logRecordBuilder.emit() }
        verify(exactly = 0) { logRecordBuilder.setAttribute(SESSION_PREVIOUS_ID, any()) }
    }

    @Test
    fun `should emit session start event with previous session ID when available`() {
        val newSession = Session.DefaultSession(sessionId2, System.nanoTime())
        val previousSession = Session.DefaultSession(sessionId1, System.nanoTime() - 1_000_000_000L)

        sessionIdEventSender.onSessionStarted(newSession, previousSession)

        verify { logRecordBuilder.setEventName(EVENT_SESSION_START) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId2) }
        verify { logRecordBuilder.setAttribute(SESSION_PREVIOUS_ID, sessionId1) }
        verify { logRecordBuilder.emit() }
    }

    @Test
    fun `should emit session end event with expiration timestamp`() {
        val endingSession = Session.DefaultSession(sessionId1, System.nanoTime() - 1_000_000_000L)

        sessionIdEventSender.onSessionEnded(endingSession, expirationTimestampNanos)

        verify { logRecordBuilder.setEventName(EVENT_SESSION_END) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId1) }
        verify { logRecordBuilder.setTimestamp(expirationTimestampNanos, TimeUnit.NANOSECONDS) }
        verify { logRecordBuilder.emit() }
    }

    @Test
    fun `should emit session end event without expiration timestamp when null`() {
        val endingSession = Session.DefaultSession(sessionId1, System.nanoTime())

        sessionIdEventSender.onSessionEnded(endingSession, null)

        verify { logRecordBuilder.setEventName(EVENT_SESSION_END) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId1) }
        verify(exactly = 0) { logRecordBuilder.setTimestamp(any(), any()) }
        verify { logRecordBuilder.emit() }
    }

    @Test
    fun `should use ending session ID not next session ID`() {
        val endingSession = Session.DefaultSession(sessionId1, System.nanoTime())

        sessionIdEventSender.onSessionEnded(endingSession, expirationTimestampNanos)

        val sessionIdSlot = slot<String>()
        verify { logRecordBuilder.setAttribute(SESSION_ID, capture(sessionIdSlot)) }
        assert(sessionIdSlot.captured == sessionId1)
    }

    @Test
    fun `should not emit session end event for blank session ID`() {
        val blankSession = Session.NONE

        sessionIdEventSender.onSessionEnded(blankSession, expirationTimestampNanos)

        verify(exactly = 0) { logRecordBuilder.emit() }
    }
}
