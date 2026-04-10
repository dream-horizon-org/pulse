import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// SpanProcessor that appends location attributes to every span by reading from the same cache as LocationProvider.
public final class LocationAttributesSpanAppender: SpanProcessor {

    private let userDefaults: UserDefaults
    private let cacheKey: String
    private let cacheInvalidationTime: TimeInterval

    public var isStartRequired: Bool = true
    public var isEndRequired: Bool = false

    public init(
        userDefaults: UserDefaults = .standard,
        cacheKey: String = LocationConstants.locationCacheKey,
        cacheInvalidationTime: TimeInterval = LocationConstants.defaultCacheInvalidationTime
    ) {
        self.userDefaults = userDefaults
        self.cacheKey = cacheKey
        self.cacheInvalidationTime = cacheInvalidationTime
    }

    public func onStart(parentContext: SpanContext?, span: ReadableSpan) {
        let attrs = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: cacheInvalidationTime
        )
        for (key, value) in attrs {
            span.setAttribute(key: key, value: value)
        }
    }

    public func onEnd(span: ReadableSpan) {}

    public func shutdown(explicitTimeout: TimeInterval?) {}

    public func forceFlush(timeout: TimeInterval?) {}
}
