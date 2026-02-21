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
    private val expirationTimestamp = 1_000_000_000L // 1 second in nanoseconds

    /**
     * setUp() runs before each test method
     * This is where we initialize mocks and set up default behaviors
     */
    @BeforeEach
    fun setUp() {
        // Initialize all @MockK annotated fields
        MockKAnnotations.init(this)

        // Configure the mock logger to return our mock builder when logRecordBuilder() is called
        // This allows us to chain calls: eventLogger.logRecordBuilder().setEventName(...)
        every { eventLogger.logRecordBuilder() } returns logRecordBuilder

        // Configure the builder to return itself for method chaining
        // This allows: builder.setEventName(...).setAttribute(...).emit()
        every { logRecordBuilder.setEventName(any()) } returns logRecordBuilder
        // Use specific overload for setAttribute to avoid ambiguity
        every { logRecordBuilder.setAttribute(any<AttributeKey<String>>(), any<String>()) } returns
            logRecordBuilder
        every { logRecordBuilder.setTimestamp(any(), any()) } returns logRecordBuilder
        every { logRecordBuilder.emit() } returns Unit

        // Create the instance we're testing
        sessionIdEventSender = SessionIdEventSender(eventLogger)
    }

    /**
     * Test: session.start event should be created with correct session ID
     *
     * What this tests:
     * - When a new session starts, SessionIdEventSender should emit a session.start event
     * - The event should have the new session's ID as an attribute
     *
     * How it works:
     * 1. Create a new session and previous session (Session.NONE for first session)
     * 2. Call onSessionStarted() which should trigger event emission
     * 3. Verify that setEventName("session.start") was called
     * 4. Verify that setAttribute(SESSION_ID, newSessionId) was called
     */
    @Test
    fun `should emit session start event with new session ID`() {
        // Given: Create a new session (first session, so previous is Session.NONE)
        val newSession = Session.DefaultSession(sessionId1, System.nanoTime())
        val previousSession = Session.NONE

        // When: Notify that a session has started
        sessionIdEventSender.onSessionStarted(newSession, previousSession)

        // Then: Verify the event was created correctly
        // verify() checks that a method was called on the mock
        verify { logRecordBuilder.setEventName(EVENT_SESSION_START) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId1) }
        verify { logRecordBuilder.emit() }

        // Verify that SESSION_PREVIOUS_ID was NOT set (since previous session is NONE)
        // exactly = 0 means the method should never be called
        verify(exactly = 0) { logRecordBuilder.setAttribute(SESSION_PREVIOUS_ID, any()) }
    }

    /**
     * Test: session.start event should include previous session ID when available
     *
     * What this tests:
     * - When a session starts after another session ended, the previous session ID should be included
     * - This helps track session transitions
     */
    @Test
    fun `should emit session start event with previous session ID when available`() {
        // Given: New session with a previous session
        val newSession = Session.DefaultSession(sessionId2, System.nanoTime())
        val previousSession = Session.DefaultSession(sessionId1, System.nanoTime() - 1_000_000_000L)

        // When: Notify that a session has started
        sessionIdEventSender.onSessionStarted(newSession, previousSession)

        // Then: Verify both session IDs are set
        verify { logRecordBuilder.setEventName(EVENT_SESSION_START) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId2) }
        verify { logRecordBuilder.setAttribute(SESSION_PREVIOUS_ID, sessionId1) }
        verify { logRecordBuilder.emit() }
    }

    /**
     * Test: session.end event should be created with expiration timestamp
     *
     * What this tests:
     * - When a session ends, the expiration timestamp should be set on the event
     * - This timestamp reflects when the session actually expired (not when it was detected)
     *
     * How it works:
     * - expirationTimestamp is passed to onSessionEnded()
     * - SessionIdEventSender sets this timestamp on the log record using setTimestamp()
     */
    @Test
    fun `should emit session end event with expiration timestamp`() {
        // Given: A session that has ended with an expiration timestamp
        val endingSession = Session.DefaultSession(sessionId1, System.nanoTime() - 1_000_000_000L)

        // When: Notify that the session has ended with a timestamp
        sessionIdEventSender.onSessionEnded(endingSession, expirationTimestamp)

        // Then: Verify the event was created with the timestamp
        verify { logRecordBuilder.setEventName(EVENT_SESSION_END) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId1) }
        // Verify that setTimestamp was called with the expiration timestamp
        verify { logRecordBuilder.setTimestamp(expirationTimestamp, TimeUnit.NANOSECONDS) }
        verify { logRecordBuilder.emit() }
    }

    /**
     * Test: session.end event should work without expiration timestamp
     *
     * What this tests:
     * - If expirationTimestamp is null, the event should still be created
     * - This handles edge cases where timestamp might not be available
     */
    @Test
    fun `should emit session end event without expiration timestamp when null`() {
        // Given: A session that has ended without a timestamp
        val endingSession = Session.DefaultSession(sessionId1, System.nanoTime())

        // When: Notify that the session has ended without a timestamp
        sessionIdEventSender.onSessionEnded(endingSession, null)

        // Then: Verify the event was created but setTimestamp was NOT called
        verify { logRecordBuilder.setEventName(EVENT_SESSION_END) }
        verify { logRecordBuilder.setAttribute(SESSION_ID, sessionId1) }
        verify(exactly = 0) { logRecordBuilder.setTimestamp(any(), any()) }
        verify { logRecordBuilder.emit() }
    }

    /**
     * Test: session.end should use the ending session's ID, not the next session's ID
     *
     * What this tests:
     * - This is a critical test to ensure we capture the CORRECT session ID
     * - The ending session ID is captured at the start of onSessionEnded() to prevent race conditions
     * - This ensures session.end events always have the ID of the session that's actually ending
     *
     * Why this matters:
     * - If we used the global session state, it might have already been updated to the new session
     * - By capturing the ID early, we ensure correctness
     */
    @Test
    fun `should use ending session ID not next session ID`() {
        // Given: A session that is ending
        val endingSession = Session.DefaultSession(sessionId1, System.nanoTime())

        // When: Notify that the session has ended
        sessionIdEventSender.onSessionEnded(endingSession, expirationTimestamp)

        // Then: Verify the event uses the ending session's ID (sessionId1)
        // We use a slot to capture the actual value passed to setAttribute
        val sessionIdSlot = slot<String>()
        verify { logRecordBuilder.setAttribute(SESSION_ID, capture(sessionIdSlot)) }

        // Assert that the captured session ID matches the ending session
        assert(sessionIdSlot.captured == sessionId1)
    }

    /**
     * Test: session.end should not emit event for blank session ID
     *
     * What this tests:
     * - Edge case: If session ID is blank, no event should be emitted
     * - This prevents invalid events from being created
     */
    @Test
    fun `should not emit session end event for blank session ID`() {
        // Given: A session with blank ID (invalid session)
        val blankSession = Session.NONE

        // When: Try to notify that the session has ended
        sessionIdEventSender.onSessionEnded(blankSession, expirationTimestamp)

        // Then: Verify NO event was emitted (emit() should not be called)
        verify(exactly = 0) { logRecordBuilder.emit() }
    }
}
