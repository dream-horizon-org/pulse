/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for PulseDeviceContext (device attribute values for session sampling).
 */

import XCTest
@testable import PulseKit

final class PulseDeviceContextTests: XCTestCase {

    func testCurrentReturnsContext() {
        let context = PulseDeviceContext.current
        XCTAssertNotNil(context.value(for: .platform))
    }

    func testValueForPlatformReturnsIosSwift() {
        let context = PulseDeviceContext.current
        XCTAssertEqual(context.value(for: .platform), PulseSdkName.pulse_ios_swift.rawValue)
    }

    func testValueForUnknownReturnsNil() {
        let context = PulseDeviceContext.current
        XCTAssertNil(context.value(for: .unknown))
    }

    func testValueForStateReturnsNil() {
        let context = PulseDeviceContext.current
        XCTAssertNil(context.value(for: .state))
    }

    func testPlatformMatchesIosSwiftPattern() {
        let context = PulseDeviceContext.current
        XCTAssertTrue(PulseDeviceAttributeName.platform.matches(deviceContext: context, value: "pulse_ios_swift"))
    }

    func testUnknownMatchesReturnsFalse() {
        let context = PulseDeviceContext.current
        XCTAssertFalse(PulseDeviceAttributeName.unknown.matches(deviceContext: context, value: ".*"))
    }
}
