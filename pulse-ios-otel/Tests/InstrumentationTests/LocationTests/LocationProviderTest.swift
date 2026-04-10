import XCTest
import CoreLocation
@testable import PulseKit

final class LocationProviderTest: XCTestCase {
    private var userDefaults: UserDefaults!
    private var suiteName: String!
    private var locationProvider: LocationProvider!

    override func setUp() {
        super.setUp()
        suiteName = "LocationProviderTest-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)!
        CachedLocationSaver.shared.cachedLocation = nil
    }

    override func tearDown() {
        locationProvider?.stopPeriodicRefresh()
        userDefaults.removePersistentDomain(forName: suiteName)
        CachedLocationSaver.shared.cachedLocation = nil
        super.tearDown()
    }

    func testCachedLocationJSONCanBeDeserializedCorrectly() throws {
        let currentTime = Date().timeIntervalSince1970
        let jsonString = """
        {"latitude":37.7749,"longitude":-122.4194,"timestamp":\(currentTime - 1),"countryIsoCode":"US","regionIsoCode":"US-CA","localityName":"San Francisco","postalCode":"94102"}
        """

        let data = jsonString.data(using: .utf8)!
        let cachedLocation = try JSONDecoder().decode(CachedLocation.self, from: data)

        XCTAssertEqual(cachedLocation.latitude, 37.7749, accuracy: 0.0001)
        XCTAssertEqual(cachedLocation.longitude, -122.4194, accuracy: 0.0001)
        XCTAssertEqual(cachedLocation.countryIsoCode, "US")
        XCTAssertEqual(cachedLocation.regionIsoCode, "US-CA")
        XCTAssertEqual(cachedLocation.localityName, "San Francisco")
        XCTAssertEqual(cachedLocation.postalCode, "94102")
    }

    func testCachedLocationJSONWithOnlyLatLonCanBeDeserializedCorrectly() throws {
        let currentTime = Date().timeIntervalSince1970
        let jsonString = """
        {"latitude":37.7749,"longitude":-122.4194,"timestamp":\(currentTime - 1),"countryIsoCode":null,"regionIsoCode":null,"localityName":null,"postalCode":null}
        """

        let data = jsonString.data(using: .utf8)!
        let cachedLocation = try JSONDecoder().decode(CachedLocation.self, from: data)

        XCTAssertEqual(cachedLocation.latitude, 37.7749, accuracy: 0.0001)
        XCTAssertEqual(cachedLocation.longitude, -122.4194, accuracy: 0.0001)
        XCTAssertNil(cachedLocation.countryIsoCode)
        XCTAssertNil(cachedLocation.regionIsoCode)
        XCTAssertNil(cachedLocation.localityName)
        XCTAssertNil(cachedLocation.postalCode)
    }

    func testLocationProviderInitialization() {
        locationProvider = LocationProvider(
            userDefaults: userDefaults,
            cacheKey: "test_key",
            cacheInvalidationTime: 1800
        )
        XCTAssertNotNil(locationProvider)
    }

    func testLocationProviderSaveAndLoad() throws {
        let location = CachedLocation(
            latitude: 34.0522,
            longitude: -118.2437,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: "US",
            regionIsoCode: "US-CA",
            localityName: "Los Angeles",
            postalCode: "90001"
        )
        CachedLocationSaver.shared.cachedLocation = location
        let data = try JSONEncoder().encode(location)
        userDefaults.set(data, forKey: "test_key")

        let loadedData = userDefaults.data(forKey: "test_key")
        XCTAssertNotNil(loadedData)

        let decoded = try JSONDecoder().decode(CachedLocation.self, from: loadedData!)
        XCTAssertEqual(decoded.latitude, 34.0522)
        XCTAssertEqual(decoded.longitude, -118.2437)
        XCTAssertNotNil(CachedLocationSaver.shared.cachedLocation)
        XCTAssertEqual(CachedLocationSaver.shared.cachedLocation?.latitude, 34.0522)
    }

    func testLocationProviderStopPeriodicRefresh() {
        locationProvider = LocationProvider(
            userDefaults: userDefaults,
            cacheKey: "test_key",
            cacheInvalidationTime: 30
        )
        locationProvider.startPeriodicRefresh()
        locationProvider.stopPeriodicRefresh()
    }

    func testLocationProviderMultipleStartStopCalls() {
        locationProvider = LocationProvider(
            userDefaults: userDefaults,
            cacheKey: "test_key",
            cacheInvalidationTime: 30
        )
        locationProvider.startPeriodicRefresh()
        locationProvider.startPeriodicRefresh()
        locationProvider.stopPeriodicRefresh()
        locationProvider.stopPeriodicRefresh()
    }

    func testWhenNoLocationIsDeliveredNoLocationIsSavedToCache() {
        let cacheKey = LocationConstants.locationCacheKey
        userDefaults.removeObject(forKey: cacheKey)
        CachedLocationSaver.shared.cachedLocation = nil

        locationProvider = LocationProvider(
            userDefaults: userDefaults,
            cacheKey: cacheKey,
            cacheInvalidationTime: 3600
        )
        locationProvider.startPeriodicRefresh()

        let expectation = expectation(description: "wait for refresh cycle")
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 0.15) {
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1.0)

        XCTAssertNil(userDefaults.data(forKey: cacheKey))
    }
}
