/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for PulseSdkConfigStorage (load/save, decode failure).
 */

import XCTest
@testable import PulseKit

final class PulseSdkConfigStorageTests: XCTestCase {

    /// Use nil suite so tests use UserDefaults.standard and don't require a real suite.
    private var storage: PulseSdkConfigStorage!

    override func setUp() {
        super.setUp()
        storage = PulseSdkConfigStorage(suiteName: nil)
        clearStoredConfig()
    }

    override func tearDown() {
        clearStoredConfig()
        super.tearDown()
    }

    private func clearStoredConfig() {
        UserDefaults.standard.removeObject(forKey: PulseSdkConfigStorage.configKey)
        UserDefaults.standard.synchronize()
    }

    func testLoadReturnsNilWhenNothingStored() {
        let result = storage.load()
        XCTAssertNil(result)
    }

    func testSaveSyncThenLoadReturnsSameConfig() {
        let config = makeMinimalConfig(version: 2)
        storage.saveSync(config)
        let loaded = storage.load()
        XCTAssertNotNil(loaded)
        XCTAssertEqual(loaded?.version, 2)
    }

    func testLoadReturnsNilWhenStoredDataIsInvalidJSON() {
        UserDefaults.standard.set("not valid json", forKey: PulseSdkConfigStorage.configKey)
        UserDefaults.standard.synchronize()
        let result = storage.load()
        XCTAssertNil(result)
    }

    private func makeMinimalConfig(version: Int) -> PulseSdkConfig {
        PulseSdkConfig(
            version: version,
            description: "test",
            sampling: PulseSamplingConfig(
                default: PulseDefaultSamplingConfig(sessionSampleRate: 0.5),
                rules: [],
                signalsToSample: []
            ),
            signals: PulseSignalConfig(
                scheduleDurationMs: 60_000,
                logsCollectorUrl: "https://logs",
                metricCollectorUrl: "https://metrics",
                spanCollectorUrl: "https://spans",
                customEventCollectorUrl: "https://custom",
                attributesToDrop: [],
                attributesToAdd: [],
                metricsToAdd: []
            ),
            interaction: PulseInteractionConfig(
                collectorUrl: "https://coll",
                configUrl: "https://config",
                beforeInitQueueSize: 100
            ),
            features: []
        )
    }
}
