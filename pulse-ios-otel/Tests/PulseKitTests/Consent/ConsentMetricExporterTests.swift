/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

final class ConsentMetricExporterTests: XCTestCase {

    func testAllowed_forwardsToDelegate() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.allowed
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        let m = createMetric(name: "m1")
        XCTAssertEqual(exporter.export(metrics: [m]), .success)

        XCTAssertEqual(mock.exportedMetrics.count, 1)
        XCTAssertEqual(mock.exportedMetrics[0].name, "m1")
    }

    func testDenied_drops() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.denied
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        XCTAssertEqual(exporter.export(metrics: [createMetric(name: "x")]), .success)
        XCTAssertTrue(mock.exportedMetrics.isEmpty)
    }

    func testPending_buffersDoesNotForward() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.pending
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        XCTAssertEqual(exporter.export(metrics: [createMetric(name: "a")]), .success)
        XCTAssertTrue(mock.exportedMetrics.isEmpty)
    }

    func testPending_thenFlushBuffer_forwardsBuffered() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.pending
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        _ = exporter.export(metrics: [createMetric(name: "a"), createMetric(name: "b")])
        exporter.flushBuffer()

        XCTAssertEqual(mock.exportedMetrics.map(\.name), ["a", "b"])
    }

    func testClearBuffer_clearsWithoutForwarding() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.pending
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        _ = exporter.export(metrics: [createMetric(name: "x")])
        exporter.clearBuffer()
        exporter.flushBuffer()

        XCTAssertTrue(mock.exportedMetrics.isEmpty)
    }

    func testFlush_whenPending_doesNotCallDelegateFlush() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.pending
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        _ = exporter.flush()
        XCTAssertEqual(mock.flushCount, 0)
    }

    func testFlush_whenAllowed_forwardsToDelegate() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.allowed
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        _ = exporter.flush()
        XCTAssertEqual(mock.flushCount, 1)
    }

    func testShutdown_forwardsToDelegate() {
        let mock = MockMetricExporter()
        var state = PulseDataCollectionConsent.pending
        let exporter = ConsentMetricExporter(delegate: mock, getState: { state })

        _ = exporter.shutdown()
        XCTAssertEqual(mock.shutdownCount, 1)
    }

    // MARK: - Helpers

    private func createMetric(name: String) -> MetricData {
        let point = DoublePointData(
            startEpochNanos: 0,
            endEpochNanos: 1,
            attributes: [:],
            exemplars: [],
            value: 1.0
        )
        return MetricData(
            resource: Resource(),
            instrumentationScopeInfo: InstrumentationScopeInfo(name: "test", version: "1.0.0"),
            name: name,
            description: "d",
            unit: "",
            type: .DoubleSum,
            isMonotonic: true,
            data: MetricData.Data(aggregationTemporality: .cumulative, points: [point])
        )
    }
}

private final class MockMetricExporter: MetricExporter {
    var exportedMetrics: [MetricData] = []
    var flushCount = 0
    var shutdownCount = 0

    func export(metrics: [MetricData]) -> ExportResult {
        exportedMetrics.append(contentsOf: metrics)
        return .success
    }

    func flush() -> ExportResult {
        flushCount += 1
        return .success
    }

    func shutdown() -> ExportResult {
        shutdownCount += 1
        return .success
    }

    func getAggregationTemporality(for instrument: InstrumentType) -> AggregationTemporality {
        .cumulative
    }

    func getDefaultAggregation(for instrument: InstrumentType) -> Aggregation {
        SumAggregation.instance
    }
}
