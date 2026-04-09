/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for PulseMetricsToAddModels (decode/encode for metricsToAdd, target, type).
 */

import XCTest
@testable import PulseKit

final class PulseMetricsToAddModelsTests: XCTestCase {

    // MARK: - PulseMetricsData

    func testDecodeCounterMetricsData() throws {
        let json = """
        {"type":"counter"}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsData.self, from: data)
        if case .counter = decoded {
            // OK
        } else {
            XCTFail("Expected counter, got \(decoded)")
        }
    }

    func testDecodeGaugeMetricsData() throws {
        let json = """
        {"type":"gauge","isFraction":true}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsData.self, from: data)
        if case .gauge(let isFraction) = decoded {
            XCTAssertTrue(isFraction)
        } else {
            XCTFail("Expected gauge, got \(decoded)")
        }
    }

    func testDecodeHistogramMetricsDataWithBuckets() throws {
        let json = """
        {"type":"histogram","bucket":[50,100,250,500,1000],"isFraction":true}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsData.self, from: data)
        if case .histogram(let bucket, let isFraction) = decoded {
            XCTAssertEqual(bucket, [50, 100, 250, 500, 1000])
            XCTAssertTrue(isFraction)
        } else {
            XCTFail("Expected histogram, got \(decoded)")
        }
    }

    func testDecodeHistogramMetricsDataNoBuckets() throws {
        let json = """
        {"type":"histogram","isFraction":false}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsData.self, from: data)
        if case .histogram(let bucket, let isFraction) = decoded {
            XCTAssertNil(bucket)
            XCTAssertFalse(isFraction)
        } else {
            XCTFail("Expected histogram, got \(decoded)")
        }
    }

    func testDecodeSumMetricsData() throws {
        let json = """
        {"type":"sum","isFraction":false}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsData.self, from: data)
        if case .sum(let isFraction, let isMonotonic) = decoded {
            XCTAssertFalse(isFraction)
            XCTAssertFalse(isMonotonic, "Backend MetricsType.Sum defaults isMonotonic to false when omitted")
        } else {
            XCTFail("Expected sum, got \(decoded)")
        }
    }

    // MARK: - PulseMetricsToAddTarget

    func testDecodeMetricsToAddTargetTypeName() throws {
        let json = """
        {"type":"name"}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsToAddTarget.self, from: data)
        if case .name = decoded {
            // OK
        } else {
            XCTFail("Expected .name, got \(decoded)")
        }
    }

    func testDecodeMetricsToAddTargetAttribute() throws {
        let json = """
        {"type":"attribute","condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift"]},"shouldAddPropNameAsSuffix":true}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsToAddTarget.self, from: data)
        if case .attribute(let condition, let addPropNameAsSuffix) = decoded {
            XCTAssertEqual(condition.name, ".*")
            XCTAssertTrue(addPropNameAsSuffix)
        } else {
            XCTFail("Expected .attribute, got \(decoded)")
        }
    }

    func testDecodeMetricsToAddTargetShouldAddPropNameAsSuffix() throws {
        let json = """
        {"type":"attribute","condition":{"name":"key","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift"]},"shouldAddPropNameAsSuffix":true}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsToAddTarget.self, from: data)
        if case .attribute(_, let addPropNameAsSuffix) = decoded {
            XCTAssertTrue(addPropNameAsSuffix)
        } else {
            XCTFail("Expected .attribute, got \(decoded)")
        }
    }

    func testDecodeSumWithIsMonotonic() throws {
        let json = """
        {"type":"sum","isFraction":false,"isMonotonic":true}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsData.self, from: data)
        if case .sum(let isFraction, let isMonotonic) = decoded {
            XCTAssertFalse(isFraction)
            XCTAssertTrue(isMonotonic)
        } else {
            XCTFail("Expected sum, got \(decoded)")
        }

        let json2 = """
        {"type":"sum","isFraction":true,"isMonotonic":false}
        """
        let data2 = json2.data(using: .utf8)!
        let decoded2 = try JSONDecoder().decode(PulseMetricsData.self, from: data2)
        if case .sum(let isFraction2, let isMonotonic2) = decoded2 {
            XCTAssertTrue(isFraction2)
            XCTAssertFalse(isMonotonic2)
        } else {
            XCTFail("Expected sum, got \(decoded2)")
        }
    }

    func testDecodeMetricsToAddTargetAttributeShouldAddPropNameAsSuffixDefaultsFalse() throws {
        let json = """
        {"type":"attribute","condition":{"name":"attr_key","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift"]}}
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsToAddTarget.self, from: data)
        if case .attribute(_, let addPropNameAsSuffix) = decoded {
            XCTAssertFalse(addPropNameAsSuffix)
        } else {
            XCTFail("Expected .attribute, got \(decoded)")
        }
    }

    // MARK: - PulseMetricsToAddEntry

    func testDecodeFullMetricsToAddEntry() throws {
        let json = """
        {
            "name":"my_metric",
            "target":{"type":"name"},
            "condition":{"name":"Created","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift"]},
            "type":{"type":"counter"}
        }
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsToAddEntry.self, from: data)
        XCTAssertEqual(decoded.name, "my_metric")
        if case .name = decoded.target { } else { XCTFail("Expected target .name") }
        XCTAssertEqual(decoded.condition.name, "Created")
        if case .counter = decoded.type { } else { XCTFail("Expected type .counter") }
        XCTAssertTrue(decoded.attributesToPick.isEmpty)
    }

    func testDecodeMetricsToAddEntryBackendShape() throws {
        let json = """
        {
            "name": "span_count",
            "target": { "type": "name" },
            "condition": {
                "name": ".*",
                "props": [],
                "scopes": ["traces"],
                "sdks": ["pulse_ios_swift", "pulse_ios_rn"]
            },
            "type": { "type": "counter" },
            "attributesToPick": null
        }
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsToAddEntry.self, from: data)
        XCTAssertEqual(decoded.name, "span_count")
        if case .name = decoded.target { } else { XCTFail("Expected target .name") }
        XCTAssertEqual(decoded.condition.name, ".*")
        XCTAssertEqual(decoded.condition.sdks, [.pulse_ios_swift, .pulse_ios_rn])
        if case .counter = decoded.type { } else { XCTFail("Expected type .counter") }
        XCTAssertTrue(decoded.attributesToPick.isEmpty)
    }

    func testDecodeMetricsToAddEntryWithAttributesToPick() throws {
        let json = """
        {
            "name":"my_metric",
            "target":{"type":"name"},
            "condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift"]},
            "type":{"type":"counter"},
            "attributesToPick":[{"name":"http.method","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift"]}]
        }
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseMetricsToAddEntry.self, from: data)
        XCTAssertEqual(decoded.attributesToPick.count, 1)
        XCTAssertEqual(decoded.attributesToPick[0].name, "http.method")
    }

    // MARK: - PulseSignalConfig

    func testDecodeSignalConfigWithMetricsToAdd() throws {
        let json = """
        {
            "scheduleDurationMs":60000,
            "logsCollectorUrl":"https://logs",
            "metricCollectorUrl":"https://metrics",
            "spanCollectorUrl":"https://spans",
            "customEventCollectorUrl":"https://custom",
            "attributesToDrop":[],
            "attributesToAdd":[],
            "metricsToAdd":[{"name":"test_counter","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift"]},"type":{"type":"counter"}}]
        }
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseSignalConfig.self, from: data)
        XCTAssertEqual(decoded.metricsToAdd.count, 1)
        XCTAssertEqual(decoded.metricsToAdd[0].name, "test_counter")
    }

    func testDecodeSignalConfigWithoutMetricsToAddDefaultsToEmpty() throws {
        let json = """
        {
            "scheduleDurationMs":60000,
            "logsCollectorUrl":"https://logs",
            "metricCollectorUrl":"https://metrics",
            "spanCollectorUrl":"https://spans",
            "customEventCollectorUrl":"https://custom",
            "attributesToDrop":[],
            "attributesToAdd":[]
        }
        """
        let data = json.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PulseSignalConfig.self, from: data)
        XCTAssertTrue(decoded.metricsToAdd.isEmpty)
    }

    // MARK: - Encode round-trip

    func testEncodeDecodeRoundTrip() throws {
        let entry = PulseMetricsToAddEntry(
            name: "roundtrip_metric",
            target: .attribute(
                condition: PulseSignalMatchCondition(
                    name: ".*",
                    props: [PulseProp(name: "attr", value: ".*")],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                ),
                addPropNameAsSuffix: true
            ),
            condition: PulseSignalMatchCondition(
                name: "HTTP.*",
                props: [],
                scopes: [.traces],
                sdks: [.pulse_ios_swift]
            ),
            type: .histogram(bucket: [50, 100, 250], isFraction: true),
            attributesToPick: [
                PulseSignalMatchCondition(
                    name: "http.method",
                    props: [],
                    scopes: [.traces],
                    sdks: [.pulse_ios_swift]
                )
            ]
        )
        let encoder = JSONEncoder()
        let data = try encoder.encode(entry)
        let decoded = try JSONDecoder().decode(PulseMetricsToAddEntry.self, from: data)
        XCTAssertEqual(decoded.name, entry.name)
        XCTAssertEqual(decoded.attributesToPick.count, entry.attributesToPick.count)
    }
}
