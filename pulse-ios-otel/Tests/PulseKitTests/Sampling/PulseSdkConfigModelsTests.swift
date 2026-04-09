/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for Pulse SDK config models (decoding, attributesToDrop schema).
 */

import XCTest
@testable import PulseKit

final class PulseSdkConfigModelsTests: XCTestCase {

    func testDecodeMinimalConfig() throws {
        let json = """
        {
            "version": 1,
            "description": "test",
            "sampling": { "default": { "sessionSampleRate": 0.5 }, "rules": [] },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "https://logs",
                "metricCollectorUrl": "https://metrics",
                "spanCollectorUrl": "https://spans",
                "customEventCollectorUrl": "https://custom",
                "attributesToDrop": [],
                "attributesToAdd": []
            },
            "interaction": {
                "collectorUrl": "https://coll",
                "configUrl": "https://config",
                "beforeInitQueueSize": 100
            },
            "features": []
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertEqual(config.version, 1)
        XCTAssertEqual(config.signals.attributesToDrop.count, 0)
        XCTAssertEqual(config.signals.attributesToAdd.count, 0)
    }

    func testDecodeAttributesToDropWithStringValues() throws {
        let json = """
        {
            "version": 1,
            "description": "test",
            "sampling": { "default": { "sessionSampleRate": 0.5 }, "rules": [] },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "https://logs",
                "metricCollectorUrl": "https://metrics",
                "spanCollectorUrl": "https://spans",
                "customEventCollectorUrl": "https://custom",
                "attributesToDrop": [
                    {
                        "values": ["secret", "internal.id"],
                        "condition": {
                            "name": ".*",
                            "props": [],
                            "scopes": ["traces"],
                            "sdks": ["pulse_ios_swift"]
                        }
                    }
                ],
                "attributesToAdd": []
            },
            "interaction": {
                "collectorUrl": "https://coll",
                "configUrl": "https://config",
                "beforeInitQueueSize": 100
            },
            "features": []
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertEqual(config.signals.attributesToDrop.count, 1)
        let entry = config.signals.attributesToDrop[0]
        XCTAssertEqual(entry.values, ["secret", "internal.id"])
        XCTAssertEqual(entry.condition.name, ".*")
        XCTAssertEqual(entry.condition.scopes, [.traces])
        XCTAssertEqual(entry.condition.sdks, [.pulse_ios_swift])
    }

    func testDecodeSignalConfigWithoutMetricsToAddDefaultsToEmpty() throws {
        let json = """
        {
            "version": 1,
            "description": "test",
            "sampling": { "default": { "sessionSampleRate": 0.5 }, "rules": [] },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "https://logs",
                "metricCollectorUrl": "https://metrics",
                "spanCollectorUrl": "https://spans",
                "customEventCollectorUrl": "https://custom",
                "attributesToDrop": [],
                "attributesToAdd": []
            },
            "interaction": {
                "collectorUrl": "https://coll",
                "configUrl": "https://config",
                "beforeInitQueueSize": 100
            },
            "features": []
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertTrue(config.signals.metricsToAdd.isEmpty)
    }

    func testDecodeSignalConfigWithMetricsToAdd() throws {
        let json = """
        {
            "version": 1,
            "description": "test",
            "sampling": { "default": { "sessionSampleRate": 0.5 }, "rules": [] },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "https://logs",
                "metricCollectorUrl": "https://metrics",
                "spanCollectorUrl": "https://spans",
                "customEventCollectorUrl": "https://custom",
                "attributesToDrop": [],
                "attributesToAdd": [],
                "metricsToAdd": [
                    {
                        "name": "pulse.screen.load.count",
                        "target": { "type": "name" },
                        "condition": {
                            "name": "Created",
                            "props": [],
                            "scopes": ["traces"],
                            "sdks": ["pulse_ios_swift", "pulse_ios_rn"]
                        },
                        "type": {
                            "type": "counter"
                        }
                    }
                ]
            },
            "interaction": {
                "collectorUrl": "https://coll",
                "configUrl": "https://config",
                "beforeInitQueueSize": 100
            },
            "features": []
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertEqual(config.signals.metricsToAdd.count, 1)
        let entry = config.signals.metricsToAdd[0]
        XCTAssertEqual(entry.name, "pulse.screen.load.count")
        if case .name = entry.target { } else { XCTFail("Expected target .name") }
        XCTAssertEqual(entry.condition.name, "Created")
        if case .counter = entry.type {
            // OK - Counter has no params
        } else { XCTFail("Expected .counter") }
    }

    func testDecodeSignalsToSample() throws {
        let json = """
        {
            "version": 1,
            "description": "test",
            "sampling": {
                "default": { "sessionSampleRate": 0.5 },
                "rules": [],
                "signalsToSample": [
                    {
                        "condition": {
                            "name": "checkout_flow",
                            "props": [],
                            "scopes": ["traces"],
                            "sdks": ["pulse_ios_swift"]
                        },
                        "sampleRate": 0.2
                    }
                ]
            },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "https://logs",
                "metricCollectorUrl": "https://metrics",
                "spanCollectorUrl": "https://spans",
                "customEventCollectorUrl": "https://custom",
                "attributesToDrop": [],
                "attributesToAdd": []
            },
            "interaction": {
                "collectorUrl": "https://coll",
                "configUrl": "https://config",
                "beforeInitQueueSize": 100
            },
            "features": []
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertEqual(config.sampling.signalsToSample.count, 1)
        let entry = config.sampling.signalsToSample[0]
        XCTAssertEqual(entry.condition.name, "checkout_flow")
        XCTAssertEqual(entry.sampleRate, 0.2)
    }

    /// Decodes Config 3 from MOCK_CONFIG_PAYLOADS – signalsToSample with always keep, probabilistic, always drop.
    func testDecodeConfig3SignalsToSamplePayload() throws {
        let json = """
        {
            "version": 1,
            "description": "Config with signalsToSample - always keep checkout, probabilistic http GET, always drop noise",
            "sampling": {
                "default": { "sessionSampleRate": 0.3 },
                "rules": [],
                "signalsToSample": [
                    {
                        "condition": {
                            "name": "checkout_complete",
                            "props": [],
                            "scopes": ["traces"],
                            "sdks": ["pulse_ios_swift", "pulse_ios_rn"]
                        },
                        "sampleRate": 1.0
                    },
                    {
                        "condition": {
                            "name": "HTTP GET",
                            "props": [{ "name": "http.method", "value": "GET" }],
                            "scopes": ["traces"],
                            "sdks": ["pulse_ios_swift"]
                        },
                        "sampleRate": 0.2
                    },
                    {
                        "condition": {
                            "name": "noise_span",
                            "props": [],
                            "scopes": ["traces"],
                            "sdks": ["pulse_ios_swift"]
                        },
                        "sampleRate": 0.0
                    }
                ]
            },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "http://127.0.0.1:4318/v1/logs",
                "metricCollectorUrl": "http://127.0.0.1:4318/v1/metrics",
                "spanCollectorUrl": "http://127.0.0.1:4318/v1/traces",
                "customEventCollectorUrl": "http://127.0.0.1:4318/v1/logs",
                "attributesToDrop": [],
                "attributesToAdd": [],
                "metricsToAdd": []
            },
            "interaction": {
                "collectorUrl": "http://127.0.0.1:8080",
                "configUrl": "http://127.0.0.1:8080/v1/interaction-configs/",
                "beforeInitQueueSize": 100
            },
            "features": [
                { "featureName": "ios_crash", "sessionSampleRate": 1, "sdks": ["pulse_ios_swift"] }
            ]
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertEqual(config.sampling.signalsToSample.count, 3)
        XCTAssertEqual(config.sampling.signalsToSample[0].condition.name, "checkout_complete")
        XCTAssertEqual(config.sampling.signalsToSample[0].sampleRate, 1.0)
        XCTAssertEqual(config.sampling.signalsToSample[1].condition.name, "HTTP GET")
        XCTAssertEqual(config.sampling.signalsToSample[1].condition.props.first?.name, "http.method")
        XCTAssertEqual(config.sampling.signalsToSample[1].sampleRate, 0.2)
        XCTAssertEqual(config.sampling.signalsToSample[2].condition.name, "noise_span")
        XCTAssertEqual(config.sampling.signalsToSample[2].sampleRate, 0)
        XCTAssertEqual(config.signals.spanCollectorUrl, "http://127.0.0.1:4318/v1/traces")
    }

    /// Decodes Config 3b from MOCK_CONFIG_PAYLOADS – sessionSampleRate 0 with signalsToSample override.
    func testDecodeConfig3bSignalsToSampleOverridePayload() throws {
        let json = """
        {
            "version": 1,
            "description": "Global 0% session rate but signalsToSample overrides - checkout_complete always emitted",
            "sampling": {
                "default": { "sessionSampleRate": 0.0 },
                "rules": [],
                "signalsToSample": [
                    {
                        "condition": {
                            "name": "checkout_complete",
                            "props": [],
                            "scopes": ["traces"],
                            "sdks": ["pulse_ios_swift", "pulse_ios_rn"]
                        },
                        "sampleRate": 1.0
                    },
                    {
                        "condition": {
                            "name": ".*",
                            "props": [],
                            "scopes": ["traces", "logs"],
                            "sdks": ["pulse_ios_swift", "pulse_ios_rn"]
                        },
                        "sampleRate": 0.0
                    }
                ]
            },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "http://127.0.0.1:4318/v1/logs",
                "metricCollectorUrl": "http://127.0.0.1:4318/v1/metrics",
                "spanCollectorUrl": "http://127.0.0.1:4318/v1/traces",
                "customEventCollectorUrl": "http://127.0.0.1:4318/v1/logs",
                "attributesToDrop": [],
                "attributesToAdd": [],
                "metricsToAdd": []
            },
            "interaction": {
                "collectorUrl": "http://127.0.0.1:8080",
                "configUrl": "http://127.0.0.1:8080/v1/interaction-configs/",
                "beforeInitQueueSize": 100
            },
            "features": [
                { "featureName": "ios_crash", "sessionSampleRate": 1, "sdks": ["pulse_ios_swift"] }
            ]
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertEqual(config.sampling.default.sessionSampleRate, 0)
        XCTAssertEqual(config.sampling.signalsToSample.count, 2)
        XCTAssertEqual(config.sampling.signalsToSample[0].condition.name, "checkout_complete")
        XCTAssertEqual(config.sampling.signalsToSample[0].sampleRate, 1.0)
        XCTAssertEqual(config.sampling.signalsToSample[1].condition.name, ".*")
        XCTAssertEqual(config.sampling.signalsToSample[1].sampleRate, 0)
    }

    func testDecodeWithoutSignalsToSampleDefaultsToEmpty() throws {
        let json = """
        {
            "version": 1,
            "description": "test",
            "sampling": { "default": { "sessionSampleRate": 0.5 }, "rules": [] },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "https://logs",
                "metricCollectorUrl": "https://metrics",
                "spanCollectorUrl": "https://spans",
                "customEventCollectorUrl": "https://custom",
                "attributesToDrop": [],
                "attributesToAdd": []
            },
            "interaction": {
                "collectorUrl": "https://coll",
                "configUrl": "https://config",
                "beforeInitQueueSize": 100
            },
            "features": []
        }
        """
        let data = json.data(using: .utf8)!
        let config = try JSONDecoder().decode(PulseSdkConfig.self, from: data)
        XCTAssertTrue(config.sampling.signalsToSample.isEmpty)
    }
}
