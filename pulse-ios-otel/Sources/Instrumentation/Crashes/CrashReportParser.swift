/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

struct ParsedCrashReport {
    let exceptionMessage: String
    let exceptionType: String
    let threadId: String
    let threadName: String
}

enum CrashReportParser {

    static func parse(dictionary: [String: Any]) -> ParsedCrashReport? {
        guard let crash = dictionary["crash"] as? [String: Any] else { return nil }
        return parseCrashSection(crash)
    }

    static func parse(jsonString: String) -> ParsedCrashReport? {
        guard let data = jsonString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return parse(dictionary: json)
    }

    private static func parseCrashSection(_ crash: [String: Any]) -> ParsedCrashReport? {
        let threads = crash["threads"] as? [[String: Any]]
        let (exceptionType, exceptionMessage) = extractExceptionInfo(crash)
        let (threadId, threadName) = extractCrashedThreadInfo(threads)

        return ParsedCrashReport(
            exceptionMessage: exceptionMessage,
            exceptionType: exceptionType,
            threadId: threadId,
            threadName: threadName
        )
    }

    private static func extractExceptionInfo(
        _ crash: [String: Any]
    ) -> (type: String, message: String) {
        let diagnosis = crash["diagnosis"] as? String ?? ""
        guard let error = crash["error"] as? [String: Any] else {
            return (diagnosis.isEmpty ? "unknown" : diagnosis, diagnosis)
        }

        if let nsexception = error["nsexception"] as? [String: Any] {
            let name = nsexception["name"] as? String ?? "NSException"
            let reason = nsexception["reason"] as? String ?? diagnosis
            return (name, reason)
        }

        if let signal = error["signal"] as? [String: Any] {
            let name = signal["name"] as? String ?? "SIGUNKNOWN"
            let code = signal["code"] as? Int ?? 0
            let message = diagnosis.isEmpty ? "\(name) (code: \(code))" : diagnosis
            return (name, message)
        }

        if let mach = error["mach"] as? [String: Any] {
            let name = mach["exception_name"] as? String
                ?? mach["exception"] as? String
                ?? "Mach exception"
            return (name, diagnosis.isEmpty ? name : diagnosis)
        }

        return (diagnosis.isEmpty ? "unknown" : diagnosis, diagnosis)
    }

    private static func extractCrashedThreadInfo(
        _ threads: [[String: Any]]?
    ) -> (id: String, name: String) {
        guard let threads = threads, !threads.isEmpty else { return ("0", "unknown") }

        let crashedIdx = threads.firstIndex {
            ($0["crashed"] as? NSNumber)?.boolValue == true
        } ?? 0

        let thread = threads[crashedIdx]
        let index = thread["index"] as? Int ?? crashedIdx
        let name = thread["name"] as? String
            ?? thread["dispatch_queue"] as? String
            ?? "Thread \(index)"

        return (String(index), name)
    }
}
