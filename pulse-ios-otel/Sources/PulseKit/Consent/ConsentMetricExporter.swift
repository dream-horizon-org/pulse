/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

private let consentMetricBufferLimit = 5000

/// Sits **outside** the persistence-backed metric exporter so pending consent does not write metric batches to disk.
/// Batches are buffered (by `MetricData` count, max 5000) while consent is pending; forwarded when allowed.
internal final class ConsentMetricExporter: MetricExporter {
    private let delegate: MetricExporter
    private let getState: () -> PulseDataCollectionConsent
    private var buffer: [MetricData] = []
    private let queue = DispatchQueue(label: "com.pulse.consent.metric.exporter")

    init(delegate: MetricExporter, getState: @escaping () -> PulseDataCollectionConsent) {
        self.delegate = delegate
        self.getState = getState
    }

    func export(metrics: [MetricData]) -> ExportResult {
        queue.sync {
            switch getState() {
            case .denied:
                return .success
            case .pending:
                let available = consentMetricBufferLimit - buffer.count
                guard available > 0 else { return .success }
                buffer.append(contentsOf: metrics.prefix(available))
                return .success
            case .allowed:
                return delegate.export(metrics: metrics)
            }
        }
    }

    func flush() -> ExportResult {
        queue.sync {
            switch getState() {
            case .pending, .denied:
                return .success
            case .allowed:
                return delegate.flush()
            }
        }
    }

    func shutdown() -> ExportResult {
        queue.sync {
            delegate.shutdown()
        }
    }

    func getAggregationTemporality(for instrument: InstrumentType) -> AggregationTemporality {
        delegate.getAggregationTemporality(for: instrument)
    }

    func getDefaultAggregation(for instrument: InstrumentType) -> Aggregation {
        delegate.getDefaultAggregation(for: instrument)
    }

    /// Exports buffered metrics through the delegate (e.g. after consent moves to `.allowed`).
    func flushBuffer() {
        queue.sync {
            guard !buffer.isEmpty else { return }
            let toFlush = buffer
            buffer.removeAll()
            _ = delegate.export(metrics: toFlush)
        }
    }

    func clearBuffer() {
        queue.sync {
            buffer.removeAll()
        }
    }
}
