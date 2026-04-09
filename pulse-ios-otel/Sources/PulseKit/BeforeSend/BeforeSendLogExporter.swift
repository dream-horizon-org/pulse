/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

public typealias BeforeSendLogCallback = (ReadableLogRecord) -> ReadableLogRecord?

/// Applies a user-provided closure to each log record before export.
/// Return the log (optionally modified) to export, or nil to drop.
/// Runs on the BatchLogRecordProcessor export thread — do not block.
internal class BeforeSendLogExporter: LogRecordExporter {
    private let callback: BeforeSendLogCallback
    private let delegate: LogRecordExporter

    init(callback: @escaping BeforeSendLogCallback, delegate: LogRecordExporter) {
        self.callback = callback
        self.delegate = delegate
    }

    func export(logRecords: [ReadableLogRecord], explicitTimeout: TimeInterval?) -> ExportResult {
        let filtered = logRecords.compactMap { callback($0) }
        guard !filtered.isEmpty else { return .success }
        return delegate.export(logRecords: filtered, explicitTimeout: explicitTimeout)
    }

    func shutdown(explicitTimeout: TimeInterval?) {
        delegate.shutdown(explicitTimeout: explicitTimeout)
    }

    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        return delegate.forceFlush(explicitTimeout: explicitTimeout)
    }
}
