import XCTest
@testable import PulseKit
import OpenTelemetryApi
import OpenTelemetrySdk

final class LocationAttributesLogRecordProcessorTest: XCTestCase {
    private var userDefaults: UserDefaults!
    private var suiteName: String!
    private var processor: LocationAttributesLogRecordProcessor!
    private var mockNextProcessor: MockLogRecordProcessor!

    override func setUp() {
        super.setUp()
        suiteName = "LocationAttributesLogRecordProcessorTest-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)!
        mockNextProcessor = MockLogRecordProcessor()
        CachedLocationSaver.shared.cachedLocation = nil
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        CachedLocationSaver.shared.cachedLocation = nil
        super.tearDown()
    }

    func testOnEmitAppendsLocationAttributesWhenCachedLocationAvailable() throws {
        let cacheKey = LocationConstants.locationCacheKey
        let currentTime = Date().timeIntervalSince1970
        let location = CachedLocation(
            latitude: 37.7749,
            longitude: -122.4194,
            timestamp: currentTime - 1,
            countryIsoCode: "US",
            regionIsoCode: "US-CA",
            localityName: "San Francisco",
            postalCode: "94102"
        )
        let data = try JSONEncoder().encode(location)
        userDefaults.set(data, forKey: cacheKey)

        processor = LocationAttributesLogRecordProcessor(
            nextProcessor: mockNextProcessor,
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: 3600
        )

        processor.onEmit(logRecord: makeTestLogRecord())

        XCTAssertEqual(mockNextProcessor.emitCallCount, 1)
        let attrs = mockNextProcessor.lastEmittedRecord!.attributes
        XCTAssertEqual(attrs.count, 6)
        if case let .double(lat) = attrs["geo.location.lat"] { XCTAssertEqual(lat, 37.7749, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lat") }
        if case let .double(lon) = attrs["geo.location.lon"] { XCTAssertEqual(lon, -122.4194, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lon") }
        if case let .string(v) = attrs["geo.country.iso_code"] { XCTAssertEqual(v, "US") } else { XCTFail("Expected geo.country.iso_code") }
        if case let .string(v) = attrs["geo.region.iso_code"] { XCTAssertEqual(v, "US-CA") } else { XCTFail("Expected geo.region.iso_code") }
        if case let .string(v) = attrs["geo.locality.name"] { XCTAssertEqual(v, "San Francisco") } else { XCTFail("Expected geo.locality.name") }
        if case let .string(v) = attrs["geo.postal_code"] { XCTAssertEqual(v, "94102") } else { XCTFail("Expected geo.postal_code") }
    }

    func testOnEmitAppendsOnlyLatLonWhenPartialLocationDataAvailable() throws {
        let cacheKey = LocationConstants.locationCacheKey
        let currentTime = Date().timeIntervalSince1970
        let location = CachedLocation(
            latitude: 37.7749,
            longitude: -122.4194,
            timestamp: currentTime - 1,
            countryIsoCode: nil,
            regionIsoCode: nil,
            localityName: nil,
            postalCode: nil
        )
        let data = try JSONEncoder().encode(location)
        userDefaults.set(data, forKey: cacheKey)

        processor = LocationAttributesLogRecordProcessor(
            nextProcessor: mockNextProcessor,
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: 3600
        )

        processor.onEmit(logRecord: makeTestLogRecord())

        XCTAssertEqual(mockNextProcessor.emitCallCount, 1)
        let attrs = mockNextProcessor.lastEmittedRecord!.attributes
        XCTAssertEqual(attrs.count, 2)
        if case let .double(lat) = attrs["geo.location.lat"] { XCTAssertEqual(lat, 37.7749, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lat") }
        if case let .double(lon) = attrs["geo.location.lon"] { XCTAssertEqual(lon, -122.4194, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lon") }
    }

    func testOnEmitDoesNotAppendAttributesWhenLocationNotCached() {
        processor = LocationAttributesLogRecordProcessor(
            nextProcessor: mockNextProcessor,
            userDefaults: userDefaults,
            cacheKey: LocationConstants.locationCacheKey,
            cacheInvalidationTime: 3600
        )

        processor.onEmit(logRecord: makeTestLogRecord())

        XCTAssertEqual(mockNextProcessor.emitCallCount, 1)
        XCTAssertTrue(mockNextProcessor.lastEmittedRecord!.attributes.isEmpty)
    }

    func testOnEmitDoesNotAppendAttributesWhenCachedLocationIsInvalidJson() {
        let cacheKey = LocationConstants.locationCacheKey
        userDefaults.set("invalid json".data(using: .utf8), forKey: cacheKey)

        processor = LocationAttributesLogRecordProcessor(
            nextProcessor: mockNextProcessor,
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: 3600
        )

        processor.onEmit(logRecord: makeTestLogRecord())

        XCTAssertEqual(mockNextProcessor.emitCallCount, 1)
        XCTAssertTrue(mockNextProcessor.lastEmittedRecord!.attributes.isEmpty)
    }

    func testLogProcessorShutdownAndFlush() {
        processor = LocationAttributesLogRecordProcessor(
            nextProcessor: mockNextProcessor,
            userDefaults: userDefaults,
            cacheKey: LocationConstants.locationCacheKey,
            cacheInvalidationTime: 3600
        )
        XCTAssertEqual(processor.shutdown(explicitTimeout: nil), ExportResult.success)
        XCTAssertEqual(processor.forceFlush(explicitTimeout: nil), ExportResult.success)
    }
}
