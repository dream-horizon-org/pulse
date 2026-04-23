/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi
#if canImport(PulseLogging)
import PulseLogging
#endif

/// Throwing fails the batch and emits `sdk.beforesend.error` (coarse `error_class` only).
public typealias BeforeSendSpanCallback = (SpanData) throws -> SpanData?

/// Applies a user-provided closure to each span before export.
/// Runs on the BatchSpanProcessor export thread — do not block.
internal class BeforeSendSpanExporter: SpanExporter {
    private let callback: BeforeSendSpanCallback
    private let delegate: SpanExporter

    init(callback: @escaping BeforeSendSpanCallback, delegate: SpanExporter) {
        self.callback = callback
        self.delegate = delegate
    }

    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        let filtered: [SpanData]
        do {
            filtered = try spans.compactMap { try callback($0) }
        } catch {
            let errClass = PulseErrorClassification.classify(error)
            PulseLogger.error("sdk.beforesend.error signal=span_data error_class=\(errClass)")
            return .failure
        }
        guard !filtered.isEmpty else { return .success }
        return delegate.export(spans: filtered, explicitTimeout: explicitTimeout)
    }

    func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        return delegate.flush(explicitTimeout: explicitTimeout)
    }

    func shutdown(explicitTimeout: TimeInterval?) {
        delegate.shutdown(explicitTimeout: explicitTimeout)
    }
}
