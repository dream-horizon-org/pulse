/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.session

import android.content.SharedPreferences
import androidx.core.content.edit
import io.opentelemetry.android.session.Session

internal class PersistentSessionStorage(
    private val sharedPreferences: SharedPreferences,
) : SessionStorage {
    companion object {
        private const val KEY_SESSION_ID = "otel-session-id"
        private const val KEY_SESSION_START_TIMESTAMP = "otel-session-start-timestamp"
    }

    override fun get(): Session {
        val id = sharedPreferences.getString(KEY_SESSION_ID, null) ?: return Session.NONE
        val startTimestamp = sharedPreferences.getLong(KEY_SESSION_START_TIMESTAMP, -1)

        if (startTimestamp == -1L) {
            return Session.NONE
        }

        return Session.DefaultSession(id, startTimestamp)
    }

    override fun save(newSession: Session) {
        sharedPreferences.edit {
            if (newSession.getId().isBlank()) {
                remove(KEY_SESSION_ID)
                remove(KEY_SESSION_START_TIMESTAMP)
            } else {
                putString(KEY_SESSION_ID, newSession.getId())
                putLong(KEY_SESSION_START_TIMESTAMP, newSession.getStartTimestamp())
            }
        }
    }

    fun teardown() {
        sharedPreferences.edit {
            remove(KEY_SESSION_ID)
            remove(KEY_SESSION_START_TIMESTAMP)
        }
    }
}
