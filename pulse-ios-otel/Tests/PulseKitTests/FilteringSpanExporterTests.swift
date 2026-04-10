import XCTest
@testable import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

final class FilteringSpanExporterTests: XCTestCase {
    
    func testFiltersOutDiscardedSpans() {
        let mockExporter = MockSpanExporter()
        let filteringExporter = FilteringSpanExporter(delegate: mockExporter)
        
        let normalSpan = createTestSpan(name: "normal", isInternal: false)
        let discardedSpan = createTestSpan(name: "discarded", isInternal: true)
        let anotherNormalSpan = createTestSpan(name: "another", isInternal: false)
        
        let result = filteringExporter.export(spans: [normalSpan, discardedSpan, anotherNormalSpan], explicitTimeout: nil)
        
        XCTAssertEqual(result, .success)
        XCTAssertEqual(mockExporter.exportedSpans.count, 2)
        XCTAssertEqual(mockExporter.exportedSpans[0].name, "normal")
        XCTAssertEqual(mockExporter.exportedSpans[1].name, "another")
        XCTAssertFalse(mockExporter.exportedSpans.contains { $0.name == "discarded" })
    }
    
    func testFiltersOutAllDiscardedSpans() {
        let mockExporter = MockSpanExporter()
        let filteringExporter = FilteringSpanExporter(delegate: mockExporter)
        
        let discarded1 = createTestSpan(name: "discarded1", isInternal: true)
        let discarded2 = createTestSpan(name: "discarded2", isInternal: true)
        
        let result = filteringExporter.export(spans: [discarded1, discarded2], explicitTimeout: nil)
        
        XCTAssertEqual(result, .success)
        XCTAssertEqual(mockExporter.exportedSpans.count, 0)
    }
    
    func testKeepsAllNormalSpans() {
        let mockExporter = MockSpanExporter()
        let filteringExporter = FilteringSpanExporter(delegate: mockExporter)
        
        let normal1 = createTestSpan(name: "normal1", isInternal: false)
        let normal2 = createTestSpan(name: "normal2", isInternal: false)
        
        let result = filteringExporter.export(spans: [normal1, normal2], explicitTimeout: nil)
        
        XCTAssertEqual(result, .success)
        XCTAssertEqual(mockExporter.exportedSpans.count, 2)
    }
    
    private func createTestSpan(name: String, isInternal: Bool) -> SpanData {
        let start = Date()
        let end = start.addingTimeInterval(0.1)
        var spanData = SpanData(
            traceId: TraceId.random(),
            spanId: SpanId.random(),
            name: name,
            kind: SpanKind.internal,
            startTime: start,
            endTime: end
        )
        
        if isInternal {
            spanData.settingAttributes(["pulse.internal": AttributeValue.bool(true)])
            spanData.settingTotalAttributeCount(1)
        } else {
            spanData.settingAttributes([:])
            spanData.settingTotalAttributeCount(0)
        }
        
        spanData.settingHasEnded(true)
        spanData.settingTotalRecordedEvents(0)
        spanData.settingLinks([])
        spanData.settingTotalRecordedLinks(0)
        spanData.settingStatus(Status.ok)
        
        return spanData
    }
}

private class MockSpanExporter: SpanExporter {
    var exportedSpans: [SpanData] = []
    
    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        exportedSpans.append(contentsOf: spans)
        return .success
    }
    
    func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        return .success
    }
    
    func shutdown(explicitTimeout: TimeInterval?) {}
}
