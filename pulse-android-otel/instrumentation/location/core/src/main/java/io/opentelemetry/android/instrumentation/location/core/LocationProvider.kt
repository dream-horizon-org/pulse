package io.opentelemetry.android.instrumentation.location.core

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
import com.pulse.utils.BuildConfig
import io.opentelemetry.android.instrumentation.location.models.CachedLocation
import io.opentelemetry.android.instrumentation.location.models.LocationConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

public class LocationProvider
    @JvmOverloads
    constructor(
        private val context: Context,
        private val sharedPreferences: SharedPreferences,
        private val cacheInvalidationTimeMs: Long = LocationConstants.DEFAULT_CACHE_INVALIDATION_TIME_MS,
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

        private fun getCachedLocation(): CachedLocation? {
            val jsonString = sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) ?: return null

            return try {
                json.decodeFromString<CachedLocation>(jsonString)
            } catch (_: Exception) {
                null
            }
        }

        private suspend fun fetchLocationAsync() {
            val client = actualFusedLocationClient
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

        private fun saveLocationToCache(location: Location) {
            val cachedLocation =
                CachedLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    // Geo attributes will be updated later by convertToGeoAttributes
                    countryIsoCode = null,
                    regionIsoCode = null,
                    localityName = null,
                    postalCode = null,
                )
            val jsonString = json.encodeToString(cachedLocation)
            sharedPreferences.edit { putString(LocationConstants.LOCATION_CACHE_KEY, jsonString) }
        }

        private var periodicRefreshJob: Job? = null

        /**
         * Starts periodic location refresh that updates location every [cacheInvalidationTimeMs].
         * Only fetches location immediately if cache is expired or missing.
         */
        public fun startPeriodicRefresh() {
            stopPeriodicRefresh()

            // Check if cache is valid before fetching
            val cachedLocation = getCachedLocation()
            val shouldFetchNow = cachedLocation == null || cachedLocation.isExpired(cacheInvalidationTimeMs)

            periodicRefreshJob =
                launch(dispatcher) {
                    // Fetch immediately only if cache is expired or missing
                    if (shouldFetchNow) {
                        fetchLocationAsync()
                    }

                    while (isActive) {
                        delay(cacheInvalidationTimeMs)
                        ensureActive()
                        fetchLocationAsync()
                    }
                }
        }

        /**
         * Stops the periodic location refresh handler.
         */
        public fun stopPeriodicRefresh() {
            periodicRefreshJob?.cancel()
            periodicRefreshJob = null
        }

        private suspend fun convertToGeoAttributes(location: Location) {
            val geocoderInstance = actualGeocoder
            // Run geocoding in background thread
            try {
                val addresses = getFromLocationWithListener(geocoderInstance, location.latitude, location.longitude)
                if (addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val geoAttributes = extractGeoAttributes(address)

                    // Update the cached location with geo attributes
                    val currentCached = getCachedLocation()
                    if (currentCached != null) {
                        val updatedCached =
                            CachedLocation(
                                latitude = currentCached.latitude,
                                longitude = currentCached.longitude,
                                timestamp = currentCached.timestamp,
                                countryIsoCode = geoAttributes.countryIsoCode,
                                regionIsoCode = geoAttributes.regionIsoCode,
                                localityName = geoAttributes.localityName,
                                postalCode = geoAttributes.postalCode,
                            )
                        val jsonString = json.encodeToString(updatedCached)
                        sharedPreferences.edit { putString(LocationConstants.LOCATION_CACHE_KEY, jsonString) }
                    }
                }
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                PulseOtelUtils.logError(TAG, e) {
                    "convertToGeoAttributes geo call failed"
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

        private companion object {
            private const val TAG = "LocationProvider"
            private val json =
                Json {
                    encodeDefaults = true
                    explicitNulls = false
                    ignoreUnknownKeys = !BuildConfig.DEBUG
                    prettyPrint = BuildConfig.DEBUG
                    isLenient = !BuildConfig.DEBUG
                    allowSpecialFloatingPointValues = true
                    useAlternativeNames = true
                }
        }
    }
