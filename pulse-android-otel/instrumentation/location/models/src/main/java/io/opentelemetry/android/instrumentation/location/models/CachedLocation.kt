package io.opentelemetry.android.instrumentation.location.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Keep
@Serializable
public class CachedLocation(
    public val latitude: Double,
    public val longitude: Double,
    public val timestamp: Long,
    public val countryIsoCode: String?,
    public val regionIsoCode: String?,
    public val localityName: String?,
    public val postalCode: String?,
) {
    /**
     * Checks if the cached location has expired based on the cache invalidation time.
     *
     * @param cacheInvalidationTimeMs The cache invalidation time in milliseconds
     * @return true if the cache is expired, false otherwise
     */
    public fun isExpired(cacheInvalidationTimeMs: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - timestamp > cacheInvalidationTimeMs
    }
}

public object LocationConstants {
    public const val LOCATION_CACHE_KEY: String = "location_cache"

    /**
     * Default cache invalidation time: 1 hour
     */
    public val DEFAULT_CACHE_INVALIDATION_TIME_MS: Long =
        if (BuildConfig.DEBUG) TimeUnit.MINUTES.toMillis(1) else TimeUnit.HOURS.toMillis(1)
}
