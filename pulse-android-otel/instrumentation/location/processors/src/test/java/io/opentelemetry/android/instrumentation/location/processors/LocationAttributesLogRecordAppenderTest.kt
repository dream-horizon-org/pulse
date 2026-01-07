package io.opentelemetry.android.instrumentation.location.processors

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.opentelemetry.android.instrumentation.location.models.LocationConstants
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.semconv.incubating.GeoIncubatingAttributes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationAttributesLogRecordAppenderTest {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var logAppender: LocationAttributesLogRecordAppender
    private lateinit var logRecord: ReadWriteLogRecord

    @BeforeEach
    fun setUp() {
        sharedPreferences = mockk(relaxed = true)
        logAppender = LocationAttributesLogRecordAppender(sharedPreferences)
        logRecord = mockk(relaxed = true)
        every { logRecord.setAllAttributes(any()) } returns logRecord
    }

    @Test
    fun `onEmit appends location attributes when cached location available`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L},"countryIsoCode":"US","regionIsoCode":"US-CA","localityName":"San Francisco","postalCode":"94102"}"""
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns jsonString

        val capturedAttributes = slot<Attributes>()
        logAppender.onEmit(Context.current(), logRecord)

        verify {
            logRecord.setAllAttributes(capture(capturedAttributes))
        }

        assertThat(capturedAttributes.captured.size()).isEqualTo(6)
        assertThat(capturedAttributes.captured)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LAT, 37.7749)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LON, -122.4194)
            .containsEntry(GeoIncubatingAttributes.GEO_COUNTRY_ISO_CODE, "US")
            .containsEntry(GeoIncubatingAttributes.GEO_REGION_ISO_CODE, "US-CA")
            .containsEntry(GeoIncubatingAttributes.GEO_LOCALITY_NAME, "San Francisco")
            .containsEntry(GeoIncubatingAttributes.GEO_POSTAL_CODE, "94102")
    }

    @Test
    fun `onEmit appends only lat lon when partial location data available`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L},"countryIsoCode":null,"regionIsoCode":null,"localityName":null,"postalCode":null}"""
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns jsonString

        val capturedAttributes = slot<Attributes>()

        logAppender.onEmit(Context.current(), logRecord)

        verify {
            logRecord.setAllAttributes(capture(capturedAttributes))
        }

        assertThat(capturedAttributes.captured.size()).isEqualTo(2)
        assertThat(capturedAttributes.captured)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LAT, 37.7749)
            .containsEntry(GeoIncubatingAttributes.GEO_LOCATION_LON, -122.4194)
    }

    @Test
    fun `onEmit does not append attributes when location not cached`() {
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns null

        logAppender.onEmit(Context.current(), logRecord)

        verify(exactly = 0) {
            logRecord.setAllAttributes(any())
        }
    }

    @Test
    fun `onEmit does not append attributes when cached location is invalid json`() {
        every { sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) } returns "invalid json"

        logAppender.onEmit(Context.current(), logRecord)

        verify(exactly = 0) {
            logRecord.setAllAttributes(any())
        }
    }
}
