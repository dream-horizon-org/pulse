import XCTest
@testable import PulseKit
import OpenTelemetryApi
import OpenTelemetrySdk

final class LocationAttributesUtilsTest: XCTestCase {
    private var userDefaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        suiteName = "LocationAttributesUtilsTest-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)!
        CachedLocationSaver.shared.cachedLocation = nil
    }

    override func tearDown() {
        userDefaults.removePersistentDomain(forName: suiteName)
        CachedLocationSaver.shared.cachedLocation = nil
        super.tearDown()
    }

    func testGetLocationAttributesFromCache_EmptyWhenNoCacheExists() {
        let attributes = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: "test_key"
        )

        XCTAssertTrue(attributes.isEmpty, "Should return empty when no cache exists")
    }

    func testGetLocationAttributesFromCache_ReturnsAttributesFromMemoryCache() {
        let location = CachedLocation(
            latitude: 51.5074,
            longitude: -0.1278,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: "GB",
            regionIsoCode: "GB-ENG",
            localityName: "London",
            postalCode: "SW1A"
        )

        CachedLocationSaver.shared.cachedLocation = location

        let attributes = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: "test_key"
        )

        XCTAssertFalse(attributes.isEmpty)

        if case let .double(lat) = attributes["geo.location.lat"] {
            XCTAssertEqual(lat, 51.5074, accuracy: 0.0001)
        } else {
            XCTFail("Expected latitude attribute")
        }

        if case let .double(lon) = attributes["geo.location.lon"] {
            XCTAssertEqual(lon, -0.1278, accuracy: 0.0001)
        } else {
            XCTFail("Expected longitude attribute")
        }

        if case let .string(country) = attributes["geo.country.iso_code"] {
            XCTAssertEqual(country, "GB")
        } else {
            XCTFail("Expected country attribute")
        }

        if case let .string(locality) = attributes["geo.locality.name"] {
            XCTAssertEqual(locality, "London")
        } else {
            XCTFail("Expected locality attribute")
        }
    }

    func testGetLocationAttributesFromCache_FallsBackToUserDefaults() throws {
        CachedLocationSaver.shared.cachedLocation = nil

        let location = CachedLocation(
            latitude: 48.8566,
            longitude: 2.3522,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: "FR",
            regionIsoCode: "FR-IDF",
            localityName: "Paris",
            postalCode: "75001"
        )

        let data = try JSONEncoder().encode(location)
        userDefaults.set(data, forKey: "test_key")

        let attributes = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: "test_key"
        )

        XCTAssertFalse(attributes.isEmpty)

        if case let .string(country) = attributes["geo.country.iso_code"] {
            XCTAssertEqual(country, "FR")
        } else {
            XCTFail("Expected country attribute from UserDefaults")
        }

        XCTAssertNotNil(CachedLocationSaver.shared.cachedLocation)
        XCTAssertEqual(CachedLocationSaver.shared.cachedLocation?.latitude, 48.8566)
    }

    func testGetLocationAttributesFromCache_ReturnsEmptyWhenExpired() {
        let expiredLocation = CachedLocation(
            latitude: 35.6762,
            longitude: 139.6503,
            timestamp: Date().timeIntervalSince1970 - 7200,
            countryIsoCode: "JP",
            regionIsoCode: "JP-13",
            localityName: "Tokyo",
            postalCode: "100-0001"
        )

        CachedLocationSaver.shared.cachedLocation = expiredLocation

        let attributes = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: "test_key",
            cacheInvalidationTime: 30
        )

        XCTAssertTrue(attributes.isEmpty, "Should return empty when cache is expired")
    }

    func testGetLocationAttributesFromCache_HandlesNilOptionalFields() {
        let location = CachedLocation(
            latitude: 52.5200,
            longitude: 13.4050,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: nil,
            regionIsoCode: nil,
            localityName: nil,
            postalCode: nil
        )

        CachedLocationSaver.shared.cachedLocation = location

        let attributes = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: "test_key"
        )

        XCTAssertEqual(attributes.count, 2, "Should only have lat/lon")
        XCTAssertNotNil(attributes["geo.location.lat"])
        XCTAssertNotNil(attributes["geo.location.lon"])
        XCTAssertNil(attributes["geo.country.iso_code"])
        XCTAssertNil(attributes["geo.locality.name"])
    }

    func testGetLocationAttributesFromCache_InvalidJSONReturnsEmpty() {
        CachedLocationSaver.shared.cachedLocation = nil

        let invalidData = "not valid json".data(using: .utf8)!
        userDefaults.set(invalidData, forKey: "test_key")

        let attributes = LocationAttributesUtils.getLocationAttributesFromCache(
            userDefaults: userDefaults,
            cacheKey: "test_key"
        )

        XCTAssertTrue(attributes.isEmpty, "Should return empty for invalid JSON")
    }
}
