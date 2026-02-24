/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.internal.processors

import io.opentelemetry.android.common.RumConstants.Events.EVENT_SESSION_END
import io.opentelemetry.android.common.RumConstants.Events.EVENT_SESSION_START
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_ID

internal class SessionIdLogRecordAppender(
    private val sessionProvider: SessionProvider,
) : LogRecordProcessor {
    override fun onEmit(
        context: Context,
        logRecord: ReadWriteLogRecord,
    ) {
        val eventName = logRecord.eventName
        if (eventName == EVENT_SESSION_START || eventName == EVENT_SESSION_END) {
            return
        }
        logRecord.setAttribute(SESSION_ID, sessionProvider.getSessionId())
    }
}
