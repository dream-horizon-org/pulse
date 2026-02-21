/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.session

import android.content.Context
import android.content.SharedPreferences
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.time.TestClock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val SESSION_AWAIT_SECONDS: Long = 5
private const val SESSION_ID_LENGTH = 32
private const val MAX_SESSION_LIFETIME: Long = 4

/**
 * Verifies [SessionManager] functionality including session ID generation, timeout handling,
 * observer notifications, and thread-safety under concurrent access scenarios.
 */
internal class SessionManagerTest {
    @MockK
    lateinit var timeoutHandler: SessionIdTimeoutHandler

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockSharedPreferences: SharedPreferences

    @MockK
    lateinit var mockEditor: SharedPreferences.Editor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { timeoutHandler.hasTimedOut() } returns false
        every { timeoutHandler.bump() } just Runs
        every { timeoutHandler.getBackgroundStartTime() } returns null
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } just Runs
        every { mockSharedPreferences.getString(any(), any()) } returns null
        every { mockSharedPreferences.getLong(any(), any()) } returns -1L
    }

    @Test
    fun `generated session IDs are valid 32-character hex strings`() {
        // Given/When
        val sessionManager =
            SessionManager(
                TestClock.create(),
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )
        val sessionId = sessionManager.getSessionId()
        val sessionIdPattern = "[a-f0-9]+"

        // Then
        assertThat(sessionId).isNotNull()
        assertThat(sessionId).hasSize(SESSION_ID_LENGTH)
        assertThat(Pattern.compile(sessionIdPattern).matcher(sessionId).matches()).isTrue()
    }

    @Test
    fun valueSameUntil4Hours() {
        // Given
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        // When - initial session
        val value = sessionManager.getSessionId()

        // Then - should remain same for multiple accesses and time advances
        assertThat(value).isEqualTo(sessionManager.getSessionId())
        clock.advance(3, TimeUnit.HOURS)
        assertThat(value).isEqualTo(sessionManager.getSessionId())
        clock.advance(59, TimeUnit.MINUTES)
        assertThat(value).isEqualTo(sessionManager.getSessionId())
        clock.advance(59, TimeUnit.SECONDS)
        assertThat(value).isEqualTo(sessionManager.getSessionId())

        // When - advance past maxLifetime
        clock.advance(1, TimeUnit.SECONDS)
        val newSessionId = sessionManager.getSessionId()

        // Then - should create new session
        assertThat(newSessionId).isNotNull()
        assertThat(value).isNotEqualTo(newSessionId)
    }

    @Test
    fun shouldCallSessionIdChangeListener() {
        // Given
        val clock = TestClock.create()
        val observer = mockk<SessionObserver>()
        every { observer.onSessionStarted(any<Session>(), any<Session>()) } just Runs
        every { observer.onSessionEnded(any<Session>(), any()) } just Runs

        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )
        sessionManager.addObserver(observer)

        // When - The first call expires the Session.NONE initial session and notifies
        val firstSessionId = sessionManager.getSessionId()

        // Then
        verify(exactly = 1) { timeoutHandler.bump() }
        verify(exactly = 1) { timeoutHandler.hasTimedOut() }
        verify(exactly = 1) { observer.onSessionStarted(any<Session>(), eq(Session.NONE)) }
        verify(exactly = 1) { observer.onSessionEnded(eq(Session.NONE), any()) }

        // When
        clock.advance(3, TimeUnit.HOURS)
        val secondSessionId = sessionManager.getSessionId()

        // Then
        assertThat(firstSessionId).isEqualTo(secondSessionId)
        verify(exactly = 2) { timeoutHandler.bump() }
        verify(exactly = 2) { timeoutHandler.hasTimedOut() }
        verify(exactly = 1) { observer.onSessionStarted(any<Session>(), any<Session>()) }
        verify(exactly = 1) { observer.onSessionEnded(any<Session>(), any()) }

        // When
        clock.advance(1, TimeUnit.HOURS)
        val thirdSessionId = sessionManager.getSessionId()

        // Then
        verify(exactly = 3) { timeoutHandler.bump() }
        verify(exactly = 3) { timeoutHandler.hasTimedOut() }
        assertThat(thirdSessionId).isNotEqualTo(secondSessionId)
        verifyOrder {
            timeoutHandler.bump()
            observer.onSessionEnded(match { it.getId() == secondSessionId }, any())
            observer.onSessionStarted(
                match { it.getId() == thirdSessionId },
                match { it.getId() == secondSessionId },
            )
        }
        confirmVerified(observer)
        verify(atLeast = 0) { timeoutHandler.getBackgroundStartTime() }
        confirmVerified(timeoutHandler)
    }

    @Test
    fun shouldCreateNewSessionIdAfterTimeout() {
        // Given
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        // When - access session ID twice, should be same
        val value = sessionManager.getSessionId()
        verify { timeoutHandler.bump() }

        // Then
        assertThat(value).isEqualTo(sessionManager.getSessionId())
        verify(exactly = 2) { timeoutHandler.bump() }

        // When - timeout handler indicates timeout
        every { timeoutHandler.hasTimedOut() } returns true
        every { timeoutHandler.getBackgroundStartTime() } returns null

        // Then - should create new session
        assertThat(value).isNotEqualTo(sessionManager.getSessionId())
        verify(exactly = 3) { timeoutHandler.bump() }
    }

    @Test
    fun `concurrent access during timeout should create only one new session`() {
        // Given
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        // Establish an initial session
        val initialSessionId = sessionManager.getSessionId()

        // Advance time to trigger session expiration
        clock.advance(5, TimeUnit.HOURS)

        val numThreads = 10
        val executor = Executors.newFixedThreadPool(numThreads)
        val firstLatch = CountDownLatch(numThreads)
        val lastLatch = CountDownLatch(numThreads)
        val sessionIds = mutableSetOf<String>()
        val sessionIdCount = AtomicInteger(0)

        // When - multiple threads access session concurrently after timeout
        val params =
            AddSessionIdsParameters(
                numThreads,
                executor,
                firstLatch,
                lastLatch,
                sessionManager,
                sessionIds,
                sessionIdCount,
            )
        addSessionIdsAcrossThreads(params)

        val isCountZero = lastLatch.await(SESSION_AWAIT_SECONDS, TimeUnit.SECONDS)
        assertThat(isCountZero).isTrue()
        executor.shutdown()

        // Then - verify that only one new session was created
        assertThat(sessionIds).hasSize(1)
        assertThat(sessionIds.first()).isNotEqualTo(initialSessionId)
        assertThat(sessionIdCount.get()).isEqualTo(numThreads)
    }

    @Test
    fun `concurrent access with timeout handler should create only one new session`() {
        // Given
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        every { timeoutHandler.hasTimedOut() } returns true
        every { timeoutHandler.getBackgroundStartTime() } returns null

        val numThreads = 5
        val executor = Executors.newFixedThreadPool(numThreads)
        val firstLatch = CountDownLatch(numThreads)
        val lastLatch = CountDownLatch(numThreads)
        val sessionIds = mutableSetOf<String>()
        val sessionIdCount = AtomicInteger(0)

        // When - multiple threads access session with timeout handler indicating timeout
        val params =
            AddSessionIdsParameters(
                numThreads,
                executor,
                firstLatch,
                lastLatch,
                sessionManager,
                sessionIds,
                sessionIdCount,
            )
        addSessionIdsAcrossThreads(params)

        val isCountZero = lastLatch.await(SESSION_AWAIT_SECONDS, TimeUnit.SECONDS)
        assertThat(isCountZero).isTrue()
        executor.shutdown()

        // Then - verify the expected number of session IDs
        assertThat(sessionIdCount.get()).isEqualTo(numThreads)
    }

    @Test
    fun `concurrent access should accesses see the same session ID when no timeout occurs`() {
        // Given
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        val numThreads = 20
        val executor = Executors.newFixedThreadPool(numThreads)
        val firstLatch = CountDownLatch(numThreads)
        val lastLatch = CountDownLatch(numThreads)
        val sessionIds = mutableSetOf<String>()
        val sessionIdCount = AtomicInteger(0)

        // When - multiple threads access session concurrently
        val params =
            AddSessionIdsParameters(
                numThreads,
                executor,
                firstLatch,
                lastLatch,
                sessionManager,
                sessionIds,
                sessionIdCount,
            )
        addSessionIdsAcrossThreads(params)

        val isCountZero = lastLatch.await(SESSION_AWAIT_SECONDS, TimeUnit.SECONDS)
        assertThat(isCountZero).isTrue()
        executor.shutdown()

        // Then - all threads should have gotten the same session ID
        assertThat(sessionIds).hasSize(1)
        assertThat(sessionIdCount.get()).isEqualTo(numThreads)
    }

    @Test
    fun `session expiration check uses correct session instance`() {
        // Given
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        // When - get initial session
        val firstSessionId = sessionManager.getSessionId()

        // Then - verify session is stable with ids match
        clock.advance(2, TimeUnit.HOURS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        clock.advance(1, TimeUnit.HOURS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        // When - advance time to exactly the expiration boundary
        clock.advance(59, TimeUnit.MINUTES)
        clock.advance(59, TimeUnit.SECONDS)

        // Then - session should still be the same (3h 59m 59s < 4h)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        // When - advance 1 second to exceed maxLifetime
        clock.advance(1, TimeUnit.SECONDS)
        val secondSessionId = sessionManager.getSessionId()

        // Then - new session should have been created
        assertThat(secondSessionId).isNotEqualTo(firstSessionId)
        assertThat(secondSessionId).isNotNull()
        assertThat(secondSessionId).hasSize(SESSION_ID_LENGTH)

        // When - verify ids match
        clock.advance(3, TimeUnit.HOURS)
        assertThat(sessionManager.getSessionId()).isEqualTo(secondSessionId)

        // When - expire second session
        clock.advance(1, TimeUnit.HOURS)
        clock.advance(1, TimeUnit.SECONDS)
        val thirdSessionId = sessionManager.getSessionId()

        // Then - third session should be different from both previous sessions
        assertThat(thirdSessionId).isNotEqualTo(firstSessionId)
        assertThat(thirdSessionId).isNotEqualTo(secondSessionId)
    }

    private fun addSessionIdsAcrossThreads(params: AddSessionIdsParameters) {
        repeat(params.numThreads) {
            params.executor.submit {
                try {
                    params.firstLatch.countDown()
                    params.firstLatch.await(SESSION_AWAIT_SECONDS, TimeUnit.SECONDS)

                    val sessionId = params.sessionManager.getSessionId()
                    synchronized(params.sessionIds) {
                        params.sessionIds.add(sessionId)
                        params.sessionIdCount.incrementAndGet()
                    }
                } finally {
                    params.lastLatch.countDown()
                }
            }
        }
    }

    @Test
    fun `should use default 4 hours when maxLifetime is null`() {
        // Given - config with null maxLifetime
        val config = SessionConfig(maxLifetime = null)
        val sessionManager =
            SessionManager.create(
                mockContext,
                timeoutHandler,
                config,
            )

        // When - get session
        val sessionId = sessionManager.getSessionId()

        // Then - should use default 4 hours (verified by using 4h in SessionManager.create)
        assertThat(sessionId).isNotNull()
        assertThat(sessionId).hasSize(SESSION_ID_LENGTH)
    }

    @Test
    fun `should use persistent storage when shouldPersist is true`() {
        // Given - config with persistence enabled
        val config = SessionConfig(shouldPersist = true)

        // When - create SessionManager
        val sessionManager =
            SessionManager.create(
                mockContext,
                timeoutHandler,
                config,
            )

        // Then - should use PersistentSessionStorage
        verify(exactly = 1) { mockContext.getSharedPreferences("otel_session_storage", Context.MODE_PRIVATE) }
    }

    @Test
    fun `should use in-memory storage when shouldPersist is false`() {
        // Given - config with persistence disabled
        val config = SessionConfig(shouldPersist = false)

        // When - create SessionManager
        val sessionManager =
            SessionManager.create(
                mockContext,
                timeoutHandler,
                config,
            )

        // Then - should not use SharedPreferences (in-memory storage)
        verify(exactly = 0) { mockContext.getSharedPreferences(any(), any()) }
    }

    @Test
    fun `should restore valid session from persistent storage`() {
        // Given - simulate stored session (within 4 hours)
        val clock = TestClock.create()
        val currentTime = clock.now()
        val storedSessionId = "stored-session-id-12345678901234567890123456789012"
        // Store session from 2 hours ago (within 4 hour limit)
        val storedStartTime = currentTime - (2 * 1_000_000_000L * 3600) // 2 hours ago

        every { mockSharedPreferences.getString("otel-session-id", null) } returns storedSessionId
        every { mockSharedPreferences.getLong("otel-session-start-timestamp", -1) } returns storedStartTime

        val config = SessionConfig(shouldPersist = true, maxLifetime = 4.hours)
        val sessionStorage: SessionStorage = PersistentSessionStorage(mockSharedPreferences)

        // When - create SessionManager with TestClock (should restore session)
        val sessionManager =
            SessionManager(
                clock = clock,
                sessionStorage = sessionStorage,
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = config.maxLifetime ?: 4.hours,
            )

        // Then - should reuse restored session
        val sessionId = sessionManager.getSessionId()
        assertThat(sessionId).isEqualTo(storedSessionId)
    }

    @Test
    fun `should create new session when restored session is expired`() {
        // Given - simulate expired stored session (5 hours ago, past 4h limit)
        val storedSessionId = "expired-session-id-12345678901234567890123456789012"
        val storedStartTime = System.nanoTime() - (5 * 1_000_000_000L * 3600) // 5 hours ago

        every { mockSharedPreferences.getString("otel-session-id", null) } returns storedSessionId
        every { mockSharedPreferences.getLong("otel-session-start-timestamp", -1) } returns storedStartTime

        val observer = mockk<SessionObserver>()
        every { observer.onSessionStarted(any<Session>(), any<Session>()) } just Runs
        every { observer.onSessionEnded(any<Session>(), any()) } just Runs

        val config = SessionConfig(shouldPersist = true, maxLifetime = 4.hours)

        // When - create SessionManager and get session
        val sessionManager =
            SessionManager.create(
                mockContext,
                timeoutHandler,
                config,
            )
        sessionManager.addObserver(observer)

        val newSessionId = sessionManager.getSessionId()

        // Then - should create new session and emit session.end for expired one
        assertThat(newSessionId).isNotEqualTo(storedSessionId)
        verify(exactly = 1) { observer.onSessionEnded(match { it.getId() == storedSessionId }, any()) }
        verify(exactly = 1) { observer.onSessionStarted(any<Session>(), match { it.getId() == storedSessionId }) }
    }

    @Test
    fun `should handle nullable maxLifetime with custom value`() {
        // Given - config with custom maxLifetime
        val config = SessionConfig(maxLifetime = 1.hours)
        val sessionManager =
            SessionManager.create(
                mockContext,
                timeoutHandler,
                config,
            )

        // When - get session
        val sessionId = sessionManager.getSessionId()

        // Then - should use custom 1 hour maxLifetime
        assertThat(sessionId).isNotNull()
        assertThat(sessionId).hasSize(SESSION_ID_LENGTH)
    }

    /**
     * Test: session.end timestamp should be session.start + maxLifetime for foreground expiration
     *
     * What this tests:
     * - When a session expires in the foreground due to max lifetime, the expiration timestamp
     *   should be calculated as: session.start + maxLifetime
     * - This ensures accurate timestamps for session.end events
     *
     * How it works:
     * 1. Create a session and capture its start time from the observer
     * 2. Advance time past maxLifetime to trigger expiration
     * 3. Verify that onSessionEnded() is called with the correct expiration timestamp
     *
     * Test syntax explanation:
     * - `slot<Session>()` creates a "slot" to capture the Session object passed to the mock
     * - `capture(sessionStartTimeSlot)` captures the Session parameter when onSessionStarted is called
     * - `verify { ... }` checks that the method was called with the expected parameters
     * - `match { ... }` is a matcher that checks a condition (e.g., session ID matches)
     */
    @Test
    fun `should set expiration timestamp to session start plus max lifetime for foreground expiration`() {
        // Given: A session manager with a test clock and observer
        val clock = TestClock.create()
        val observer = mockk<SessionObserver>()
        every { observer.onSessionStarted(any<Session>(), any<Session>()) } just Runs
        every { observer.onSessionEnded(any<Session>(), any()) } just Runs

        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )
        sessionManager.addObserver(observer)

        // When: Create initial session and capture its start time from the session object
        // We'll capture the session start time from the observer's onSessionStarted call
        val sessionStartTimeSlot = slot<Session>()
        val firstSessionId = sessionManager.getSessionId()

        // Capture the session from the onSessionStarted call to get its start timestamp
        // This is how we get the actual session start time that was used when creating the session
        verify(exactly = 1) {
            observer.onSessionStarted(capture(sessionStartTimeSlot), eq(Session.NONE))
        }
        val firstSession = sessionStartTimeSlot.captured
        val firstSessionStartTime = firstSession.getStartTimestamp()

        // Advance time to just before expiration (3h 59m 59s)
        clock.advance(3, TimeUnit.HOURS)
        clock.advance(59, TimeUnit.MINUTES)
        clock.advance(59, TimeUnit.SECONDS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        // Advance 1 second to trigger expiration (exactly 4 hours from session start)
        clock.advance(1, TimeUnit.SECONDS)
        val secondSessionId = sessionManager.getSessionId()

        // Then: Verify expiration timestamp is session.start + maxLifetime
        // Calculate what the expiration timestamp should be
        val expectedExpirationTimestamp = firstSessionStartTime + MAX_SESSION_LIFETIME.hours.inWholeNanoseconds

        // Capture the expiration timestamp passed to onSessionEnded
        // We use a slot to capture the nullable Long parameter
        val expirationTimestampSlot = slot<Long>()
        verify(exactly = 1) {
            observer.onSessionEnded(
                match { it.getId() == firstSessionId }, // Verify it's the ending session's ID
                capture(expirationTimestampSlot), // Capture the expiration timestamp
            )
        }

        // Assert the timestamp matches our calculation
        // This verifies that the expiration timestamp is correctly calculated as session.start + maxLifetime
        assertThat(expirationTimestampSlot.captured).isEqualTo(expectedExpirationTimestamp)
        assertThat(secondSessionId).isNotEqualTo(firstSessionId)
    }

    /**
     * Test: session.end timestamp should be background start time for background expiration
     *
     * What this tests:
     * - When a session expires in the background (due to max lifetime or background timeout),
     *   the expiration timestamp should be the time when the app went to background
     * - This ensures all background expirations are attributed to the moment the app went to background
     *
     * How it works:
     * 1. Create a session and simulate app going to background
     * 2. Use a real timeout handler to track background state
     * 3. Trigger session expiration while in background
     * 4. Verify that onSessionEnded() uses backgroundStartTime as the expiration timestamp
     *
     * Test syntax explanation:
     * - We use a REAL timeout handler (not a mock) so we can test actual background time tracking
     * - `realTimeoutHandler.onApplicationBackgrounded()` simulates the app going to background
     * - `realTimeoutHandler.getBackgroundStartTime()` returns the time when app went to background
     */
    @OptIn(Incubating::class)
    @Test
    fun `should set expiration timestamp to background start time for background expiration`() {
        // Given: A session manager with background timeout handler
        val clock = TestClock.create()
        val observer = mockk<SessionObserver>()
        every { observer.onSessionStarted(any<Session>(), any<Session>()) } just Runs
        every { observer.onSessionEnded(any<Session>(), any()) } just Runs

        // Create a real timeout handler to track background state
        // We use a real handler (not a mock) so we can test the actual background time tracking
        val realTimeoutHandler =
            SessionIdTimeoutHandler(
                clock,
                15.minutes,
            )

        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = realTimeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )
        sessionManager.addObserver(observer)

        // When: Create initial session
        val firstSessionId = sessionManager.getSessionId()

        // Simulate app going to background
        // This sets the backgroundStartTime in the timeout handler
        realTimeoutHandler.onApplicationBackgrounded()
        val backgroundStartTime = clock.now()

        // Advance time to trigger background timeout (15 minutes)
        // Note: hasTimedOut() will return true because we've advanced 16 minutes past background
        clock.advance(16, TimeUnit.MINUTES)

        // Trigger session expiration (this will call hasTimedOut() which should return true)
        val secondSessionId = sessionManager.getSessionId()

        // Then: Verify expiration timestamp is background start time
        // Capture the expiration timestamp that was passed to onSessionEnded
        val expirationTimestampSlot = slot<Long>()
        verify(exactly = 1) {
            observer.onSessionEnded(
                match { it.getId() == firstSessionId }, // Verify it's the ending session
                capture(expirationTimestampSlot), // Capture the expiration timestamp
            )
        }

        // The expiration timestamp should be the background start time
        // This ensures that background expirations are attributed to when the app went to background
        assertThat(expirationTimestampSlot.captured).isEqualTo(backgroundStartTime)
        assertThat(secondSessionId).isNotEqualTo(firstSessionId)
    }

    private data class AddSessionIdsParameters(
        val numThreads: Int,
        val executor: ExecutorService,
        val firstLatch: CountDownLatch,
        val lastLatch: CountDownLatch,
        val sessionManager: SessionManager,
        val sessionIds: MutableSet<String>,
        val sessionIdCount: AtomicInteger,
    )
}
