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
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.trace.ReadWriteSpan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationAttributesSpanAppenderTest {
    private lateinit var locationProvider: LocationProvider
    private lateinit var spanAppender: LocationAttributesSpanAppender
    private lateinit var span: ReadWriteSpan

    @BeforeEach
    fun setUp() {
        locationProvider = mockk(relaxed = true)
        spanAppender = LocationAttributesSpanAppender(locationProvider)
        span = mockk(relaxed = true)
        every { span.setAllAttributes(any()) } returns span
    }

    @Test
    fun `onStart appends location attributes when available`() {
        val attributes =
            Attributes
                .builder()
                .put("geo.location.lat", 37.7749)
                .put("geo.location.lon", -122.4194)
                .put("geo.country.iso_code", "US")
                .put("geo.region.iso_code", "US-CA")
                .build()
        every { locationProvider.getLocationAttributes() } returns attributes

        spanAppender.onStart(Context.current(), span)

        verify {
            span.setAllAttributes(attributes)
        }
        assertThat(spanAppender.isStartRequired).isTrue()
        assertThat(spanAppender.isEndRequired).isFalse()
    }

    @Test
    fun `onStart does not append attributes when location not available`() {
        every { locationProvider.getLocationAttributes() } returns Attributes.empty()

        spanAppender.onStart(Context.current(), span)

        verify {
            span.setAllAttributes(Attributes.empty())
        }
    }

    @Test
    fun `onEnd is no-op`() {
        val readableSpan = mockk<io.opentelemetry.sdk.trace.ReadableSpan>(relaxed = true)

        spanAppender.onEnd(readableSpan)
    }
}
