package io.opentelemetry.android.instrumentation.location.processors

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.android.instrumentation.location.models.LocationConstants
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.semconv.incubating.GeoIncubatingAttributes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationAttributesSpanAppenderTest {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var spanAppender: LocationAttributesSpanAppender
    private lateinit var span: ReadWriteSpan

    @BeforeEach
    fun setUp() {
        sharedPreferences = mockk(relaxed = true)
        spanAppender = LocationAttributesSpanAppender(sharedPreferences)
        span = mockk(relaxed = true)
        every { span.setAllAttributes(any()) } returns span
    }

    @Test
    fun `onStart appends location attributes when cached location available`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L},"countryIsoCode":"US","regionIsoCode":"US-CA","localityName":"San Francisco","postalCode":"94102"}"""
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns jsonString

        var capturedAttributes: io.opentelemetry.api.common.Attributes? = null
        every { span.setAllAttributes(any()) } answers {
            capturedAttributes = firstArg()
            span
        }

        spanAppender.onStart(Context.current(), span)

        verify {
            span.setAllAttributes(any())
        }

        assertThat(capturedAttributes!!.size()).isEqualTo(6)
        assertThat(capturedAttributes)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LAT, 37.7749)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LON, -122.4194)
            .containsEntry(GeoIncubatingAttributes.GEO_COUNTRY_ISO_CODE, "US")
            .containsEntry(GeoIncubatingAttributes.GEO_REGION_ISO_CODE, "US-CA")
            .containsEntry(GeoIncubatingAttributes.GEO_LOCALITY_NAME, "San Francisco")
            .containsEntry(GeoIncubatingAttributes.GEO_POSTAL_CODE, "94102")

        assertThat(spanAppender.isStartRequired).isTrue()
        assertThat(spanAppender.isEndRequired).isFalse()
    }

    @Test
    fun `onStart appends only lat lon when partial location data available`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L},"countryIsoCode":null,"regionIsoCode":null,"localityName":null,"postalCode":null}"""
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns jsonString

        var capturedAttributes: io.opentelemetry.api.common.Attributes? = null
        every { span.setAllAttributes(any()) } answers {
            capturedAttributes = firstArg()
            span
        }

        spanAppender.onStart(Context.current(), span)

        verify {
            span.setAllAttributes(any())
        }

        assertThat(capturedAttributes!!.size()).isEqualTo(2)
        assertThat(capturedAttributes)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LAT, 37.7749)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LON, -122.4194)
    }

    @Test
    fun `onStart does not append attributes when location not cached`() {
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns null

        spanAppender.onStart(Context.current(), span)

        verify(exactly = 0) {
            span.setAllAttributes(any())
        }
    }

    @Test
    fun `onStart does not append attributes when cached location is invalid json`() {
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns "invalid json"

        spanAppender.onStart(Context.current(), span)

        verify(exactly = 0) {
            span.setAllAttributes(any())
        }
    }

    @Test
    fun `onEnd is no-op`() {
        val readableSpan = mockk<io.opentelemetry.sdk.trace.ReadableSpan>(relaxed = true)

        spanAppender.onEnd(readableSpan)
    }
}
