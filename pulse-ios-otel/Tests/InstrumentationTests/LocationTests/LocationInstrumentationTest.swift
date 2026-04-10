import XCTest
@testable import PulseKit

final class LocationInstrumentationTest: XCTestCase {
    private var instrumentation: LocationInstrumentation!
    private var userDefaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        instrumentation = LocationInstrumentation()
        suiteName = "LocationInstrumentationTest-\(UUID().uuidString)"
        userDefaults = UserDefaults(suiteName: suiteName)!
    }

    override func tearDown() {
        LocationInstrumentation.uninstall()
        userDefaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testLocationInstrumentationInstallUninstall() {
        LocationInstrumentation.uninstall()

        LocationInstrumentation.install(
            userDefaults: userDefaults,
            cacheInvalidationTime: 30
        )

        XCTAssertNotNil(LocationInstrumentation.provider)

        LocationInstrumentation.uninstall()

        XCTAssertNil(LocationInstrumentation.provider)
    }

    func testLocationInstrumentationMultipleInstallCalls() {
        LocationInstrumentation.uninstall()

        LocationInstrumentation.install(userDefaults: userDefaults)
        let provider1 = LocationInstrumentation.provider

        LocationInstrumentation.install(userDefaults: userDefaults)
        let provider2 = LocationInstrumentation.provider

        XCTAssertTrue(provider1 === provider2)

        LocationInstrumentation.uninstall()
    }

    func testLocationInstrumentationUninstallWithoutInstall() {
        LocationInstrumentation.uninstall()
        LocationInstrumentation.uninstall()
    }
}
