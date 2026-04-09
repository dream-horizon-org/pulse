/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit

final class AppStateWatcherTests: XCTestCase {

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

    func testCurrentStateInitiallyCreated() {
        XCTAssertEqual(AppStateWatcher.shared.currentState, .created)
    }

    func testStartAndStopDoNotCrash() {
        AppStateWatcher.shared.start()
        AppStateWatcher.shared.stop()
    }

    func testRemoveListenerWhenNotRegisteredDoesNotCrash() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)
        AppStateWatcher.shared.removeListener(instrumentation)
    }

    func testRegisterAndRemoveListener() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)

        AppStateWatcher.shared.registerListener(instrumentation)
        instrumentation.appCreated()
        var records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1)

        AppStateWatcher.shared.removeListener(instrumentation)
        instrumentation.appForegrounded()
        records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 2, "One from appCreated, one from direct appForegrounded; removeListener only stops watcher from notifying")
    }

    // MARK: - Edge cases

    /// Registering the same listener twice is idempotent; one remove clears it.
    func testRegisterSameListenerTwiceThenRemoveOnce() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)

        AppStateWatcher.shared.registerListener(instrumentation)
        AppStateWatcher.shared.registerListener(instrumentation)
        AppStateWatcher.shared.removeListener(instrumentation)
        instrumentation.appForegrounded()
        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1, "After register twice + remove once, listener is no longer in watcher; direct call still emits one log")
    }

    /// Removing the same listener multiple times does not crash.
    func testRemoveListenerTwiceDoesNotCrash() {
        let logger = loggerProvider.get(instrumentationScopeName: "test.app.lifecycle")
        let instrumentation = AppLifecycleInstrumentation(logger: logger)
        AppStateWatcher.shared.registerListener(instrumentation)
        AppStateWatcher.shared.removeListener(instrumentation)
        AppStateWatcher.shared.removeListener(instrumentation)
    }

    /// start() then stop() then start() again does not crash (e.g. re-init scenario).
    func testStartStopStartDoesNotCrash() {
        AppStateWatcher.shared.start()
        AppStateWatcher.shared.stop()
        AppStateWatcher.shared.start()
        AppStateWatcher.shared.stop()
    }
}
