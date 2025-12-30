/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.location

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import com.google.android.gms.location.FusedLocationProviderClient
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.opentelemetry.semconv.incubating.GeoIncubatingAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationProviderTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private lateinit var locationProvider: LocationProvider

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)
        fusedLocationClient = mockk(relaxed = true)
        geocoder = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putString(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.apply() } just Runs

        locationProvider =
            LocationProvider(
                context,
                sharedPreferences,
                3600000L,
                geocoder = null,
            )
    }

    @Test
    fun `getLocationAttributes returns empty attributes when no cached location`() {
        every { sharedPreferences.getString("location_cache", null) } returns null

        val attributes = locationProvider.getLocationAttributes()

        assertThat(attributes.isEmpty).isTrue()
    }

    @Test
    fun `getLocationAttributes returns geo attributes when cache is valid`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L},"countryIsoCode":"US","regionIsoCode":"US-CA","localityName":"San Francisco","postalCode":"94102"}"""
        every { sharedPreferences.getString("location_cache", null) } returns jsonString

        val attributes = locationProvider.getLocationAttributes()

        assertThat(attributes.get(GeoIncubatingAttributes.GEO_LOCATION_LAT)).isEqualTo(37.7749)
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_LOCATION_LON)).isEqualTo(-122.4194)
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_COUNTRY_ISO_CODE)).isEqualTo("US")
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_REGION_ISO_CODE)).isEqualTo("US-CA")
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_LOCALITY_NAME)).isEqualTo("San Francisco")
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_POSTAL_CODE)).isEqualTo("94102")
    }

    @Test
    fun `getLocationAttributes returns geo attributes when cache is expired but data exists`() {
        val expiredTime = System.currentTimeMillis() - 7200000L // 2 hours ago
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":$expiredTime,"countryIsoCode":"US","regionIsoCode":"US-CA"}"""
        every { sharedPreferences.getString("location_cache", null) } returns jsonString

        val attributes = locationProvider.getLocationAttributes()

        // Even when expired, return cached attributes if available (will trigger refresh in background)
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_LOCATION_LAT)).isEqualTo(37.7749)
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_LOCATION_LON)).isEqualTo(-122.4194)
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_COUNTRY_ISO_CODE)).isEqualTo("US")
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_REGION_ISO_CODE)).isEqualTo("US-CA")
    }

    @Test
    fun `getLocationAttributes returns only lat lon when no geo data available`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L}}"""
        every { sharedPreferences.getString("location_cache", null) } returns jsonString

        val attributes = locationProvider.getLocationAttributes()

        assertThat(attributes.get(GeoIncubatingAttributes.GEO_LOCATION_LAT)).isEqualTo(37.7749)
        assertThat(attributes.get(GeoIncubatingAttributes.GEO_LOCATION_LON)).isEqualTo(-122.4194)
        assertThat(attributes.size()).isEqualTo(2)
    }
}
