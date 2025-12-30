/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.location

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * A [LogRecordProcessor] implementation that appends location attributes
 */
internal class LocationAttributesLogRecordAppender(
    private val locationProvider: LocationProvider,
) : LogRecordProcessor {
    override fun onEmit(
        context: Context,
        logRecord: ReadWriteLogRecord,
    ) {
        val locationAttributes = locationProvider.getLocationAttributes()
        logRecord.setAllAttributes(locationAttributes)
    }
}
