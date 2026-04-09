/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests for Batch 2: Session parser and device matching.
 */

import XCTest
@testable import PulseKit

final class PulseSessionParserTests: XCTestCase {

    // MARK: - PulseSessionConfigParser

    func testReturnsDefaultWhenNoRulesMatch() {
        let config = makeSamplingConfig(defaultRate: 0.5, rules: [])
        let parser = PulseSessionConfigParser()
        let dc = PulseDeviceContext.current

        let result = parser.parses(
            deviceContext: dc,
            samplingConfig: config,
            currentSdkName: .pulse_ios_swift
        )

        XCTAssertEqual(result, 0.5)
    }

    func testReturnsDefaultWhenSdkNotInRuleSdks() {
        let rule = PulseSessionSamplingRule(
            name: .platform,
            value: "pulse_ios_swift",
            sdks: [.pulse_android_java], // iOS not in list
            sessionSampleRate: 0.9
        )
        let config = makeSamplingConfig(defaultRate: 0.3, rules: [rule])
        let parser = PulseSessionConfigParser()
        let dc = PulseDeviceContext.current

        let result = parser.parses(
            deviceContext: dc,
            samplingConfig: config,
            currentSdkName: .pulse_ios_swift
        )

        XCTAssertEqual(result, 0.3)
    }

    func testReturnsRuleRateWhenPlatformRuleMatches() {
        let rule = PulseSessionSamplingRule(
            name: .platform,
            value: "pulse_ios_swift",
            sdks: [.pulse_ios_swift],
            sessionSampleRate: 0.8
        )
        let config = makeSamplingConfig(defaultRate: 0.2, rules: [rule])
        let parser = PulseSessionConfigParser()
        let dc = PulseDeviceContext.current

        let result = parser.parses(
            deviceContext: dc,
            samplingConfig: config,
            currentSdkName: .pulse_ios_swift
        )

        XCTAssertEqual(result, 0.8)
    }

    func testReturnsFirstMatchingRuleWhenMultipleExist() {
        let rule1 = PulseSessionSamplingRule(
            name: .platform,
            value: "nomatch",
            sdks: [.pulse_ios_swift],
            sessionSampleRate: 0.5
        )
        let rule2 = PulseSessionSamplingRule(
            name: .platform,
            value: "pulse_ios_swift",
            sdks: [.pulse_ios_swift],
            sessionSampleRate: 0.9
        )
        let config = makeSamplingConfig(defaultRate: 0.1, rules: [rule1, rule2])
        let parser = PulseSessionConfigParser()
        let dc = PulseDeviceContext.current

        let result = parser.parses(
            deviceContext: dc,
            samplingConfig: config,
            currentSdkName: .pulse_ios_swift
        )

        XCTAssertEqual(result, 0.9)
    }

    // MARK: - PulseSessionSamplingDecision

    func testShouldSampleThisSessionWithAlwaysOnParser() {
        let config = makeSamplingConfig(defaultRate: 1.0, rules: [])
        let decision = PulseSessionSamplingDecision(
            samplingConfig: config,
            currentSdkName: .pulse_ios_swift,
            parser: AlwaysOnSessionParser(),
            randomGenerator: { 0.5 }
        )
        XCTAssertTrue(decision.shouldSampleThisSession)
    }

    func testShouldNotSampleThisSessionWithAlwaysOffParser() {
        let config = makeSamplingConfig(defaultRate: 0.0, rules: [])
        let decision = PulseSessionSamplingDecision(
            samplingConfig: config,
            currentSdkName: .pulse_ios_swift,
            parser: AlwaysOffSessionParser(),
            randomGenerator: { 0.5 }
        )
        XCTAssertFalse(decision.shouldSampleThisSession)
    }

    func testShouldSampleWhenRandomEqualsRate() {
        let config = makeSamplingConfig(defaultRate: 0.5, rules: [])
        let decision = PulseSessionSamplingDecision(
            samplingConfig: config,
            currentSdkName: .pulse_ios_swift,
            parser: PulseSessionConfigParser(),
            randomGenerator: { 0.5 }
        )
        XCTAssertTrue(decision.shouldSampleThisSession)
    }

    // MARK: - Helpers

    private func makeSamplingConfig(
        defaultRate: Float,
        rules: [PulseSessionSamplingRule]
    ) -> PulseSamplingConfig {
        PulseSamplingConfig(
            default: PulseDefaultSamplingConfig(sessionSampleRate: defaultRate),
            rules: rules,
            signalsToSample: []
        )
    }
}
