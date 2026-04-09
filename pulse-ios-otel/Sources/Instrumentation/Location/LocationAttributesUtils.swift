/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// OpenTelemetry Geo semantic convention attribute names (incubating).
/// See https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/
enum GeoAttributes {
    static let geoLocationLat = "geo.location.lat"
    static let geoLocationLon = "geo.location.lon"
    static let geoCountryIsoCode = "geo.country.iso_code"
    static let geoRegionIsoCode = "geo.region.iso_code"
    static let geoLocalityName = "geo.locality.name"
    static let geoPostalCode = "geo.postal_code"
}

/// In-memory cache holder for CachedLocation - avoids repeated UserDefaults reads.
/// No locking required - single writer (LocationProvider), multiple readers (processors).
final class CachedLocationSaver {
    static let shared = CachedLocationSaver()
    private init() {}
    
    var cachedLocation: CachedLocation?
}

/// Internal utility for reading location attributes from cache
enum LocationAttributesUtils {
    /// Retrieves location attributes from the in-memory cache or UserDefaults.
    /// Returns empty dictionary if cache is null or expired.
    /// Prioritizes in-memory cache (cachedLocationSaver) over UserDefaults for performance.
    static func getLocationAttributesFromCache(
        userDefaults: UserDefaults = .standard,
        cacheKey: String = LocationConstants.locationCacheKey,
        cacheInvalidationTime: TimeInterval = LocationConstants.defaultCacheInvalidationTime
    ) -> [String: AttributeValue] {
        // Try in-memory cache first (fast path)
        if let memCached = CachedLocationSaver.shared.cachedLocation,
           !memCached.isExpired(cacheInvalidationTime) {
            return buildLocationAttributes(memCached)
        }
        
        // Fallback to UserDefaults if in-memory cache is null or expired
        guard let data = userDefaults.data(forKey: cacheKey),
              let cached = try? JSONDecoder().decode(CachedLocation.self, from: data),
              !cached.isExpired(cacheInvalidationTime) else {
            return [:]
        }
        
        // Update in-memory cache from UserDefaults
        CachedLocationSaver.shared.cachedLocation = cached
        return buildLocationAttributes(cached)
    }

    private static func buildLocationAttributes(_ cached: CachedLocation) -> [String: AttributeValue] {
        var attrs: [String: AttributeValue] = [
            GeoAttributes.geoLocationLat: AttributeValue.double(cached.latitude),
            GeoAttributes.geoLocationLon: AttributeValue.double(cached.longitude)
        ]
        if let v = cached.countryIsoCode { attrs[GeoAttributes.geoCountryIsoCode] = .string(v) }
        if let v = cached.regionIsoCode { attrs[GeoAttributes.geoRegionIsoCode] = .string(v) }
        if let v = cached.localityName { attrs[GeoAttributes.geoLocalityName] = .string(v) }
        if let v = cached.postalCode { attrs[GeoAttributes.geoPostalCode] = .string(v) }
        return attrs
    }
}
