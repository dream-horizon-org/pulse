package io.opentelemetry.android.instrumentation.location.processors

import android.content.SharedPreferences
import io.opentelemetry.android.instrumentation.location.models.CachedLocation
import io.opentelemetry.android.instrumentation.location.models.LocationConstants
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.incubating.GeoIncubatingAttributes
import kotlinx.serialization.json.Json

/**
 * Internal utility class for location attributes processing.
 * This class is not part of the public API and should not be used outside this module.
 */
internal object LocationAttributesUtils {
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

    /**
     * Retrieves location attributes from the cache in SharedPreferences.
     * Returns empty attributes if cache is null or expired.
     */
    fun getLocationAttributesFromCache(sharedPreferences: SharedPreferences): Attributes {
        val cachedLocation = getCachedLocation(sharedPreferences)

        if (
            cachedLocation == null ||
            cachedLocation.isExpired(LocationConstants.DEFAULT_CACHE_INVALIDATION_TIME_MS)
        ) {
            return Attributes.empty()
        }

        return buildLocationAttributes(cachedLocation)
    }

    /**
     * Retrieves the cached location from SharedPreferences.
     * Returns null if not found or if deserialization fails.
     */
    private fun getCachedLocation(sharedPreferences: SharedPreferences): CachedLocation? {
        val jsonString = sharedPreferences.getString(LocationConstants.LOCATION_CACHE_KEY, null) ?: return null

        return try {
            json.decodeFromString<CachedLocation>(jsonString)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds OpenTelemetry attributes from a CachedLocation.
     */
    private fun buildLocationAttributes(cachedLocation: CachedLocation): Attributes =
        Attributes
            .builder()
            .apply {
                put(
                    GeoIncubatingAttributes.GEO_LOCATION_LAT,
                    cachedLocation.latitude,
                )
                put(
                    GeoIncubatingAttributes.GEO_LOCATION_LON,
                    cachedLocation.longitude,
                )

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
}
