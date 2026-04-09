/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit

final class AppLifecycleInstrumentationTests: XCTestCase {

    var logExporter: InMemoryLogRecordExporter!
    var loggerProvider: LoggerProviderSdk!

    override func setUp() {
        super.setUp()
        logExporter = InMemoryLogRecordExporter()
        loggerProvider = LoggerProviderBuilder()
            .with(processors: [SimpleLogRecordProcessor(logRecordExporter: logExporter)])
            .build()
    }

    override func tearDown() {
        AppStateWatcher.shared.stop()
        OpenTelemetry.registerLoggerProvider(loggerProvider: DefaultLoggerProvider.instance)
        super.tearDown()
    }

    // MARK: - AppLifecycleInstrumentation emission

    func testAppCreatedEmitsLifecycleLog() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)

        instrumentation.appCreated()

        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0].eventName, AppLifecycleInstrumentation.eventName)
        XCTAssertEqual(records[0].attributes[AppLifecycleInstrumentation.appStateAttributeKey], AttributeValue.string(AppState.created.rawValue))
    }

    func testAppForegroundedEmitsLifecycleLog() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)

        instrumentation.appForegrounded()

        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0].eventName, AppLifecycleInstrumentation.eventName)
        XCTAssertEqual(records[0].attributes[AppLifecycleInstrumentation.appStateAttributeKey], AttributeValue.string(AppState.foreground.rawValue))
    }

    func testAppBackgroundedEmitsLifecycleLog() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)

        instrumentation.appBackgrounded()

        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0].eventName, AppLifecycleInstrumentation.eventName)
        XCTAssertEqual(records[0].attributes[AppLifecycleInstrumentation.appStateAttributeKey], AttributeValue.string(AppState.background.rawValue))
    }

    func testAllThreeStatesEmitDistinctLogs() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)

        instrumentation.appCreated()
        instrumentation.appForegrounded()
        instrumentation.appBackgrounded()

        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 3)
        let states = records.compactMap { record -> String? in
            guard case .string(let s) = record.attributes[AppLifecycleInstrumentation.appStateAttributeKey] else { return nil }
            return s
        }
        XCTAssertEqual(states, [AppState.created.rawValue, AppState.foreground.rawValue, AppState.background.rawValue])
    }

    // MARK: - AppLifecycleInstrumentation uninstall

    func testUninstallDoesNotCrash() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)
        AppStateWatcher.shared.registerListener(instrumentation)

        instrumentation.uninstall()

        // After uninstall, further callbacks should not crash (listener removed)
        instrumentation.appForegrounded()
        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1)
    }

    // MARK: - Edge cases

    /// Uninstall when never registered (e.g. config was disabled) does not crash.
    func testUninstallWhenNotRegisteredDoesNotCrash() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)
        instrumentation.uninstall()
    }

    /// Double uninstall does not crash.
    func testDoubleUninstallDoesNotCrash() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)
        AppStateWatcher.shared.registerListener(instrumentation)
        instrumentation.uninstall()
        instrumentation.uninstall()
    }
}
