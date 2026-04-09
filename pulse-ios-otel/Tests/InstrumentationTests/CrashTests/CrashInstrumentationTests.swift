/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
import OpenTelemetrySdk
import OpenTelemetryApi
@testable import PulseKit

// MARK: - Mock LogRecordBuilder

private class MockLogRecordBuilder: LogRecordBuilder {
    var timestamp: Date?
    var observedTimestamp: Date?
    var spanContext: SpanContext?
    var severity: Severity?
    var body: AttributeValue?
    var attributes: [String: AttributeValue] = [:]
    var eventName: String?
    var emitCalled = false

    func setTimestamp(_ timestamp: Date) -> Self {
        self.timestamp = timestamp
        return self
    }

    func setObservedTimestamp(_ observed: Date) -> Self {
        self.observedTimestamp = observed
        return self
    }

    func setSpanContext(_ context: SpanContext) -> Self {
        self.spanContext = context
        return self
    }

    func setSeverity(_ severity: Severity) -> Self {
        self.severity = severity
        return self
    }

    func setBody(_ body: AttributeValue) -> Self {
        self.body = body
        return self
    }

    func setAttributes(_ attributes: [String: AttributeValue]) -> Self {
        self.attributes = attributes
        return self
    }

    func setEventName(_ eventName: String) -> Self {
        self.eventName = eventName
        return self
    }

    func emit() {
        emitCalled = true
    }
}

// MARK: - Tests

final class CrashInstrumentationTests: XCTestCase {

    // MARK: - extractCrashMessage

    func testExtractCrashMessageWithExceptionType() {
        let stackTrace = """
        Exception Type:  EXC_BREAKPOINT (SIGTRAP)
        Thread 0 Crashed:
        0   libswiftCore.dylib            0x000000019ed5c8c4 $ss17_assertionFailure + 172
        """

        let result = CrashInstrumentation.extractCrashMessage(from: stackTrace)
        XCTAssertEqual(result, "EXC_BREAKPOINT (SIGTRAP) detected on thread 0 at libswiftCore.dylib + 172")
    }

    func testExtractCrashMessageWithBadAccess() {
        let stackTrace = """
        Exception Type:  EXC_BAD_ACCESS (SIGSEGV)
        Thread 2 Crashed:
        0   MyApp                         0x0000000104abc123 main + 456
        """

        let result = CrashInstrumentation.extractCrashMessage(from: stackTrace)
        XCTAssertEqual(result, "EXC_BAD_ACCESS (SIGSEGV) detected on thread 2 at MyApp + 456")
    }

    func testExtractCrashMessageWithoutExceptionType() {
        let stackTrace = """
        Thread 0 Crashed:
        0   libswiftCore.dylib            0x000000019ed5c8c4 $ss17_assertionFailure + 172
        """

        let result = CrashInstrumentation.extractCrashMessage(from: stackTrace)
        XCTAssertEqual(result, "Unknown exception detected on thread 0 at libswiftCore.dylib + 172")
    }

    func testExtractCrashMessageWithDifferentThread() {
        let stackTrace = """
        Exception Type:  EXC_CRASH (SIGABRT)
        Thread 5 Crashed:
        0   SomeFramework                 0x00000001f14e1a90 someFunction + 8
        """

        let result = CrashInstrumentation.extractCrashMessage(from: stackTrace)
        XCTAssertEqual(result, "EXC_CRASH (SIGABRT) detected on thread 5 at SomeFramework + 8")
    }

    func testExtractCrashMessageEmptyString() {
        XCTAssertEqual(
            CrashInstrumentation.extractCrashMessage(from: ""),
            "Unknown exception detected at unknown location"
        )
    }

    func testExtractCrashMessageNoThreadCrashed() {
        let stackTrace = "Some other content\nThread 1:\n0   libsystem_kernel.dylib"
        XCTAssertEqual(
            CrashInstrumentation.extractCrashMessage(from: stackTrace),
            "Unknown exception detected at unknown location"
        )
    }

    func testExtractCrashMessageMalformedCrashedLine() {
        let stackTrace = "Thread Crashed:\n0   SomeFramework"
        XCTAssertEqual(
            CrashInstrumentation.extractCrashMessage(from: stackTrace),
            "Unknown exception detected at unknown location"
        )
    }

    func testExtractCrashMessageExceptionTypeOnly() {
        let stackTrace = """
        Exception Type:  EXC_CRASH (SIGABRT)
        """

        let result = CrashInstrumentation.extractCrashMessage(from: stackTrace)
        XCTAssertEqual(result, "EXC_CRASH (SIGABRT) detected at unknown location")
    }

    func testExtractCrashMessageWithWhitespaceHandling() {
        let stackTrace = """
        Exception Type:  EXC_BAD_ACCESS (SIGSEGV)
        Thread 2 Crashed:
        0     MyFramework     \t\t\t    0x123456789    myFunction    +    123
        """

        let result = CrashInstrumentation.extractCrashMessage(from: stackTrace)
        XCTAssertEqual(result, "EXC_BAD_ACCESS (SIGSEGV) detected on thread 2 at MyFramework + 123")
    }

    // MARK: - CrashReportParser

    func testParseNSException() {
        let json: [String: Any] = [
            "crash": [
                "error": [
                    "nsexception": [
                        "name": "NSRangeException",
                        "reason": "index 10 beyond bounds [0 .. 2]"
                    ]
                ],
                "threads": [
                    [
                        "index": 0,
                        "crashed": true,
                        "dispatch_queue": "com.apple.main-thread",
                        "backtrace": ["contents": []]
                    ]
                ]
            ]
        ]

        let parsed = CrashReportParser.parse(dictionary: json)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.exceptionType, "NSRangeException")
        XCTAssertEqual(parsed?.exceptionMessage, "index 10 beyond bounds [0 .. 2]")
        XCTAssertEqual(parsed?.threadId, "0")
        XCTAssertEqual(parsed?.threadName, "com.apple.main-thread")
    }

    func testParseSignalCrash() {
        let json: [String: Any] = [
            "crash": [
                "error": [
                    "signal": [
                        "name": "SIGABRT",
                        "code": 0
                    ]
                ],
                "diagnosis": "Abort trap triggered",
                "threads": [
                    [
                        "index": 3,
                        "crashed": true,
                        "name": "com.dispatch.worker.7",
                        "backtrace": ["contents": []]
                    ]
                ]
            ]
        ]

        let parsed = CrashReportParser.parse(dictionary: json)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.exceptionType, "SIGABRT")
        XCTAssertEqual(parsed?.exceptionMessage, "Abort trap triggered")
        XCTAssertEqual(parsed?.threadId, "3")
        XCTAssertEqual(parsed?.threadName, "com.dispatch.worker.7")
    }

    func testParseMachException() {
        let json: [String: Any] = [
            "crash": [
                "error": [
                    "mach": [
                        "exception_name": "EXC_BAD_ACCESS",
                        "exception": 1
                    ]
                ],
                "diagnosis": "Attempted to dereference null pointer",
                "threads": [
                    ["index": 0, "crashed": false, "dispatch_queue": "com.apple.main-thread"],
                    ["index": 1, "crashed": true, "name": "WorkerThread"]
                ]
            ]
        ]

        let parsed = CrashReportParser.parse(dictionary: json)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.exceptionType, "EXC_BAD_ACCESS")
        XCTAssertEqual(parsed?.exceptionMessage, "Attempted to dereference null pointer")
        XCTAssertEqual(parsed?.threadId, "1")
        XCTAssertEqual(parsed?.threadName, "WorkerThread")
    }

    func testParseSignalWithNoDiagnosis() {
        let json: [String: Any] = [
            "crash": [
                "error": [
                    "signal": [
                        "name": "SIGSEGV",
                        "code": 11
                    ]
                ],
                "threads": [
                    ["index": 0, "crashed": true, "dispatch_queue": "com.apple.main-thread"]
                ]
            ]
        ]

        let parsed = CrashReportParser.parse(dictionary: json)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.exceptionType, "SIGSEGV")
        XCTAssertEqual(parsed?.exceptionMessage, "SIGSEGV (code: 11)")
        XCTAssertEqual(parsed?.threadId, "0")
    }

    func testParseMissingCrashSection() {
        let json: [String: Any] = ["report": ["timestamp": "2025-01-01"]]
        XCTAssertNil(CrashReportParser.parse(dictionary: json))
    }

    func testParseEmptyDictionary() {
        XCTAssertNil(CrashReportParser.parse(dictionary: [:]))
    }

    func testParseNoThreads() {
        let json: [String: Any] = [
            "crash": [
                "error": [
                    "nsexception": [
                        "name": "TestException",
                        "reason": "test"
                    ]
                ]
            ]
        ]

        let parsed = CrashReportParser.parse(dictionary: json)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.exceptionType, "TestException")
        XCTAssertEqual(parsed?.threadId, "0")
        XCTAssertEqual(parsed?.threadName, "unknown")
    }

    func testParseNoCrashedThread() {
        let json: [String: Any] = [
            "crash": [
                "error": ["signal": ["name": "SIGABRT", "code": 0]],
                "threads": [
                    ["index": 0, "crashed": false, "dispatch_queue": "com.apple.main-thread"],
                    ["index": 1, "crashed": false, "name": "worker"]
                ]
            ]
        ]

        let parsed = CrashReportParser.parse(dictionary: json)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.threadId, "0")
        XCTAssertEqual(parsed?.threadName, "com.apple.main-thread")
    }

    func testParseFromJsonString() {
        let jsonString = """
        {
            "crash": {
                "error": {
                    "nsexception": {
                        "name": "TestCrashException",
                        "reason": "Test reason"
                    }
                },
                "threads": [
                    {
                        "index": 0,
                        "crashed": true,
                        "dispatch_queue": "com.apple.main-thread"
                    }
                ]
            }
        }
        """

        let parsed = CrashReportParser.parse(jsonString: jsonString)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.exceptionType, "TestCrashException")
        XCTAssertEqual(parsed?.exceptionMessage, "Test reason")
    }

    func testParseFromInvalidJsonString() {
        XCTAssertNil(CrashReportParser.parse(jsonString: "not json"))
        XCTAssertNil(CrashReportParser.parse(jsonString: ""))
    }

    // MARK: - recoverCrashContext

    func testRecoverCrashContextWithSessionAndTimestamp() {
        let mockLog = MockLogRecordBuilder()
        let rawCrash: [String: Any] = [
            "report": ["timestamp": "2025-06-15T10:30:00.000Z"],
            "user": [
                "session.id": "crash-session-abc",
                "session.previous_id": "prev-session-xyz"
            ]
        ]
        let inputAttrs: [String: AttributeValue] = [
            CrashAttributes.exceptionType: .string("SIGABRT")
        ]

        let result = CrashInstrumentation.recoverCrashContext(
            from: rawCrash, log: mockLog, attributes: inputAttrs
        )

        XCTAssertNotNil(mockLog.timestamp)
        XCTAssertEqual(result[SessionConstants.id], .string("crash-session-abc"))
        XCTAssertEqual(result[SessionConstants.previousId], .string("prev-session-xyz"))
        XCTAssertEqual(result[CrashAttributes.exceptionType], .string("SIGABRT"))
    }

    func testRecoverCrashContextWithSessionNoPreviousId() {
        let mockLog = MockLogRecordBuilder()
        let rawCrash: [String: Any] = [
            "report": ["timestamp": "2025-06-15T10:30:00.000Z"],
            "user": ["session.id": "session-only"]
        ]

        let result = CrashInstrumentation.recoverCrashContext(
            from: rawCrash, log: mockLog, attributes: [:]
        )

        XCTAssertNotNil(mockLog.timestamp)
        XCTAssertEqual(result[SessionConstants.id], .string("session-only"))
        XCTAssertNil(result[SessionConstants.previousId])
    }

    func testRecoverCrashContextMissingUserInfo() {
        let mockLog = MockLogRecordBuilder()
        let rawCrash: [String: Any] = [
            "report": ["timestamp": "2025-06-15T10:30:00.000Z"]
        ]

        let result = CrashInstrumentation.recoverCrashContext(
            from: rawCrash, log: mockLog, attributes: [:]
        )

        XCTAssertNotNil(mockLog.timestamp, "Should fall back to Date()")
        XCTAssertNil(result[SessionConstants.id])
    }

    func testRecoverCrashContextMissingSessionId() {
        let mockLog = MockLogRecordBuilder()
        let rawCrash: [String: Any] = [
            "report": ["timestamp": "2025-06-15T10:30:00.000Z"],
            "user": ["some_other_key": "value"]
        ]

        let result = CrashInstrumentation.recoverCrashContext(
            from: rawCrash, log: mockLog, attributes: [:]
        )

        XCTAssertNil(result[SessionConstants.id])
    }

    func testRecoverCrashContextMissingReport() {
        let mockLog = MockLogRecordBuilder()
        let rawCrash: [String: Any] = [
            "user": ["session.id": "orphan-session"]
        ]

        let result = CrashInstrumentation.recoverCrashContext(
            from: rawCrash, log: mockLog, attributes: [:]
        )

        XCTAssertNil(result[SessionConstants.id], "Should not recover session without report timestamp")
    }

    func testRecoverCrashContextInvalidTimestamp() {
        let mockLog = MockLogRecordBuilder()
        let rawCrash: [String: Any] = [
            "report": ["timestamp": "not-a-date"],
            "user": ["session.id": "session-123"]
        ]

        let result = CrashInstrumentation.recoverCrashContext(
            from: rawCrash, log: mockLog, attributes: [:]
        )

        XCTAssertNil(result[SessionConstants.id], "Should not recover session with unparseable timestamp")
    }

    func testRecoverCrashContextEmptyDictionary() {
        let mockLog = MockLogRecordBuilder()

        let result = CrashInstrumentation.recoverCrashContext(
            from: [:], log: mockLog, attributes: [:]
        )

        XCTAssertNotNil(mockLog.timestamp, "Should fall back to Date()")
        XCTAssertNil(result[SessionConstants.id])
    }

    func testRecoverCrashContextPreservesExistingAttributes() {
        let mockLog = MockLogRecordBuilder()
        let rawCrash: [String: Any] = [
            "report": ["timestamp": "2025-06-15T10:30:00.000Z"],
            "user": ["session.id": "crash-session"]
        ]
        let inputAttrs: [String: AttributeValue] = [
            CrashAttributes.exceptionType: .string("NSRangeException"),
            CrashAttributes.exceptionMessage: .string("index out of bounds"),
            CrashAttributes.threadId: .string("0"),
            CrashAttributes.threadName: .string("main")
        ]

        let result = CrashInstrumentation.recoverCrashContext(
            from: rawCrash, log: mockLog, attributes: inputAttrs
        )

        XCTAssertEqual(result[CrashAttributes.exceptionType], .string("NSRangeException"))
        XCTAssertEqual(result[CrashAttributes.exceptionMessage], .string("index out of bounds"))
        XCTAssertEqual(result[CrashAttributes.threadId], .string("0"))
        XCTAssertEqual(result[CrashAttributes.threadName], .string("main"))
        XCTAssertEqual(result[SessionConstants.id], .string("crash-session"))
    }

    // MARK: - Constants

    func testCrashAttributeKeys() {
        XCTAssertEqual(CrashAttributes.exceptionMessage, "exception.message")
        XCTAssertEqual(CrashAttributes.exceptionType, "exception.type")
        XCTAssertEqual(CrashAttributes.exceptionStacktrace, "exception.stacktrace")
        XCTAssertEqual(CrashAttributes.threadId, "thread.id")
        XCTAssertEqual(CrashAttributes.threadName, "thread.name")
    }

    func testCrashEventName() {
        XCTAssertEqual(CrashEventName.deviceCrash, "device.crash")
    }

    func testMaxStackTraceBytes() {
        XCTAssertEqual(CrashInstrumentation.maxStackTraceBytes, 25 * 1024)
    }
}
