import XCTest
@testable import PulseKit

final class CachedLocationSaverTest: XCTestCase {
    override func tearDown() {
        CachedLocationSaver.shared.cachedLocation = nil
        super.tearDown()
    }

    func testCachedLocationSaverSingleton() {
        let instance1 = CachedLocationSaver.shared
        let instance2 = CachedLocationSaver.shared
        XCTAssertTrue(instance1 === instance2, "Should be same instance")
    }

    func testCachedLocationSaverStoresAndRetrieves() {
        let location = CachedLocation(
            latitude: 40.7128,
            longitude: -74.0060,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: "US",
            regionIsoCode: "US-NY",
            localityName: "New York",
            postalCode: "10001"
        )

        CachedLocationSaver.shared.cachedLocation = location

        let retrieved = CachedLocationSaver.shared.cachedLocation
        XCTAssertNotNil(retrieved)
        XCTAssertEqual(retrieved?.latitude, 40.7128)
        XCTAssertEqual(retrieved?.longitude, -74.0060)
        XCTAssertEqual(retrieved?.countryIsoCode, "US")
    }

    func testCachedLocationSaverCanBeCleared() {
        let location = CachedLocation(
            latitude: 40.7128,
            longitude: -74.0060,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: "US",
            regionIsoCode: "US-NY",
            localityName: "New York",
            postalCode: "10001"
        )

        CachedLocationSaver.shared.cachedLocation = location
        XCTAssertNotNil(CachedLocationSaver.shared.cachedLocation)

        CachedLocationSaver.shared.cachedLocation = nil
        XCTAssertNil(CachedLocationSaver.shared.cachedLocation)
    }
}
