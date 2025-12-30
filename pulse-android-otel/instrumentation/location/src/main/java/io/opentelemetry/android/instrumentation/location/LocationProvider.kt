/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Address
import android.location.Geocoder
import android.location.Geocoder.GeocodeListener
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.pulse.otel.utils.PulseOtelUtils
import com.pulse.otel.utils.await
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.incubating.GeoIncubatingAttributes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationProvider
    @JvmOverloads
    constructor(
        private val context: Context,
        private val sharedPreferences: SharedPreferences,
        private val cacheInvalidationTimeMs: Long = DEFAULT_CACHE_INVALIDATION_TIME_MS,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : CoroutineScope by CoroutineScope(SupervisorJob() + dispatcher) {
        private var fusedLocationClient: FusedLocationProviderClient? = null
        private var geocoder: Geocoder? = null

        internal constructor(
            context: Context,
            sharedPreferences: SharedPreferences,
            cacheInvalidationTimeMs: Long,
            fusedLocationClient: FusedLocationProviderClient? = null,
            geocoder: Geocoder? = null,
        ) : this(
            context,
            sharedPreferences,
            cacheInvalidationTimeMs,
        ) {
            this.fusedLocationClient = fusedLocationClient
            this.geocoder = geocoder
        }

        private val actualFusedLocationClient: FusedLocationProviderClient by lazy {
            fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(context)
        }

        private val actualGeocoder: Geocoder by lazy {
            geocoder ?: Geocoder(context, Locale.getDefault())
        }

        fun getLocationAttributes(): Attributes {
            val cachedLocation = getCachedLocation()
            if (cachedLocation != null && !isCacheExpired(cachedLocation.timestamp)) {
                return buildLocationAttributes(cachedLocation)
            }

            fetchLocationAsync()
            return cachedLocation?.let { buildLocationAttributes(it) } ?: Attributes.empty()
        }

        private fun buildLocationAttributes(cachedLocation: CachedLocation): Attributes =
            Attributes
                .builder()
                .apply {
                    put(GeoIncubatingAttributes.GEO_LOCATION_LAT, cachedLocation.latitude)
                    put(GeoIncubatingAttributes.GEO_LOCATION_LON, cachedLocation.longitude)

                    cachedLocation.countryIsoCode?.let {
                        put(GeoIncubatingAttributes.GEO_COUNTRY_ISO_CODE, it)
                    }
                    cachedLocation.regionIsoCode?.let {
                        put(GeoIncubatingAttributes.GEO_REGION_ISO_CODE, it)
                    }
                    cachedLocation.localityName?.let {
                        put(GeoIncubatingAttributes.GEO_LOCALITY_NAME, it)
                    }
                    cachedLocation.postalCode?.let {
                        put(GeoIncubatingAttributes.GEO_POSTAL_CODE, it)
                    }
                }.build()

        private fun getCachedLocation(): CachedLocation? {
            val jsonString = sharedPreferences.getString(LOCATION_CACHE_KEY, null) ?: return null

            return try {
                json.decodeFromString<CachedLocation>(jsonString)
            } catch (_: Exception) {
                null
            }
        }

        private fun isCacheExpired(timestamp: Long): Boolean {
            val currentTime = System.currentTimeMillis()
            return currentTime - timestamp > cacheInvalidationTimeMs
        }

        private fun fetchLocationAsync() {
            val client = actualFusedLocationClient
            getFetchJob?.cancel()
            getFetchJob =
                launch {
                    @Suppress("TooGenericExceptionCaught") // async network call can throw any exception
                    try {
                        val location = client.lastLocation.await()
                        if (location != null) {
                            saveLocationToCache(location)
                            convertToGeoAttributes(location)
                        }
                    } catch (e: Throwable) {
                        currentCoroutineContext().ensureActive()
                        PulseOtelUtils.logError(TAG, e) {
                            "fetchLocationAsync lastLocation task failed"
                        }
                    }
                }
        }

        private fun saveLocationToCache(location: Location) {
            val cachedLocation =
                CachedLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    // Geo attributes will be updated later by convertToGeoAttributes
                )
            val jsonString = json.encodeToString(cachedLocation)
            sharedPreferences.edit {
                putString(LOCATION_CACHE_KEY, jsonString)
            }
        }

        private var getFetchJob: Job? = null

        private fun convertToGeoAttributes(location: Location) {
            val geocoderInstance = actualGeocoder
            // Run geocoding in background thread
            getFetchJob?.cancel()
            getFetchJob =
                launch {
                    try {
                        val addresses = getFromLocationWithListener(geocoderInstance, location.latitude, location.longitude)
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val geoAttributes = extractGeoAttributes(address)

                            // Update the cached location with geo attributes
                            val currentCached = getCachedLocation()
                            if (currentCached != null) {
                                val updatedCached =
                                    currentCached.copy(
                                        countryIsoCode = geoAttributes.countryIsoCode,
                                        regionIsoCode = geoAttributes.regionIsoCode,
                                        localityName = geoAttributes.localityName,
                                        postalCode = geoAttributes.postalCode,
                                    )
                                val jsonString = json.encodeToString(updatedCached)
                                sharedPreferences.edit {
                                    putString(LOCATION_CACHE_KEY, jsonString)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        currentCoroutineContext().ensureActive()
                        PulseOtelUtils.logError(TAG, e) {
                            "convertToGeoAttributes geo call failed"
                        }
                    }
                }
        }

        @SuppressLint("ObsoleteSdkInt")
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private suspend fun getFromLocationWithListener(
            geocoder: Geocoder,
            latitude: Double,
            longitude: Double,
        ): List<Address> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        @Suppress("NewApi")
                        geocoder.getFromLocation(
                            latitude,
                            longitude,
                            1,
                            object : GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    continuation.resume(addresses)
                                }

                                override fun onError(errorMessage: String?) {
                                    continuation.resumeWithException(
                                        RuntimeException("Geocoding failed: ${errorMessage ?: "no-err-msg"}"),
                                    )
                                }
                            },
                        )
                    } catch (e: IllegalArgumentException) {
                        PulseOtelUtils.logError(TAG, e) {
                            "getFromLocationWithListener failed with IllegalArgumentException"
                        }
                    }
                    continuation.invokeOnCancellation {
                        // no-op
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
            }

        private fun extractGeoAttributes(address: Address): GeoAttributes {
            val countryCode = address.countryCode
            val adminArea = address.adminArea
            val locality = address.locality
            val postalCode = address.postalCode

            val regionIsoCode =
                if (countryCode != null && adminArea != null) {
                    formatRegionIsoCode(countryCode, adminArea)
                } else {
                    null
                }

            return GeoAttributes(
                countryIsoCode = countryCode,
                regionIsoCode = regionIsoCode,
                localityName = locality,
                postalCode = postalCode,
            )
        }

        private fun formatRegionIsoCode(
            countryCode: String,
            adminArea: String,
        ): String {
            // ISO 3166-2 format: CountryCode-SubdivisionCode
            // For simplicity, we'll use country code and first part of admin area
            val subdivisionCode = adminArea.split(" ")[0].uppercase()
            return "$countryCode-$subdivisionCode"
        }

        private data class GeoAttributes(
            val countryIsoCode: String?,
            val regionIsoCode: String?,
            val localityName: String?,
            val postalCode: String?,
        )

        @Serializable
        private data class CachedLocation(
            val latitude: Double,
            val longitude: Double,
            val timestamp: Long,
            val countryIsoCode: String? = null,
            val regionIsoCode: String? = null,
            val localityName: String? = null,
            val postalCode: String? = null,
        )

        companion object {
            private const val TAG = "LocationProvider"
            private const val LOCATION_CACHE_KEY = "location_cache"
            internal val DEFAULT_CACHE_INVALIDATION_TIME_MS = TimeUnit.HOURS.toMillis(1)
            private val json = Json { ignoreUnknownKeys = true }
        }
    }
