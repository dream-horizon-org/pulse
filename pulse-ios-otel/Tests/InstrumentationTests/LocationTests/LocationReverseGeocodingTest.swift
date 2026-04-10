import XCTest
import CoreLocation
@testable import PulseKit

final class LocationReverseGeocodingTest: XCTestCase {
    private var reverseGeocoding: LocationReverseGeocoding!

    override func setUp() {
        super.setUp()
        reverseGeocoding = LocationReverseGeocoding()
    }

    func testGeoResultHoldsExpectedFields() {
        let result = GeoResult(
            countryIsoCode: "US",
            administrativeArea: "California",
            locality: "San Francisco",
            postalCode: "94102"
        )
        XCTAssertEqual(result.countryIsoCode, "US")
        XCTAssertEqual(result.administrativeArea, "California")
        XCTAssertEqual(result.locality, "San Francisco")
        XCTAssertEqual(result.postalCode, "94102")
    }

    func testGeoResultAllowsNilOptionals() {
        let result = GeoResult(
            countryIsoCode: nil,
            administrativeArea: nil,
            locality: nil,
            postalCode: nil
        )
        XCTAssertNil(result.countryIsoCode)
        XCTAssertNil(result.administrativeArea)
        XCTAssertNil(result.locality)
        XCTAssertNil(result.postalCode)
    }

    func testCancelDoesNotCrash() {
        reverseGeocoding.cancel()
    }

    func testResolveCallsCompletion() {
        let location = CLLocation(latitude: 37.7749, longitude: -122.4194)
        let expectation = expectation(description: "resolve completion")
        reverseGeocoding.resolve(location: location) { result in
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 5.0)
    }
}
