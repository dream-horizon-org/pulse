import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// LogRecordProcessor that appends location attributes to log records by reading from the same cache as LocationProvider.
public final class LocationAttributesLogRecordProcessor: LogRecordProcessor {

    private let nextProcessor: LogRecordProcessor
    private let userDefaults: UserDefaults
    private let cacheKey: String
    private let cacheInvalidationTime: TimeInterval

    public init(
        nextProcessor: LogRecordProcessor,
        userDefaults: UserDefaults = .standard,
        cacheKey: String = LocationConstants.locationCacheKey,
        cacheInvalidationTime: TimeInterval = LocationConstants.defaultCacheInvalidationTime
    ) {
        self.nextProcessor = nextProcessor
        self.userDefaults = userDefaults
        self.cacheKey = cacheKey
        self.cacheInvalidationTime = cacheInvalidationTime
    }

    public func onEmit(logRecord: ReadableLogRecord) {
        var enhancedRecord = logRecord
        let attrs = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: cacheInvalidationTime
        )
        for (key, value) in attrs {
            enhancedRecord.setAttribute(key: key, value: value)
        }
        nextProcessor.onEmit(logRecord: enhancedRecord)
    }

    public func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.shutdown(explicitTimeout: explicitTimeout)
    }

    public func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.forceFlush(explicitTimeout: explicitTimeout)
    }
}
