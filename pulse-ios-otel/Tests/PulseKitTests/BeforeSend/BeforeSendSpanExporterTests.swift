import XCTest
@testable import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

final class BeforeSendSpanExporterTests: XCTestCase {

    func testPassThrough() {
        let mock = MockSpanExporter()
        let exporter = BeforeSendSpanExporter(callback: { $0 }, delegate: mock)

        let span1 = createSpan(name: "span-1")
        let span2 = createSpan(name: "span-2")

        let result = exporter.export(spans: [span1, span2], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedSpans.count, 2)
        XCTAssertEqual(mock.exportedSpans[0].name, "span-1")
        XCTAssertEqual(mock.exportedSpans[1].name, "span-2")
    }

    func testDropSpecificSpan() {
        let mock = MockSpanExporter()
        let exporter = BeforeSendSpanExporter(callback: { span in
            span.name == "drop-me" ? nil : span
        }, delegate: mock)

        let keep = createSpan(name: "keep")
        let drop = createSpan(name: "drop-me")
        let alsoKeep = createSpan(name: "also-keep")

        let result = exporter.export(spans: [keep, drop, alsoKeep], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedSpans.count, 2)
        XCTAssertEqual(mock.exportedSpans[0].name, "keep")
        XCTAssertEqual(mock.exportedSpans[1].name, "also-keep")
    }

    func testDropAllReturnsSuccess() {
        let mock = MockSpanExporter()
        let exporter = BeforeSendSpanExporter(callback: { _ in nil }, delegate: mock)

        let result = exporter.export(spans: [createSpan(name: "a"), createSpan(name: "b")], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertTrue(mock.exportedSpans.isEmpty)
    }

    func testModifyAttributes() {
        let mock = MockSpanExporter()
        let exporter = BeforeSendSpanExporter(callback: { span in
            var attrs = span.attributes
            attrs.removeValue(forKey: "user.id")
            var modified = span
            modified.settingAttributes(attrs)
            return modified
        }, delegate: mock)

        let span = createSpan(name: "span-1", attributes: [
            "user.id": .string("secret-123"),
            "other.key": .string("keep-me"),
        ])

        let result = exporter.export(spans: [span], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedSpans.count, 1)
        XCTAssertNil(mock.exportedSpans[0].attributes["user.id"])
        XCTAssertEqual(mock.exportedSpans[0].attributes["other.key"], .string("keep-me"))
    }

    // MARK: - Helpers

    private func createSpan(name: String, attributes: [String: AttributeValue] = [:]) -> SpanData {
        var spanData = SpanData(
            traceId: TraceId.random(),
            spanId: SpanId.random(),
            name: name,
            kind: .internal,
            startTime: Date(),
            endTime: Date().addingTimeInterval(0.1)
        )
        spanData.settingAttributes(attributes)
        spanData.settingHasEnded(true)
        spanData.settingTotalRecordedEvents(0)
        spanData.settingTotalRecordedLinks(0)
        spanData.settingLinks([])
        spanData.settingStatus(.ok)
        return spanData
    }
}

private class MockSpanExporter: SpanExporter {
    var exportedSpans: [SpanData] = []

    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        exportedSpans.append(contentsOf: spans)
        return .success
    }

    func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode { .success }
    func shutdown(explicitTimeout: TimeInterval?) {}
}
