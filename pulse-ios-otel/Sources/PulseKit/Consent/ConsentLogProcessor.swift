/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

private let consentLogBufferLimit = 5000

/// Buffers log records while consent is pending; forwards to `BatchLogRecordProcessor` when allowed.
internal final class ConsentLogProcessor: LogRecordProcessor {
    private let delegate: BatchLogRecordProcessor
    private let getState: () -> PulseDataCollectionConsent
    private var buffer: [ReadableLogRecord] = []
    private let queue = DispatchQueue(label: "com.pulse.consent.log.processor")

    init(delegate: BatchLogRecordProcessor, getState: @escaping () -> PulseDataCollectionConsent) {
        self.delegate = delegate
        self.getState = getState
    }

    func onEmit(logRecord: ReadableLogRecord) {
        queue.sync {
            switch getState() {
            case .denied:
                return
            case .pending:
                let available = consentLogBufferLimit - buffer.count
                if available > 0 {
                    buffer.append(logRecord)
                }
            case .allowed:
                delegate.onEmit(logRecord: logRecord)
            }
        }
    }

    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        queue.sync {
            switch getState() {
            case .pending, .denied:
                return .success
            case .allowed:
                return delegate.forceFlush(explicitTimeout: explicitTimeout)
            }
        }
    }

    func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
        queue.sync {
            delegate.shutdown(explicitTimeout: explicitTimeout)
        }
    }

    func flushBuffer() {
        queue.sync {
            guard !buffer.isEmpty else { return }
            let toFlush = buffer
            buffer.removeAll()
            for record in toFlush {
                delegate.onEmit(logRecord: record)
            }
        }
    }

    func clearBuffer() {
        queue.sync {
            buffer.removeAll()
        }
    }
}
