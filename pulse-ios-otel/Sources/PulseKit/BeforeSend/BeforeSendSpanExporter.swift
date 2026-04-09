/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

public typealias BeforeSendSpanCallback = (SpanData) -> SpanData?

/// Applies a user-provided closure to each span before export.
/// Return the span (optionally modified) to export, or nil to drop.
/// Runs on the BatchSpanProcessor export thread — do not block.
internal class BeforeSendSpanExporter: SpanExporter {
    private let callback: BeforeSendSpanCallback
    private let delegate: SpanExporter

    init(callback: @escaping BeforeSendSpanCallback, delegate: SpanExporter) {
        self.callback = callback
        self.delegate = delegate
    }

    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        let filtered = spans.compactMap { callback($0) }
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
