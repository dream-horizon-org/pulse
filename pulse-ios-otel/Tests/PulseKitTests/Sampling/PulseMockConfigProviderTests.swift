/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import PulseKit

final class PulseMockConfigProviderTests: XCTestCase {

    func testMockConfigReturnsValidPulseSdkConfig() {
        let config = PulseMockConfigProvider.fullMockConfig()
        XCTAssertEqual(config.version, 999)
        XCTAssertEqual(config.description, "Local mock config for dev/testing")
        XCTAssertEqual(config.signals.scheduleDurationMs, 60_000)
    }

    func testMockConfigHasMetricsToAddEntries() {
        let config = PulseMockConfigProvider.fullMockConfig()
        XCTAssertFalse(config.signals.metricsToAdd.isEmpty)
        let names = config.signals.metricsToAdd.map { $0.name }
        XCTAssertTrue(names.contains("span_count"))
        XCTAssertTrue(names.contains("http_duration"))
        XCTAssertTrue(names.contains("log_count"))
    }

    func testMockConfigHasAllFeaturesEnabled() {
        let config = PulseMockConfigProvider.fullMockConfig()
        XCTAssertFalse(config.features.isEmpty, "Mock config should have all features enabled")
        let allNonUnknown = PulseFeatureName.allCases.filter { $0 != .unknown }
        XCTAssertEqual(config.features.count, allNonUnknown.count)
        for feature in config.features {
            XCTAssertEqual(feature.sessionSampleRate, 1, "\(feature.featureName) should be enabled")
            XCTAssertTrue(feature.sdks.contains(.pulse_ios_swift))
            XCTAssertTrue(feature.sdks.contains(.pulse_ios_rn))
        }
    }
}
