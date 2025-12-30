/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.location

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationAttributesLogRecordAppenderTest {
    private lateinit var locationProvider: LocationProvider
    private lateinit var logAppender: LocationAttributesLogRecordAppender
    private lateinit var logRecord: ReadWriteLogRecord

    @BeforeEach
    fun setUp() {
        locationProvider = mockk(relaxed = true)
        logAppender = LocationAttributesLogRecordAppender(locationProvider)
        logRecord = mockk(relaxed = true)
        every { logRecord.setAllAttributes(any()) } returns logRecord
    }

    @Test
    fun `onEmit appends location attributes when available`() {
        val attributes =
            Attributes
                .builder()
                .put("geo.location.lat", 37.7749)
                .put("geo.location.lon", -122.4194)
                .put("geo.country.iso_code", "US")
                .put("geo.region.iso_code", "US-CA")
                .build()
        every { locationProvider.getLocationAttributes() } returns attributes

        logAppender.onEmit(Context.current(), logRecord)

        verify {
            logRecord.setAllAttributes(attributes)
        }
    }

    @Test
    fun `onEmit does not append attributes when location not available`() {
        every { locationProvider.getLocationAttributes() } returns Attributes.empty()

        logAppender.onEmit(Context.current(), logRecord)

        verify {
            logRecord.setAllAttributes(Attributes.empty())
        }
    }
}
