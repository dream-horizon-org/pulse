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
@OptIn(Incubating::class)
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
        every { timeoutHandler.getBackgroundStartTimeNanos() } answers { null }
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
        val sessionManager =
            SessionManager(
                TestClock.create(),
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )
        val sessionId = sessionManager.getSessionId()
        val sessionIdPattern = "[a-f0-9]+"

        assertThat(sessionId).isNotNull()
        assertThat(sessionId).hasSize(SESSION_ID_LENGTH)
        assertThat(Pattern.compile(sessionIdPattern).matcher(sessionId).matches()).isTrue()
    }

    @Test
    fun valueSameUntil4Hours() {
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        val value = sessionManager.getSessionId()

        assertThat(value).isEqualTo(sessionManager.getSessionId())
        clock.advance(3, TimeUnit.HOURS)
        assertThat(value).isEqualTo(sessionManager.getSessionId())
        clock.advance(59, TimeUnit.MINUTES)
        assertThat(value).isEqualTo(sessionManager.getSessionId())
        clock.advance(59, TimeUnit.SECONDS)
        assertThat(value).isEqualTo(sessionManager.getSessionId())

        clock.advance(1, TimeUnit.SECONDS)
        val newSessionId = sessionManager.getSessionId()

        assertThat(newSessionId).isNotNull()
        assertThat(value).isNotEqualTo(newSessionId)
    }

    @Test
    fun shouldCallSessionIdChangeListener() {
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

        val firstSessionId = sessionManager.getSessionId()

        verify(exactly = 1) { timeoutHandler.bump() }
        verify(exactly = 1) { timeoutHandler.hasTimedOut() }
        verify(exactly = 1) { observer.onSessionStarted(any<Session>(), eq(Session.NONE)) }
        verify(exactly = 1) { observer.onSessionEnded(eq(Session.NONE), any()) }

        clock.advance(3, TimeUnit.HOURS)
        val secondSessionId = sessionManager.getSessionId()

        assertThat(firstSessionId).isEqualTo(secondSessionId)
        verify(exactly = 2) { timeoutHandler.bump() }
        verify(exactly = 2) { timeoutHandler.hasTimedOut() }
        verify(exactly = 1) { observer.onSessionStarted(any<Session>(), any<Session>()) }
        verify(exactly = 1) { observer.onSessionEnded(any<Session>(), any()) }

        clock.advance(1, TimeUnit.HOURS)
        val thirdSessionId = sessionManager.getSessionId()

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
        verify(atLeast = 0) { timeoutHandler.getBackgroundStartTimeNanos() }
        confirmVerified(timeoutHandler)
    }

    @Test
    fun shouldCreateNewSessionIdAfterTimeout() {
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        val value = sessionManager.getSessionId()
        verify { timeoutHandler.bump() }

        assertThat(value).isEqualTo(sessionManager.getSessionId())
        verify(exactly = 2) { timeoutHandler.bump() }

        every { timeoutHandler.hasTimedOut() } returns true
        every { timeoutHandler.getBackgroundStartTimeNanos() } returns null

        assertThat(value).isNotEqualTo(sessionManager.getSessionId())
        verify(exactly = 3) { timeoutHandler.bump() }
    }

    @Test
    fun `concurrent access during timeout should create only one new session`() {
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        val initialSessionId = sessionManager.getSessionId()

        clock.advance(5, TimeUnit.HOURS)

        val numThreads = 10
        val executor = Executors.newFixedThreadPool(numThreads)
        val firstLatch = CountDownLatch(numThreads)
        val lastLatch = CountDownLatch(numThreads)
        val sessionIds = mutableSetOf<String>()
        val sessionIdCount = AtomicInteger(0)

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

        assertThat(sessionIds).hasSize(1)
        assertThat(sessionIds.first()).isNotEqualTo(initialSessionId)
        assertThat(sessionIdCount.get()).isEqualTo(numThreads)
    }

    @Test
    fun `concurrent access with timeout handler should create only one new session`() {
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )
        val initialSessionId = sessionManager.getSessionId()

        every { timeoutHandler.hasTimedOut() } returns true
        every { timeoutHandler.getBackgroundStartTimeNanos() } returns null

        val numThreads = 5
        val executor = Executors.newFixedThreadPool(numThreads)
        val firstLatch = CountDownLatch(numThreads)
        val lastLatch = CountDownLatch(numThreads)
        val sessionIds = mutableSetOf<String>()
        val sessionIdCount = AtomicInteger(0)

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

        assertThat(sessionIdCount.get()).isEqualTo(numThreads)
        assertThat(sessionIds.first()).isNotEqualTo(initialSessionId)
    }

    @Test
    fun `concurrent access should accesses see the same session ID when no timeout occurs`() {
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

        assertThat(sessionIds).hasSize(1)
        assertThat(sessionIdCount.get()).isEqualTo(numThreads)
    }

    @Test
    fun `session expiration check uses correct session instance`() {
        val clock = TestClock.create()
        val sessionManager =
            SessionManager(
                clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = MAX_SESSION_LIFETIME.hours,
            )

        val firstSessionId = sessionManager.getSessionId()

        clock.advance(2, TimeUnit.HOURS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        clock.advance(1, TimeUnit.HOURS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        clock.advance(59, TimeUnit.MINUTES)
        clock.advance(59, TimeUnit.SECONDS)

        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        clock.advance(1, TimeUnit.SECONDS)
        val secondSessionId = sessionManager.getSessionId()

        assertThat(secondSessionId).isNotEqualTo(firstSessionId)
        assertThat(secondSessionId).isNotNull()
        assertThat(secondSessionId).hasSize(SESSION_ID_LENGTH)

        clock.advance(3, TimeUnit.HOURS)
        assertThat(sessionManager.getSessionId()).isEqualTo(secondSessionId)

        clock.advance(1, TimeUnit.HOURS)
        clock.advance(1, TimeUnit.SECONDS)
        val thirdSessionId = sessionManager.getSessionId()

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
        val clock = TestClock.create()
        val config = SessionConfig(maxLifetime = null)

        val sessionManager =
            SessionManager(
                clock = clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = config.maxLifetime ?: 4.hours,
            )

        val firstSessionId = sessionManager.getSessionId()

        clock.advance(3, TimeUnit.HOURS)
        clock.advance(59, TimeUnit.MINUTES)
        clock.advance(59, TimeUnit.SECONDS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        clock.advance(1, TimeUnit.SECONDS)
        val secondSessionId = sessionManager.getSessionId()

        assertThat(secondSessionId).isNotEqualTo(firstSessionId)
        assertThat(secondSessionId).isNotNull()
        assertThat(secondSessionId).hasSize(SESSION_ID_LENGTH)
    }

    @Test
    fun `should use persistent storage when shouldPersist is true`() {
        val config = SessionConfig(shouldPersist = true)

        SessionManager.create(
            mockContext,
            timeoutHandler,
            config,
        )

        verify(exactly = 1) { mockContext.getSharedPreferences("otel_session_storage", Context.MODE_PRIVATE) }
    }

    @Test
    fun `should use in-memory storage when shouldPersist is false`() {
        val config = SessionConfig(shouldPersist = false)

        SessionManager.create(
            mockContext,
            timeoutHandler,
            config,
        )

        verify(exactly = 0) { mockContext.getSharedPreferences(any(), any()) }
    }

    @Test
    fun `should restore valid session from persistent storage`() {
        val clock = TestClock.create()
        val currentTime = clock.now()
        val storedSessionId = "stored-session-id-12345678901234567890123456789012"
        val storedStartTime = currentTime - (2 * 1_000_000_000L * 3600)

        every { mockSharedPreferences.getString("otel-session-id", null) } returns storedSessionId
        every { mockSharedPreferences.getLong("otel-session-start-timestamp", -1) } returns storedStartTime

        val config = SessionConfig(shouldPersist = true, maxLifetime = 4.hours)
        val sessionStorage: SessionStorage = PersistentSessionStorage(mockSharedPreferences)

        val sessionManager =
            SessionManager(
                clock = clock,
                sessionStorage = sessionStorage,
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = config.maxLifetime ?: 4.hours,
            )

        val sessionId = sessionManager.getSessionId()
        assertThat(sessionId).isEqualTo(storedSessionId)
    }

    @Test
    fun `should create new session when restored session is expired`() {
        val storedSessionId = "expired-session-id-12345678901234567890123456789012"
        val storedStartTime = System.nanoTime() - (5 * 1_000_000_000L * 3600)

        every { mockSharedPreferences.getString("otel-session-id", null) } returns storedSessionId
        every { mockSharedPreferences.getLong("otel-session-start-timestamp", -1) } returns storedStartTime

        val observer = mockk<SessionObserver>()
        every { observer.onSessionStarted(any<Session>(), any<Session>()) } just Runs
        every { observer.onSessionEnded(any<Session>(), any()) } just Runs

        val config = SessionConfig(shouldPersist = true, maxLifetime = 4.hours)

        val sessionManager =
            SessionManager.create(
                mockContext,
                timeoutHandler,
                config,
            )
        sessionManager.addObserver(observer)

        val newSessionId = sessionManager.getSessionId()

        assertThat(newSessionId).isNotEqualTo(storedSessionId)
        verify(exactly = 1) { observer.onSessionEnded(match { it.getId() == storedSessionId }, any()) }
        verify(exactly = 1) { observer.onSessionStarted(match { it.getId() == newSessionId }, match { it.getId() == storedSessionId }) }
    }

    @Test
    fun `should handle nullable maxLifetime with custom value`() {
        val clock = TestClock.create()
        val config = SessionConfig(maxLifetime = 1.hours)

        val sessionManager =
            SessionManager(
                clock = clock,
                sessionStorage = InMemorySessionStorage(),
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = config.maxLifetime ?: 4.hours,
            )

        val firstSessionId = sessionManager.getSessionId()

        clock.advance(59, TimeUnit.MINUTES)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)
        clock.advance(59, TimeUnit.SECONDS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        clock.advance(1, TimeUnit.SECONDS)
        val secondSessionId = sessionManager.getSessionId()

        assertThat(secondSessionId).isNotNull()
        assertThat(secondSessionId).isNotEqualTo(firstSessionId)
        assertThat(secondSessionId).hasSize(SESSION_ID_LENGTH)
    }

    @Test
    fun `should set expiration timestamp to session start plus max lifetime for foreground expiration`() {
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

        val sessionStartTimeSlot = slot<Session>()
        val firstSessionId = sessionManager.getSessionId()

        verify(exactly = 1) {
            observer.onSessionStarted(capture(sessionStartTimeSlot), eq(Session.NONE))
        }
        val firstSession = sessionStartTimeSlot.captured
        val firstSessionStartTime = firstSession.getStartTimestamp()

        clock.advance(3, TimeUnit.HOURS)
        clock.advance(59, TimeUnit.MINUTES)
        clock.advance(59, TimeUnit.SECONDS)
        assertThat(sessionManager.getSessionId()).isEqualTo(firstSessionId)

        clock.advance(1, TimeUnit.SECONDS)
        val secondSessionId = sessionManager.getSessionId()

        val expectedExpirationTimestampNanos = firstSessionStartTime + MAX_SESSION_LIFETIME.hours.inWholeNanoseconds

        val expirationTimestampNanosSlot = slot<Long>()
        verify(exactly = 1) {
            observer.onSessionEnded(
                match { it.getId() == firstSessionId },
                capture(expirationTimestampNanosSlot),
            )
        }

        assertThat(expirationTimestampNanosSlot.captured).isEqualTo(expectedExpirationTimestampNanos)
        assertThat(secondSessionId).isNotEqualTo(firstSessionId)
    }

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

        realTimeoutHandler.onApplicationBackgrounded()
        val backgroundStartTimeNanos = clock.now()

        clock.advance(16, TimeUnit.MINUTES)

        val secondSessionId = sessionManager.getSessionId()

        val expirationTimestampNanosSlot = slot<Long>()
        verify(exactly = 1) {
            observer.onSessionEnded(
                match { it.getId() == firstSessionId },
                capture(expirationTimestampNanosSlot),
            )
        }

        assertThat(expirationTimestampNanosSlot.captured).isEqualTo(backgroundStartTimeNanos)
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
