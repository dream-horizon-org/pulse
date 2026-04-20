import XCTest
@testable import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

final class BeforeSendMetricExporterTests: XCTestCase {

    func testPassThrough() {
        let mock = MockMetricExporter()
        let exporter = BeforeSendMetricExporter(callback: { $0 }, delegate: mock)

        let m1 = createMetric(name: "metric-1")
        let m2 = createMetric(name: "metric-2")

        let result = exporter.export(metrics: [m1, m2])

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedMetrics.count, 2)
        XCTAssertEqual(mock.exportedMetrics[0].name, "metric-1")
        XCTAssertEqual(mock.exportedMetrics[1].name, "metric-2")
    }

    func testDropSpecificMetric() {
        let mock = MockMetricExporter()
        let exporter = BeforeSendMetricExporter(callback: { metric in
            metric.name == "drop-me" ? nil : metric
        }, delegate: mock)

        let keep = createMetric(name: "keep")
        let drop = createMetric(name: "drop-me")
        let alsoKeep = createMetric(name: "also-keep")

        let result = exporter.export(metrics: [keep, drop, alsoKeep])

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedMetrics.count, 2)
        XCTAssertEqual(mock.exportedMetrics[0].name, "keep")
        XCTAssertEqual(mock.exportedMetrics[1].name, "also-keep")
    }

    func testDropAllReturnsSuccess() {
        let mock = MockMetricExporter()
        let exporter = BeforeSendMetricExporter(callback: { _ in nil }, delegate: mock)

        let result = exporter.export(metrics: [createMetric(name: "a"), createMetric(name: "b")])

        XCTAssertEqual(result, .success)
        XCTAssertTrue(mock.exportedMetrics.isEmpty)
    }

    func testModifyMetric() {
        let mock = MockMetricExporter()
        let exporter = BeforeSendMetricExporter(callback: { metric in
            metric.name == "drop-name" ? nil : metric
        }, delegate: mock)

        let keep = createMetric(name: "keep", description: "desc-a")
        let drop = createMetric(name: "drop-name", description: "desc-b")

        let result = exporter.export(metrics: [keep, drop])

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedMetrics.count, 1)
        XCTAssertEqual(mock.exportedMetrics[0].name, "keep")
        XCTAssertEqual(mock.exportedMetrics[0].description, "desc-a")
    }

    func testForwardFlushAndShutdown() {
        let mock = MockMetricExporter()
        let exporter = BeforeSendMetricExporter(callback: { $0 }, delegate: mock)

        _ = exporter.flush()
        XCTAssertEqual(mock.flushCount, 1)

        _ = exporter.shutdown()
        XCTAssertEqual(mock.shutdownCount, 1)
    }

    func testForwardAggregationTemporalityAndDefaultAggregation() {
        let mock = MockMetricExporter()
        let exporter = BeforeSendMetricExporter(callback: { $0 }, delegate: mock)

        let temporality = exporter.getAggregationTemporality(for: .counter)
        XCTAssertEqual(temporality, .cumulative)

        let aggregation = exporter.getDefaultAggregation(for: .counter)
        XCTAssertTrue(aggregation is SumAggregation)
    }

    // MARK: - Helpers

    private func createMetric(name: String, description: String = "description") -> MetricData {
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
            description: description,
            unit: "",
            type: .DoubleSum,
            isMonotonic: true,
            data: MetricData.Data(aggregationTemporality: .cumulative, points: [point])
        )
    }
}

private class MockMetricExporter: MetricExporter {
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
        return .cumulative
    }

    func getDefaultAggregation(for instrument: InstrumentType) -> Aggregation {
        return SumAggregation.instance
    }
}
