/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.session

interface Session {
    fun getId(): String

    fun getStartTimestamp(): Long

    companion object {
        val NONE = DefaultSession("", -1)
    }

    // suppressing so that end users apis are convenient
    @Suppress("ForbiddenPublicDataClass")
    data class DefaultSession(
        private val id: String,
        private val startTimestampNanos: Long,
    ) : Session {
        override fun getId(): String = id

        override fun getStartTimestamp(): Long = startTimestampNanos
    }
}
