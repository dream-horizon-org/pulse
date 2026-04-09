/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk

#if canImport(KSCrashRecording)
  @_implementationOnly import KSCrashRecording
#elseif canImport(KSCrash)
  @_implementationOnly import KSCrash
#endif

#if canImport(KSCrashFilters)
  @_implementationOnly import KSCrashFilters
#endif

public enum CrashEventName {
    public static let deviceCrash = "device.crash"
}

/// Captures native crashes via KSCrash and emits them as `device.crash` OTel log events.

public final class CrashInstrumentation {
    static let maxStackTraceBytes = 25 * 1024

    public private(set) static var isInstalled: Bool = false

    private static var logger: Logger?
    static let reporter = KSCrash.shared
    static var observers: [NSObjectProtocol] = []
    private static let queue = DispatchQueue(label: "com.pulse.ios.sdk.crash", qos: .utility)

    private static let timestampFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private let loggerProvider: LoggerProvider
    private let instrumentationScopeName: String
    private let instrumentationVersion: String
    private let flushLogProcessor: (() -> Void)?

    public init(
        loggerProvider: LoggerProvider,
        instrumentationScopeName: String = "com.pulse.ios.sdk.crash",
        instrumentationVersion: String = "0.0.1",
        flushLogProcessor: (() -> Void)? = nil
    ) {
        self.loggerProvider = loggerProvider
        self.instrumentationScopeName = instrumentationScopeName
        self.instrumentationVersion = instrumentationVersion
        self.flushLogProcessor = flushLogProcessor
    }

    public func install() {
        guard !CrashInstrumentation.isInstalled else { return }

        CrashInstrumentation.logger = loggerProvider.get(
            instrumentationScopeName: instrumentationScopeName
        )

        do {
            try CrashInstrumentation.reporter.install(with: KSCrashConfiguration())
            CrashInstrumentation.isInstalled = true
        } catch {
            print("[Pulse] Failed to install KSCrash: \(error)")
            return
        }

        CrashInstrumentation.cacheCrashContext()
        CrashInstrumentation.setupNotificationObservers()

        let flush = self.flushLogProcessor
        CrashInstrumentation.queue.async {
            let hadCrashes = CrashInstrumentation.processStoredCrashes()
            if hadCrashes {
                flush?()
            }
        }
    }

    // MARK: - Session Context Caching

    /// Writes the current session ID into `KSCrash.shared.userInfo`.
    /// KSCrash persists this dict alongside every crash report it writes,
    /// so the session that was active at crash time can be recovered on next launch.
    static func cacheCrashContext(session: Session? = nil) {
        var userInfo: [String: Any] = [:]

        let sessionManager = SessionManagerProvider.getInstance()
        if let session = session ?? sessionManager.peekSession() {
            userInfo[SessionConstants.id] = session.id
            if let prevId = session.previousId {
                userInfo[SessionConstants.previousId] = prevId
            }
        }

        reporter.userInfo = userInfo
    }

    /// Listens for session rotation so `userInfo` always reflects the active session.
    static func setupNotificationObservers() {
        let observer = NotificationCenter.default.addObserver(
            forName: Notification.Name(SessionConstants.sessionEventNotification),
            object: nil,
            queue: nil
        ) { notification in
            if let event = notification.object as? SessionEvent {
                queue.async {
                    cacheCrashContext(session: event.session)
                }
            }
        }
        observers.append(observer)
    }

    /// Removes notification observers and resets state.
    public static func uninstall() {
        for observer in observers {
            NotificationCenter.default.removeObserver(observer)
        }
        observers.removeAll()
        isInstalled = false
      //TODO: implement complete uninstall for crashes inclduing kscrash handlers 
    }

    // MARK: - Report Processing

    /// Processes any crash reports from the previous session. Emits crash log records for each.
    /// - Returns: `true` if at least one crash was processed (previous session had a crash), `false` otherwise.
    static func processStoredCrashes() -> Bool {
        var hadValidCrash = false
        guard let logger = logger,
              let reportStore = reporter.reportStore else { return hadValidCrash }

        let reportIDs = reportStore.reportIDs
        guard !reportIDs.isEmpty else { return hadValidCrash }

        for reportID in reportIDs {
            guard let id = reportID as? Int64,
                  let crashReport = reportStore.report(for: id) else { continue }

            reportCrash(crashReport: crashReport, logger: logger)
            reportStore.deleteReport(with: id)
            hadValidCrash = true
        }
        return hadValidCrash
    }

    private static func reportCrash(crashReport: CrashReportDictionary, logger: Logger) {
        let rawCrash = crashReport.value
        let log: any LogRecordBuilder = logger.logRecordBuilder()
            .setEventName(CrashEventName.deviceCrash)

        var attributes: [String: AttributeValue] = [:]

        if let parsed = CrashReportParser.parse(dictionary: rawCrash) {
            attributes[CrashAttributes.exceptionType] = .string(parsed.exceptionType)
            attributes[CrashAttributes.exceptionMessage] = .string(parsed.exceptionMessage)
            attributes[CrashAttributes.threadId] = .string(parsed.threadId)
            attributes[CrashAttributes.threadName] = .string(parsed.threadName)
        } else {
            attributes[CrashAttributes.exceptionType] = .string("crash")
        }

        // Recover session context that was cached in userInfo at crash time
        attributes = recoverCrashContext(from: rawCrash, log: log, attributes: attributes)

        CrashReportFilterAppleFmt().filterReports([crashReport]) { reports, _ in
            var appleReport = (reports?.first as? CrashReportString)?.value
                ?? "Failed to format crash report"

            if appleReport.utf8.count > maxStackTraceBytes {
                appleReport = String(appleReport.utf8.prefix(maxStackTraceBytes)) ?? appleReport
            }

            attributes[CrashAttributes.exceptionStacktrace] = .string(appleReport)

            if attributes[CrashAttributes.exceptionMessage] == nil {
                attributes[CrashAttributes.exceptionMessage] = .string(
                    extractCrashMessage(from: appleReport)
                )
            }

            _ = log.setAttributes(attributes)
            log.emit()
        }
    }

    /// Recovers the session ID and timestamp from the crash report's `user` section.
    /// If recovery succeeds, the log timestamp is set to the original crash time so
    /// the event is stitched to the correct session. Otherwise falls back to now.
    static func recoverCrashContext(
        from rawCrash: [String: Any],
        log: LogRecordBuilder,
        attributes: [String: AttributeValue]
    ) -> [String: AttributeValue] {
        guard let report = rawCrash[CrashAttributes.reportKey] as? [String: Any],
              let timestampString = report[CrashAttributes.timestampKey] as? String,
              let timestamp = timestampFormatter.date(from: timestampString),
              let userInfo = rawCrash[CrashAttributes.userKey] as? [String: Any],
              let sessionId = userInfo[SessionConstants.id] as? String
        else {
            _ = log.setTimestamp(Date())
            return attributes
        }

        var result = attributes
        _ = log.setTimestamp(timestamp)
        result[SessionConstants.id] = .string(sessionId)

        if let previousId = userInfo[SessionConstants.previousId] as? String {
            result[SessionConstants.previousId] = .string(previousId)
        }

        return result
    }

    // MARK: - Helpers

    static func extractCrashMessage(from stackTrace: String) -> String {
        let lines = stackTrace.components(separatedBy: "\n")

        let exceptionType = lines.first(where: { $0.hasPrefix("Exception Type:") })?
            .replacingOccurrences(of: "Exception Type:", with: "")
            .trimmingCharacters(in: .whitespaces) ?? "Unknown exception"

        guard let crashedLine = lines.first(where: {
                  $0.range(of: #"Thread \d+ Crashed:"#, options: .regularExpression) != nil
              }),
              let threadMatch = crashedLine.range(
                  of: #"Thread (\d+) Crashed:"#, options: .regularExpression
              ),
              let crashedIndex = lines.firstIndex(of: crashedLine),
              let firstFrame = lines.dropFirst(crashedIndex + 1)
                  .first(where: { $0.hasPrefix("0   ") })
        else {
            return "\(exceptionType) detected at unknown location"
        }

        let threadNumber = String(crashedLine[threadMatch])
            .replacingOccurrences(
                of: #"Thread (\d+) Crashed:"#, with: "$1",
                options: .regularExpression
            )

        let frameComponents = firstFrame
            .components(separatedBy: .whitespaces)
            .filter { !$0.isEmpty }

        guard frameComponents.count >= 4,
              let module = frameComponents.dropFirst().first,
              let offset = frameComponents.last
        else {
            return "\(exceptionType) detected on thread \(threadNumber) at unknown location"
        }

        return "\(exceptionType) detected on thread \(threadNumber) at \(module) + \(offset)"
    }
}

enum CrashAttributes {
    static let exceptionMessage = "exception.message"
    static let exceptionType = "exception.type"
    static let exceptionStacktrace = "exception.stacktrace"
    static let threadId = "thread.id"
    static let threadName = "thread.name"

    // KSCrash JSON report keys
    static let reportKey = "report"
    static let timestampKey = "timestamp"
    static let userKey = "user"
}
