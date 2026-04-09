/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

final class ConsentLogProcessorTests: XCTestCase {

    func testAllowed_forwardsToDelegate() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.allowed
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.onEmit(logRecord: createLog(eventName: "e1"))
        _ = batch.forceFlush(explicitTimeout: nil)

        XCTAssertEqual(mock.exportedLogs.count, 1)
        XCTAssertEqual(mock.exportedLogs[0].eventName, "e1")
    }

    func testDenied_drops() {
        let mock: MockLogExporter = MockLogExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.denied
        let batch: BatchLogRecordProcessor = makeBatch(mock: mock)
        let processor: ConsentLogProcessor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.onEmit(logRecord: createLog(eventName: "e1"))
        _ = batch.forceFlush(explicitTimeout: nil)

        XCTAssertTrue(mock.exportedLogs.isEmpty)
    }

    func testPending_buffersDoesNotForward() {
        let mock: MockLogExporter = MockLogExporter()
        var state: PulseDataCollectionConsent = PulseDataCollectionConsent.pending
        let batch: BatchLogRecordProcessor = makeBatch(mock: mock)
        let processor: ConsentLogProcessor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.onEmit(logRecord: createLog(eventName: "e1"))
        _ = batch.forceFlush(explicitTimeout: nil)

        XCTAssertTrue(mock.exportedLogs.isEmpty)
    }

    func testPending_thenFlushBuffer_forwardsBuffered() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.pending
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.onEmit(logRecord: createLog(eventName: "a"))
        processor.onEmit(logRecord: createLog(eventName: "b"))
        state = .allowed
        processor.flushBuffer()
        _ = batch.forceFlush(explicitTimeout: nil)

        XCTAssertEqual(mock.exportedLogs.map { $0.eventName ?? "" }, ["a", "b"])
    }

    func testClearBuffer_clearsWithoutForwarding() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.pending
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.onEmit(logRecord: createLog(eventName: "e1"))
        processor.clearBuffer()
        state = .allowed
        processor.flushBuffer()
        _ = batch.forceFlush(explicitTimeout: nil)

        XCTAssertTrue(mock.exportedLogs.isEmpty)
    }

    func testForceFlush_whenAllowed_drainsBatchToExporter() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.allowed
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.onEmit(logRecord: createLog(eventName: "flushed"))
        _ = processor.forceFlush(explicitTimeout: nil)

        XCTAssertEqual(mock.exportedLogs.count, 1)
        XCTAssertEqual(mock.exportedLogs[0].eventName, "flushed")
    }

    func testForceFlush_whenPending_doesNotForward() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.pending
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.onEmit(logRecord: createLog(eventName: "q"))
        _ = processor.forceFlush(explicitTimeout: nil)

        XCTAssertTrue(mock.exportedLogs.isEmpty)
    }

    func testFlushBuffer_emptyBuffer_noOp() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.allowed
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        processor.flushBuffer()
        _ = batch.forceFlush(explicitTimeout: nil)

        XCTAssertTrue(mock.exportedLogs.isEmpty)
    }

    func testBufferCap_keepsFirst5000() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.pending
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        for i in 0 ..< 5001 {
            processor.onEmit(logRecord: createLog(eventName: "log-\(i)"))
        }
        state = .allowed
        processor.flushBuffer()
        _ = batch.forceFlush(explicitTimeout: nil)

        XCTAssertEqual(mock.exportedLogs.count, 5000)
        XCTAssertEqual(mock.exportedLogs.first?.eventName, "log-0")
        XCTAssertEqual(mock.exportedLogs.last?.eventName, "log-4999")
    }

    func testShutdown_forwardsToDelegate() {
        let mock = MockLogExporter()
        var state = PulseDataCollectionConsent.allowed
        let batch = makeBatch(mock: mock)
        let processor = ConsentLogProcessor(delegate: batch, getState: { state })

        _ = processor.shutdown(explicitTimeout: nil)

        XCTAssertEqual(mock.shutdownCallCount, 1)
    }

    private func makeBatch(mock: MockLogExporter) -> BatchLogRecordProcessor {
        BatchLogRecordProcessor(
            logRecordExporter: mock,
            scheduleDelay: 3600,
            exportTimeout: 30,
            maxQueueSize: 10_000,
            maxExportBatchSize: 512
        )
    }

    private func createLog(eventName: String) -> ReadableLogRecord {
        ReadableLogRecord(
            resource: Resource(),
            instrumentationScopeInfo: InstrumentationScopeInfo(name: "test"),
            timestamp: Date(),
            severity: .info,
            body: .string("body"),
            attributes: [:],
            eventName: eventName
        )
    }
}

private final class MockLogExporter: LogRecordExporter {
    var exportedLogs: [ReadableLogRecord] = []
    var shutdownCallCount = 0

    func export(logRecords: [ReadableLogRecord], explicitTimeout: TimeInterval?) -> ExportResult {
        exportedLogs.append(contentsOf: logRecords)
        return .success
    }

    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        .success
    }

    func shutdown(explicitTimeout: TimeInterval?) {
        shutdownCallCount += 1
    }
}
