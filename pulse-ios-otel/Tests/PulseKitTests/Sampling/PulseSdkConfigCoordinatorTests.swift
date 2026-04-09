/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for PulseSdkConfigCoordinator (loadCurrentConfig delegates to storage).
 */

import XCTest
@testable import PulseKit

final class PulseSdkConfigCoordinatorTests: XCTestCase {

    private var storage: PulseSdkConfigStorage!
    private var coordinator: PulseSdkConfigCoordinator!

    override func setUp() {
        super.setUp()
        storage = PulseSdkConfigStorage(suiteName: nil)
        coordinator = PulseSdkConfigCoordinator(storage: storage)
        UserDefaults.standard.removeObject(forKey: PulseSdkConfigStorage.configKey)
        UserDefaults.standard.synchronize()
    }

    override func tearDown() {
        UserDefaults.standard.removeObject(forKey: PulseSdkConfigStorage.configKey)
        UserDefaults.standard.synchronize()
        super.tearDown()
    }

    func testLoadCurrentConfigReturnsNilWhenStorageEmpty() {
        let result = coordinator.loadCurrentConfig()
        XCTAssertNil(result)
    }

    func testLoadCurrentConfigReturnsConfigWhenStorageHasConfig() {
        let config = makeMinimalConfig(version: 3)
        storage.saveSync(config)
        let result = coordinator.loadCurrentConfig()
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.version, 3)
    }

    func testCoordinatorUsesLocalMockWhenFlagTrue() {
        let mockCoordinator = PulseSdkConfigCoordinator(storage: storage, useLocalMockConfig: true)
        let result = mockCoordinator.loadCurrentConfig()
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.version, 999)
        XCTAssertEqual(result?.description, "Local mock config for dev/testing")
        XCTAssertFalse(result?.signals.metricsToAdd.isEmpty ?? true)
    }

    func testCoordinatorUsesStorageWhenFlagFalse() {
        let config = makeMinimalConfig(version: 42)
        storage.saveSync(config)
        let result = coordinator.loadCurrentConfig()
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.version, 42)
    }

    func testCoordinatorSkipsBackgroundFetchWhenMockEnabled() {
        let mockCoordinator = PulseSdkConfigCoordinator(storage: storage, useLocalMockConfig: true)
        // Should not throw or crash; fetch is no-op
        mockCoordinator.startBackgroundFetch(
            configEndpointUrl: "https://example.com/config",
            endpointHeaders: [:],
            currentConfigVersion: nil as Int?
        )
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
