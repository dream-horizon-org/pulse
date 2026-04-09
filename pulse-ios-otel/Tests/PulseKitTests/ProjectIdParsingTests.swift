/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for project ID extraction from API keys.
 */

import XCTest
@testable import PulseKit

final class ProjectIdParsingTests: XCTestCase {
    func testWhenProjectIdContainsHyphenShouldReturnPrefixBeforeHyphen() {
        let apiKey = "tenant123-7876796bhbghb_bhbhvub"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "tenant123-7876796bhbghb")
    }

    func testWhenApiKeyHasNoUnderscoreShouldReturnOriginalId() {
        let apiKey = "simpleid"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "simpleid")
    }

    func testWhenApiKeyContainsMultipleUnderscoresShouldReturnPrefixBeforeLastUnderscore() {
        let apiKey = "project_name_random_secret"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "project_name_random")
    }

    func testWhenUnderscoreIsAtStartOfApiKeyShouldReturnOriginalId() {
        let apiKey = "_project123"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "_project123")
    }

    func testWhenApiKeyIsEmptyStringShouldReturnEmptyString() {
        let apiKey = ""
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "")
    }

    func testWhenApiKeyHasSingleCharacterBeforeLastUnderscoreShouldReturnThatCharacter() {
        let apiKey = "a_secret"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "a")
    }

    func testWhenApiKeyIsOnlyUnderscoreShouldReturnUnderscoreString() {
        let apiKey = "_"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "_")
    }

    func testWhenApiKeyContainsProjectIdWithHyphenAndSecretShouldReturnProjectIdCorrectly() {
        let apiKey = "test_project-XwzBrFCb_fYJmt8hy0wmZcXvDq3DGRn7x"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "test_project-XwzBrFCb")
    }

    func testWhenApiKeyContainsUnderscoresInProjectIdPartShouldReturnAllBeforeLastUnderscore() {
        let apiKey = "tenant_123_secret456"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "tenant_123")
    }

    func testWhenApiKeyEndsWithUnderscoreShouldReturnPrefixCorrectly() {
        let apiKey = "project123_"
        let result = Pulse.extractProjectID(from: apiKey)
        XCTAssertEqual(result, "project123")
    }
}
