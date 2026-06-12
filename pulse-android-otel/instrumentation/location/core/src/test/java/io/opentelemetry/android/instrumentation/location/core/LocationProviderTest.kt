package io.opentelemetry.android.instrumentation.location.core

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Task
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.android.instrumentation.location.models.CachedLocation
import io.opentelemetry.android.instrumentation.location.models.LocationConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationProviderTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationProvider: LocationProvider
    private lateinit var geocoder: Geocoder

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
                fusedLocationClient = fusedLocationClient,
                geocoder = geocoder,
            )
    }

    @Test
    fun `cached location JSON can be deserialized correctly`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L},"countryIsoCode":"US","regionIsoCode":"US-CA","localityName":"San Francisco","postalCode":"94102"}"""
        every { sharedPreferences.getString("location_cache", null) } returns jsonString

        val cachedLocation = Json.decodeFromString<CachedLocation>(jsonString)

        assertThat(cachedLocation.latitude).isEqualTo(37.7749)
        assertThat(cachedLocation.longitude).isEqualTo(-122.4194)
        assertThat(cachedLocation.countryIsoCode).isEqualTo("US")
        assertThat(cachedLocation.regionIsoCode).isEqualTo("US-CA")
        assertThat(cachedLocation.localityName).isEqualTo("San Francisco")
        assertThat(cachedLocation.postalCode).isEqualTo("94102")
    }

    @Test
    fun `cached location JSON with only lat lon can be deserialized correctly`() {
        val currentTime = System.currentTimeMillis()
        val jsonString =
            """{"latitude":37.7749,"longitude":-122.4194,"timestamp":${currentTime - 1000L},"countryIsoCode":null,"regionIsoCode":null,"localityName":null,"postalCode":null}"""
        every { sharedPreferences.getString("location_cache", null) } returns jsonString

        val cachedLocation = Json.decodeFromString<CachedLocation>(jsonString)

        assertThat(cachedLocation.latitude).isEqualTo(37.7749)
        assertThat(cachedLocation.longitude).isEqualTo(-122.4194)
        assertThat(cachedLocation.countryIsoCode).isNull()
        assertThat(cachedLocation.regionIsoCode).isNull()
        assertThat(cachedLocation.localityName).isNull()
        assertThat(cachedLocation.postalCode).isNull()
    }

    @Test
    fun `when fused client returns null location, no location is saved to cache`() =
        runTest {
            val locationTask = mockk<Task<Location>>(relaxed = true)
            every { locationTask.isComplete } returns true
            every { locationTask.exception } returns null
            every { locationTask.isCanceled } returns false
            every { locationTask.result } returns null
            every { locationTask.addOnCompleteListener(any()) } returns locationTask
            every { fusedLocationClient.lastLocation } returns locationTask

            locationProvider.startPeriodicRefresh()

            delay(100)

            verify(exactly = 0) {
                sharedPreferencesEditor.putString(LocationConstants.LOCATION_CACHE_KEY, any())
            }
        }

    @Test
    fun `when fused client throws exception, error is handled gracefully and no location is saved`() =
        runTest {
            val exception = RuntimeException("Location service unavailable")
            val locationTask = mockk<Task<Location>>(relaxed = true)
            every { locationTask.isComplete } returns true
            every { locationTask.exception } returns exception
            every { locationTask.isCanceled } returns false
            every { locationTask.addOnCompleteListener(any()) } returns locationTask
            every { fusedLocationClient.lastLocation } returns locationTask

            locationProvider.startPeriodicRefresh()

            delay(100)

            verify(exactly = 0) {
                sharedPreferencesEditor.putString(LocationConstants.LOCATION_CACHE_KEY, any())
            }
        }
}
