import XCTest
@testable import PulseKit
import OpenTelemetryApi
import OpenTelemetrySdk

final class LocationAttributesSpanAppenderTest: XCTestCase {
    private var userDefaults: UserDefaults!
    private var suiteName: String!
    private var spanAppender: LocationAttributesSpanAppender!
    private var mockSpan: MockReadableSpan!

    override func setUp() {
        super.setUp()
        suiteName = "LocationAttributesSpanAppenderTest-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)!
        mockSpan = MockReadableSpan()
        CachedLocationSaver.shared.cachedLocation = nil
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        CachedLocationSaver.shared.cachedLocation = nil
        super.tearDown()
    }

    func testOnStartAppendsLocationAttributesWhenCachedLocationAvailable() throws {
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

        spanAppender = LocationAttributesSpanAppender(
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: 3600
        )

        spanAppender.onStart(parentContext: nil, span: mockSpan)

        XCTAssertEqual(mockSpan.capturedAttributes.count, 6)
        if case let .double(lat) = mockSpan.capturedAttributes["geo.location.lat"] { XCTAssertEqual(lat, 37.7749, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lat") }
        if case let .double(lon) = mockSpan.capturedAttributes["geo.location.lon"] { XCTAssertEqual(lon, -122.4194, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lon") }
        if case let .string(v) = mockSpan.capturedAttributes["geo.country.iso_code"] { XCTAssertEqual(v, "US") } else { XCTFail("Expected geo.country.iso_code") }
        if case let .string(v) = mockSpan.capturedAttributes["geo.region.iso_code"] { XCTAssertEqual(v, "US-CA") } else { XCTFail("Expected geo.region.iso_code") }
        if case let .string(v) = mockSpan.capturedAttributes["geo.locality.name"] { XCTAssertEqual(v, "San Francisco") } else { XCTFail("Expected geo.locality.name") }
        if case let .string(v) = mockSpan.capturedAttributes["geo.postal_code"] { XCTAssertEqual(v, "94102") } else { XCTFail("Expected geo.postal_code") }
        XCTAssertTrue(spanAppender.isStartRequired)
        XCTAssertFalse(spanAppender.isEndRequired)
    }

    func testOnStartAppendsOnlyLatLonWhenPartialLocationDataAvailable() throws {
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

        spanAppender = LocationAttributesSpanAppender(
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: 3600
        )

        spanAppender.onStart(parentContext: nil, span: mockSpan)

        XCTAssertEqual(mockSpan.capturedAttributes.count, 2)
        if case let .double(lat) = mockSpan.capturedAttributes["geo.location.lat"] { XCTAssertEqual(lat, 37.7749, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lat") }
        if case let .double(lon) = mockSpan.capturedAttributes["geo.location.lon"] { XCTAssertEqual(lon, -122.4194, accuracy: 0.0001) } else { XCTFail("Expected geo.location.lon") }
    }

    func testOnStartDoesNotAppendAttributesWhenLocationNotCached() {
        spanAppender = LocationAttributesSpanAppender(
            userDefaults: userDefaults,
            cacheKey: LocationConstants.locationCacheKey,
            cacheInvalidationTime: 3600
        )

        spanAppender.onStart(parentContext: nil, span: mockSpan)

        XCTAssertTrue(mockSpan.capturedAttributes.isEmpty)
    }

    func testOnStartDoesNotAppendAttributesWhenCachedLocationIsInvalidJson() {
        let cacheKey = LocationConstants.locationCacheKey
        userDefaults.set("invalid json".data(using: .utf8), forKey: cacheKey)

        spanAppender = LocationAttributesSpanAppender(
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: 3600
        )

        spanAppender.onStart(parentContext: nil, span: mockSpan)

        XCTAssertTrue(mockSpan.capturedAttributes.isEmpty)
    }

    func testOnEndIsNoOp() {
        spanAppender = LocationAttributesSpanAppender(
            userDefaults: userDefaults,
            cacheKey: LocationConstants.locationCacheKey,
            cacheInvalidationTime: 3600
        )

        spanAppender.onEnd(span: mockSpan)
    }
}
