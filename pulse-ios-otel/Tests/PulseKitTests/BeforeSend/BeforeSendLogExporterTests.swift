import XCTest
@testable import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

final class BeforeSendLogExporterTests: XCTestCase {

    func testPassThrough() {
        let mock = MockLogExporter()
        let exporter = BeforeSendLogExporter(callback: { $0 }, delegate: mock)

        let log1 = createLog(eventName: "log-1")
        let log2 = createLog(eventName: "log-2")

        let result = exporter.export(logRecords: [log1, log2], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedLogs.count, 2)
        XCTAssertEqual(mock.exportedLogs[0].eventName, "log-1")
        XCTAssertEqual(mock.exportedLogs[1].eventName, "log-2")
    }

    func testDropSpecificLog() {
        let mock = MockLogExporter()
        let exporter = BeforeSendLogExporter(callback: { log in
            log.eventName == "drop-me" ? nil : log
        }, delegate: mock)

        let keep = createLog(eventName: "keep")
        let drop = createLog(eventName: "drop-me")
        let alsoKeep = createLog(eventName: "also-keep")

        let result = exporter.export(logRecords: [keep, drop, alsoKeep], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedLogs.count, 2)
        XCTAssertEqual(mock.exportedLogs[0].eventName, "keep")
        XCTAssertEqual(mock.exportedLogs[1].eventName, "also-keep")
    }

    func testDropAllReturnsSuccess() {
        let mock = MockLogExporter()
        let exporter = BeforeSendLogExporter(callback: { _ in nil }, delegate: mock)

        let result = exporter.export(logRecords: [createLog(eventName: "a"), createLog(eventName: "b")], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertTrue(mock.exportedLogs.isEmpty)
    }

    func testModifyAttributes() {
        let mock = MockLogExporter()
        let exporter = BeforeSendLogExporter(callback: { log in
            var modified = log
            modified.setAttribute(key: "user.id", value: nil)
            return modified
        }, delegate: mock)

        let log = createLog(eventName: "log-1", attributes: [
            "user.id": .string("secret-123"),
            "other.key": .string("keep-me"),
        ])

        let result = exporter.export(logRecords: [log], explicitTimeout: nil)

        XCTAssertEqual(result, .success)
        XCTAssertEqual(mock.exportedLogs.count, 1)
        XCTAssertNil(mock.exportedLogs[0].attributes["user.id"])
        XCTAssertEqual(mock.exportedLogs[0].attributes["other.key"], .string("keep-me"))
    }

    // MARK: - Helpers

    private func createLog(eventName: String, attributes: [String: AttributeValue] = [:]) -> ReadableLogRecord {
        return ReadableLogRecord(
            resource: Resource(),
            instrumentationScopeInfo: InstrumentationScopeInfo(name: "test"),
            timestamp: Date(),
            severity: .info,
            body: .string("test body"),
            attributes: attributes,
            eventName: eventName
        )
    }
}

private class MockLogExporter: LogRecordExporter {
    var exportedLogs: [ReadableLogRecord] = []

    func export(logRecords: [ReadableLogRecord], explicitTimeout: TimeInterval?) -> ExportResult {
        exportedLogs.append(contentsOf: logRecords)
        return .success
    }

    func shutdown(explicitTimeout: TimeInterval?) {}
    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult { .success }
}
