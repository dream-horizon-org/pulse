import XCTest
@testable import PulseKit

final class CachedLocationTest: XCTestCase {
    func testCachedLocationExpiration() {
        let now = Date().timeIntervalSince1970
        let cachedRecent = CachedLocation(
            latitude: 37.7749,
            longitude: -122.4194,
            timestamp: now,
            countryIsoCode: "US",
            regionIsoCode: "US-CA",
            localityName: "San Francisco",
            postalCode: "94102"
        )

        XCTAssertFalse(cachedRecent.isExpired(30), "Recent cache should not be expired")

        let cachedOld = CachedLocation(
            latitude: 37.7749,
            longitude: -122.4194,
            timestamp: now - 7200,
            countryIsoCode: "US",
            regionIsoCode: "US-CA",
            localityName: "San Francisco",
            postalCode: "94102"
        )

        XCTAssertTrue(cachedOld.isExpired(30), "Old cache should be expired with 1 hour TTL")
    }

    func testCachedLocationCodable() throws {
        let location = CachedLocation(
            latitude: 37.7749,
            longitude: -122.4194,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: "US",
            regionIsoCode: "US-CA",
            localityName: "San Francisco",
            postalCode: "94102"
        )

        let encoded = try JSONEncoder().encode(location)
        let decoded = try JSONDecoder().decode(CachedLocation.self, from: encoded)

        XCTAssertEqual(decoded.latitude, location.latitude)
        XCTAssertEqual(decoded.longitude, location.longitude)
        XCTAssertEqual(decoded.countryIsoCode, location.countryIsoCode)
        XCTAssertEqual(decoded.regionIsoCode, location.regionIsoCode)
        XCTAssertEqual(decoded.localityName, location.localityName)
        XCTAssertEqual(decoded.postalCode, location.postalCode)
    }
}
