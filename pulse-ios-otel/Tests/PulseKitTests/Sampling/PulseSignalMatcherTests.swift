/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests for Batch 3: PulseSignalMatcher / PulseSignalsAttrMatcher.
 */

import XCTest
@testable import PulseKit

final class PulseSignalMatcherTests: XCTestCase {

    private let matcher: PulseSignalMatcher = PulseSignalsAttrMatcher()
    private let currentSdkName = PulseSdkName.pulse_ios_swift

    func testMatchesReturnsTrueWhenAllConditionsMet() {
        let condition = makeCondition(
            name: "test_signal",
            sdks: [.pulse_ios_swift],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value1"), PulseProp(name: "key2", value: "value2")]
        )
        let props: [String: Any?] = ["key1": "value1", "key2": "value2"]

        XCTAssertTrue(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: props,
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsFalseWhenSdkNotPresent() {
        let condition = makeCondition(
            name: "test_signal",
            sdks: [],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value1")]
        )

        XCTAssertFalse(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: ["key1": "value1"],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsFalseWhenScopeNotPresent() {
        let condition = makeCondition(
            name: "test_signal",
            sdks: [.pulse_ios_swift],
            scopes: [],
            props: [PulseProp(name: "key1", value: "value1")]
        )

        XCTAssertFalse(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: ["key1": "value1"],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsFalseWhenNameDoesNotMatchRegex() {
        let condition = makeCondition(
            name: "other_signal",
            sdks: [.pulse_ios_swift],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value1")]
        )

        XCTAssertFalse(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: ["key1": "value1"],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsFalseWhenPropsSizeMismatch() {
        let condition = makeCondition(
            name: "test_signal",
            sdks: [.pulse_ios_swift],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value1"), PulseProp(name: "key2", value: "value2")]
        )

        XCTAssertFalse(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: ["key1": "value1"],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsFalseWhenPropValueMismatch() {
        let condition = makeCondition(
            name: "test_signal",
            sdks: [.pulse_ios_swift],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value1"), PulseProp(name: "key2", value: "value2")]
        )

        XCTAssertFalse(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: ["key1": "value1", "key2": "value3"],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsTrueWithRegexForName() {
        let condition = makeCondition(
            name: "test_signal_.*",
            sdks: [.pulse_ios_swift],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value1")]
        )

        XCTAssertTrue(matcher.matches(
            scope: .traces,
            name: "test_signal_123",
            props: ["key1": "value1"],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsTrueWithRegexForProps() {
        let condition = makeCondition(
            name: "test_signal",
            sdks: [.pulse_ios_swift],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value_.*")]
        )

        XCTAssertTrue(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: ["key1": "value_123"],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testMatchesReturnsTrueWhenPropsValueAreNull() {
        let condition = makeCondition(
            name: "test_signal",
            sdks: [.pulse_ios_swift],
            scopes: [.traces],
            props: [PulseProp(name: "key1", value: "value_1"), PulseProp(name: "key2", value: nil)]
        )

        XCTAssertTrue(matcher.matches(
            scope: .traces,
            name: "test_signal",
            props: ["key1": "value_1", "key2": nil],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    func testAllMatchLogConditionMatchesAnyLog() {
        let condition = PulseSignalMatchCondition.allMatchLogCondition
        XCTAssertTrue(matcher.matches(
            scope: .logs,
            name: "any_event",
            props: [:],
            condition: condition,
            sdkName: currentSdkName
        ))
    }

    // MARK: - Helpers

    private func makeCondition(
        name: String,
        sdks: [PulseSdkName],
        scopes: [PulseSignalScope],
        props: [PulseProp]
    ) -> PulseSignalMatchCondition {
        PulseSignalMatchCondition(name: name, props: props, scopes: scopes, sdks: sdks)
    }
}
