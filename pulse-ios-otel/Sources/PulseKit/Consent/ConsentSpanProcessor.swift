/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

private let consentSpanBufferLimit = 5000

/// Buffers ended spans while consent is pending; forwards to `BatchSpanProcessor` when allowed.
/// Holds `ReadableSpan` references from `onEnd` (same objects the SDK passes); replay calls `delegate.onEnd` again.
internal final class ConsentSpanProcessor: SpanProcessor {
    private let delegate: BatchSpanProcessor
    private let getState: () -> PulseDataCollectionConsent
    private var buffer: [ReadableSpan] = []
    private let queue = DispatchQueue(label: "com.pulse.consent.span.processor")

    init(delegate: BatchSpanProcessor, getState: @escaping () -> PulseDataCollectionConsent) {
        self.delegate = delegate
        self.getState = getState
    }

    var isStartRequired: Bool { false }
    var isEndRequired: Bool { true }

    func onStart(parentContext: SpanContext?, span: ReadableSpan) {}

    func onEnd(span: ReadableSpan) {
        queue.sync {
            switch getState() {
            case .denied:
                return
            case .pending:
                let available = consentSpanBufferLimit - buffer.count
                if available > 0 {
                    buffer.append(span)
                }
            case .allowed:
                delegate.onEnd(span: span)
            }
        }
    }

    func shutdown(explicitTimeout: TimeInterval?) {
        queue.sync {
            delegate.shutdown(explicitTimeout: explicitTimeout)
        }
    }

    func forceFlush(timeout: TimeInterval?) {
        queue.sync {
            switch getState() {
            case .pending, .denied:
                return
            case .allowed:
                delegate.forceFlush(timeout: timeout)
            }
        }
    }

    /// Replays buffered spans through the batch processor (chunked export).
    func flushBuffer() {
        queue.sync {
            guard !buffer.isEmpty else { return }
            let toFlush = buffer
            buffer.removeAll()
            for s: any ReadableSpan in toFlush {
                delegate.onEnd(span: s)
            }
        }
    }

    func clearBuffer() {
        queue.sync {
            buffer.removeAll()
        }
    }
}
