/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

public typealias BeforeSendMetricCallback = (MetricData) throws -> MetricData?

/// Applies a user-provided closure to each metric before export.
/// Return the metric (optionally modified) to export, or nil to drop.
/// Runs on the metric reader's export thread — do not block.
internal class BeforeSendMetricExporter: MetricExporter {
    private let callback: BeforeSendMetricCallback
    private let delegate: any MetricExporter

    init(callback: @escaping BeforeSendMetricCallback, delegate: any MetricExporter) {
        self.callback = callback
        self.delegate = delegate
    }

    func export(metrics: [MetricData]) -> ExportResult {
        let filtered: [MetricData]
        do {
            filtered = try metrics.compactMap { try callback($0) }
        } catch {
            let errClass = PulseErrorClassification.classify(error)
            PulseLogger.error("sdk.beforesend.error signal=metrics error_class=\(errClass)")
            return .failure
        }
        guard !filtered.isEmpty else { return .success }
        return delegate.export(metrics: filtered)
    }

    func flush() -> ExportResult {
        return delegate.flush()
    }

    func shutdown() -> ExportResult {
        return delegate.shutdown()
    }

    func getAggregationTemporality(for instrument: InstrumentType) -> AggregationTemporality {
        return delegate.getAggregationTemporality(for: instrument)
    }

    func getDefaultAggregation(for instrument: InstrumentType) -> Aggregation {
        return delegate.getDefaultAggregation(for: instrument)
    }
}
