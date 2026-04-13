import Foundation

/// Constants for location instrumentation 
public enum LocationConstants {
    /// UserDefaults key for cached location.
    public static let locationCacheKey = "location_cache"

    /// Default cache invalidation time: 1 hour.
    public static let defaultCacheInvalidationTime: TimeInterval = 60 * 60
}
