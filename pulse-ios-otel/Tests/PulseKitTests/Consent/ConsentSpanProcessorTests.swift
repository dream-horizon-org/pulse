/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

final class ConsentSpanProcessorTests: XCTestCase {

    func testAllowed_forwardsToDelegate() {
        let mock: MockSpanExporter = MockSpanExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.allowed
        let batch: BatchSpanProcessor = makeBatch(mock: mock)
        let processor: ConsentSpanProcessor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.onEnd(span: frozenSpan(name: "s1"))
        batch.forceFlush()

        XCTAssertEqual(mock.exportedSpans.count, 1)
        XCTAssertEqual(mock.exportedSpans[0].name, "s1")
    }

    func testDenied_drops() {
        let mock: MockSpanExporter = MockSpanExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.denied
        let batch: BatchSpanProcessor = makeBatch(mock: mock)
        let processor: ConsentSpanProcessor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.onEnd(span: frozenSpan(name: "s1"))
        batch.forceFlush()

        XCTAssertTrue(mock.exportedSpans.isEmpty)
    }

    func testPending_buffersDoesNotForward() {
        let mock: MockSpanExporter = MockSpanExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.pending
        let batch: BatchSpanProcessor = makeBatch(mock: mock)
        let processor: ConsentSpanProcessor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.onEnd(span: frozenSpan(name: "s1"))
        batch.forceFlush()

        XCTAssertTrue(mock.exportedSpans.isEmpty)
    }

    func testPending_thenFlushBuffer_forwardsBufferedInOrder() {
        let mock: MockSpanExporter = MockSpanExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.pending
        let batch: BatchSpanProcessor = makeBatch(mock: mock)
        let processor: ConsentSpanProcessor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.onEnd(span: frozenSpan(name: "a"))
        processor.onEnd(span: frozenSpan(name: "b"))
        state = .allowed
        processor.flushBuffer()
        batch.forceFlush()

        XCTAssertEqual(mock.exportedSpans.map(\.name), ["a", "b"])
    }

    func testClearBuffer_clearsWithoutForwarding() {
        let mock: MockSpanExporter = MockSpanExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.pending
        let batch: BatchSpanProcessor = makeBatch(mock: mock)
        let processor: ConsentSpanProcessor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.onEnd(span: frozenSpan(name: "s1"))
        processor.clearBuffer()
        state = .allowed
        processor.flushBuffer()
        batch.forceFlush()

        XCTAssertTrue(mock.exportedSpans.isEmpty)
    }

    func testForceFlush_whenAllowed_drainsBatchToExporter() {
        let mock: MockSpanExporter = MockSpanExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.allowed
        let batch: BatchSpanProcessor = makeBatch(mock: mock)
        let processor: ConsentSpanProcessor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.onEnd(span: frozenSpan(name: "flushed"))
        processor.forceFlush(timeout: nil)

        XCTAssertEqual(mock.exportedSpans.count, 1)
        XCTAssertEqual(mock.exportedSpans[0].name, "flushed")
    }

    func testForceFlush_whenPending_doesNotDrainBatch() {
        let mock = MockSpanExporter()
        var state = PulseDataCollectionConsent.pending
        let batch = makeBatch(mock: mock)
        let processor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.onEnd(span: frozenSpan(name: "held"))
        processor.forceFlush(timeout: nil)
        batch.forceFlush()

        XCTAssertTrue(mock.exportedSpans.isEmpty)
    }

    func testFlushBuffer_emptyBuffer_noOp() {
        let mock = MockSpanExporter()
        var state = PulseDataCollectionConsent.allowed
        let batch = makeBatch(mock: mock)
        let processor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.flushBuffer()
        batch.forceFlush()

        XCTAssertTrue(mock.exportedSpans.isEmpty)
    }

    func testBufferCap_keepsFirst5000() {
        let mock = MockSpanExporter()
        var state = PulseDataCollectionConsent.pending
        let batch = makeBatch(mock: mock)
        let processor = ConsentSpanProcessor(delegate: batch, getState: { state })

        for i in 0 ..< 5001 {
            processor.onEnd(span: frozenSpan(name: "span-\(i)"))
        }
        state = .allowed
        processor.flushBuffer()
        batch.forceFlush()

        XCTAssertEqual(mock.exportedSpans.count, 5000)
        XCTAssertEqual(mock.exportedSpans.first?.name, "span-0")
        XCTAssertEqual(mock.exportedSpans.last?.name, "span-4999")
    }

    func testShutdown_forwardsToDelegate() {
        let mock = MockSpanExporter()
        var state = PulseDataCollectionConsent.allowed
        let batch = makeBatch(mock: mock)
        let processor = ConsentSpanProcessor(delegate: batch, getState: { state })

        processor.shutdown(explicitTimeout: nil)

        XCTAssertEqual(mock.shutdownCallCount, 1)
    }

    // MARK: - Helpers

    private func makeBatch(mock: MockSpanExporter) -> BatchSpanProcessor {
        BatchSpanProcessor(
            spanExporter: mock,
            scheduleDelay: 3600,
            exportTimeout: 30,
            maxQueueSize: 10_000,
            maxExportBatchSize: 512
        )
    }

    private func frozenSpan(name: String) -> ReadableSpan {
        TestReadableSpanFromData(data: spanData(name: name))
    }

    private func spanData(name: String) -> SpanData {
        var spanData = SpanData(
            traceId: TraceId.random(),
            spanId: SpanId.random(),
            traceFlags: TraceFlags().settingIsSampled(true),
            name: name,
            kind: .internal,
            startTime: Date(),
            endTime: Date().addingTimeInterval(0.1)
        )
        spanData.settingHasEnded(true)
        spanData.settingTotalRecordedEvents(0)
        spanData.settingTotalRecordedLinks(0)
        spanData.settingLinks([])
        spanData.settingStatus(.ok)
        return spanData
    }
}

/// Minimal `ReadableSpan` from `SpanData` for unit tests (sampled traces).
private final class TestReadableSpanFromData: ReadableSpan {
    private let data: SpanData

    init(data: SpanData) {
        self.data = data
    }

    var kind: SpanKind { data.kind }
    var context: SpanContext {
        SpanContext.create(
            traceId: data.traceId,
            spanId: data.spanId,
            traceFlags: data.traceFlags,
            traceState: data.traceState
        )
    }
    var isRecording: Bool { false }
    var status: Status { get { data.status } set { } }
    var name: String { get { data.name } set { } }
    var instrumentationScopeInfo: InstrumentationScopeInfo { data.instrumentationScope }
    var hasEnded: Bool { data.hasEnded }
    var latency: TimeInterval { data.endTime.timeIntervalSince(data.startTime) }
    func toSpanData() -> SpanData { data }
    func getAttributes() -> [String: AttributeValue] { data.attributes }
    func setAttribute(key: String, value: AttributeValue?) {}
    func setAttributes(_ attributes: [String: AttributeValue]) {}
    func addEvent(name: String) {}
    func addEvent(name: String, timestamp: Date) {}
    func addEvent(name: String, attributes: [String: AttributeValue]) {}
    func addEvent(name: String, attributes: [String: AttributeValue], timestamp: Date) {}
    func end() {}
    func end(time: Date) {}
    func recordException(_ exception: SpanException) {}
    func recordException(_ exception: SpanException, timestamp: Date) {}
    func recordException(_ exception: SpanException, attributes: [String: AttributeValue]) {}
    func recordException(_ exception: SpanException, attributes: [String: AttributeValue], timestamp: Date) {}
    var description: String { "TestReadableSpanFromData(\(data.name))" }
}

private final class MockSpanExporter: SpanExporter {
    var exportedSpans: [SpanData] = []
    var shutdownCallCount = 0

    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        exportedSpans.append(contentsOf: spans)
        return .success
    }

    func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        .success
    }

    func shutdown(explicitTimeout: TimeInterval?) {
        shutdownCallCount += 1
    }
}
