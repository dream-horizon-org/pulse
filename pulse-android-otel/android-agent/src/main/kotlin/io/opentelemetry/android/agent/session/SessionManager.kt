/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.session

import android.content.Context
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.android.session.SessionPublisher
import io.opentelemetry.sdk.common.Clock
import java.util.Collections.synchronizedList
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal class SessionManager(
    private val clock: Clock = Clock.getDefault(),
    private val sessionStorage: SessionStorage,
    private val timeoutHandler: SessionIdTimeoutHandler?,
    private val idGenerator: SessionIdGenerator = DefaultSessionIdGenerator(Random.Default),
    private val maxSessionLifetime: Duration,
) : SessionProvider,
    SessionPublisher {
    private val session: AtomicReference<Session> = AtomicReference(Session.NONE)
    private val observers = synchronizedList(ArrayList<SessionObserver>())

    private val expiredRestoredSession: AtomicReference<Session> = AtomicReference(Session.NONE)

    init {
        val restoredSession = sessionStorage.get()
        if (restoredSession.getId().isNotEmpty()) {
            if (!sessionHasExpired(restoredSession)) {
                session.set(restoredSession)
            } else {
                expiredRestoredSession.set(restoredSession)
                sessionStorage.save(Session.NONE)
            }
        } else {
            sessionStorage.save(Session.NONE)
        }
    }

    override fun addObserver(observer: SessionObserver) {
        observers.add(observer)
    }

    override fun getSessionId(): String {
        val currentSession = session.get()

        val isExpiredDueToMaxLifetime = sessionHasExpired(currentSession)
        val isExpiredDueToBackgroundTimeout = timeoutHandler?.hasTimedOut() == true && currentSession.getStartTimestamp() >= 0
        val shouldCreateNew = isExpiredDueToMaxLifetime || isExpiredDueToBackgroundTimeout

        return if (shouldCreateNew) {
            val newId = idGenerator.generateSessionId()
            val newSession = Session.DefaultSession(newId, clock.now())

            // Atomically update the session only if it hasn't been changed by another thread.
            if (session.compareAndSet(currentSession, newSession)) {
                sessionStorage.save(newSession)

                val expiredRestored = expiredRestoredSession.getAndSet(Session.NONE)
                val previousSession =
                    if (expiredRestored.getId().isNotEmpty()) {
                        expiredRestored
                    } else {
                        currentSession
                    }

                val isPreviousSessionExpiredDueToMaxLifetime =
                    if (expiredRestored.getId().isNotEmpty()) {
                        true
                    } else {
                        isExpiredDueToMaxLifetime
                    }

                val isPreviousSessionExpiredInBackground =
                    if (expiredRestored.getId().isNotEmpty()) {
                        false
                    } else {
                        timeoutHandler?.getBackgroundStartTimeNanos() != null
                    }

                timeoutHandler?.bump()
                // Observers need to be called after bumping the timer because it may create a new
                // span.
                notifyObserversOfSessionUpdate(
                    previousSession,
                    newSession,
                    isPreviousSessionExpiredDueToMaxLifetime,
                    isPreviousSessionExpiredInBackground,
                )
                newSession.getId()
            } else {
                // Another thread accessed this function prior to creating a new session. Use the
                // current session.
                timeoutHandler?.bump()
                session.get().getId()
            }
        } else {
            // No new session needed, just bump the timeout and return current session ID
            timeoutHandler?.bump()
            currentSession.getId()
        }
    }

    private fun notifyObserversOfSessionUpdate(
        currentSession: Session,
        newSession: Session,
        expiredDueToMaxLifetime: Boolean,
        expiredInBackground: Boolean,
    ) {
        val expirationTimestampNanos =
            if (currentSession.getStartTimestamp() >= 0) {
                if (expiredInBackground) {
                    timeoutHandler?.getBackgroundStartTimeNanos() ?: clock.now()
                } else if (expiredDueToMaxLifetime) {
                    currentSession.getStartTimestamp() + maxSessionLifetime.inWholeNanoseconds
                } else {
                    clock.now()
                }
            } else {
                null
            }
        observers.forEach {
            it.onSessionEnded(currentSession, expirationTimestampNanos)
            it.onSessionStarted(newSession, currentSession)
        }
    }

    private fun sessionHasExpired(session: Session): Boolean {
        if (session.getStartTimestamp() < 0) {
            return true
        }
        val elapsedTime = clock.now() - session.getStartTimestamp()
        return elapsedTime >= maxSessionLifetime.inWholeNanoseconds
    }

    companion object {
        @OptIn(Incubating::class)
        @JvmStatic
        fun create(
            application: Context,
            timeoutHandler: SessionIdTimeoutHandler?,
            sessionConfig: SessionConfig,
            storageKey: String = "otel_session_storage",
        ): SessionManager {
            val sessionStorage: SessionStorage =
                if (sessionConfig.shouldPersist) {
                    val sharedPreferences =
                        application.getSharedPreferences(
                            storageKey,
                            Context.MODE_PRIVATE,
                        )
                    PersistentSessionStorage(sharedPreferences)
                } else {
                    InMemorySessionStorage()
                }

            // Set default to 4 hours if maxLifetime is null
            // This is necessary because SessionManager requires a non-null maxLifetime to function.
            val maxLifetime = sessionConfig.maxLifetime ?: 4.hours

            val handler =
                timeoutHandler ?: sessionConfig.backgroundInactivityTimeout?.let {
                    SessionIdTimeoutHandler(Clock.getDefault(), it)
                }

            return SessionManager(
                sessionStorage = sessionStorage,
                timeoutHandler = handler,
                maxSessionLifetime = maxLifetime,
            )
        }
    }
}
