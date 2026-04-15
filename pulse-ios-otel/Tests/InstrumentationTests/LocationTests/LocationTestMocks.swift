import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk
@testable import PulseKit

class MockReadableSpan: ReadableSpan {
    var capturedAttributes: [String: AttributeValue] = [:]

    var instrumentationScopeInfo: InstrumentationScopeInfo = InstrumentationScopeInfo(name: "test")
    var hasEnded: Bool = false
    var latency: TimeInterval { 0 }

    func getAttributes() -> [String: AttributeValue] { capturedAttributes }
    func toSpanData() -> SpanData { fatalError("Not implemented for test") }

    func setAttribute(key: String, value: AttributeValue?) {
        if let value = value { capturedAttributes[key] = value }
    }
    func setAttributes(_ attributes: [String: AttributeValue]) {
        for (k, v) in attributes { capturedAttributes[k] = v }
    }

    func addEvent(name: String) {}
    func addEvent(name: String, timestamp: Date) {}
    func addEvent(name: String, attributes: [String: AttributeValue]) {}
    func addEvent(name: String, attributes: [String: AttributeValue], timestamp: Date) {}

    func recordException(_ exception: SpanException) {}
    func recordException(_ exception: SpanException, timestamp: Date) {}
    func recordException(_ exception: SpanException, attributes: [String: AttributeValue]) {}
    func recordException(_ exception: SpanException, attributes: [String: AttributeValue], timestamp: Date) {}

    func end() {}
    func end(time: Date) {}

    var name: String = "test-span"
    var context: SpanContext = SpanContext.create(
        traceId: TraceId.random(),
        spanId: SpanId.random(),
        traceFlags: TraceFlags(),
        traceState: TraceState()
    )
    var kind: SpanKind = .internal
    var status: Status = .ok
    var isRecording: Bool = true

    var description: String { "MockReadableSpan(\(name))" }
}

func makeTestLogRecord(attributes: [String: AttributeValue] = [:]) -> ReadableLogRecord {
    ReadableLogRecord(
        resource: Resource(),
        instrumentationScopeInfo: InstrumentationScopeInfo(name: "test"),
        timestamp: Date(),
        observedTimestamp: Date(),
        spanContext: nil,
        severity: .info,
        body: nil,
        attributes: attributes,
        eventName: nil
    )
}

class MockLogRecordProcessor: LogRecordProcessor {
    var emitCallCount = 0
    var lastEmittedRecord: ReadableLogRecord?

    func onEmit(logRecord: ReadableLogRecord) {
        emitCallCount += 1
        lastEmittedRecord = logRecord
    }

    func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
        return .success
    }

    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        return .success
    }
}
