/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Signal-select exporter: route spans/logs to different exporters by first matching condition (Batch 5, LLD §9).
 */

import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk

// MARK: - PulseSignalSelectExporter

/// Routes each signal to the first matching exporter. Used for custom events → custom endpoint.
public final class PulseSignalSelectExporter {
    private let currentSdkName: PulseSdkName
    private let signalMatcher: PulseSignalMatcher

    public init(
        currentSdkName: PulseSdkName,
        signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher()
    ) {
        self.currentSdkName = currentSdkName
        self.signalMatcher = signalMatcher
    }

    // MARK: - SelectedLogExporter

    /// Routes each log to the first matching exporter. Map order matters: first match wins.
    /// Reversed when iterating so the last-added (most specific) condition is checked first.
    public final class SelectedLogExporter: LogRecordExporter {
        private weak var parent: PulseSignalSelectExporter?
        private let logMap: [(condition: PulseSignalMatchCondition, exporter: LogRecordExporter)]

        init(parent: PulseSignalSelectExporter, logMap: [(PulseSignalMatchCondition, LogRecordExporter)]) {
            self.parent = parent
            self.logMap = logMap.reversed()
        }

        public func export(logRecords: [ReadableLogRecord], explicitTimeout: TimeInterval?) -> ExportResult {
            guard let parent = parent else {
                return logMap.first.map { $0.exporter.export(logRecords: logRecords, explicitTimeout: explicitTimeout) } ?? .success
            }
            var batches: [ObjectIdentifier: (LogRecordExporter, [ReadableLogRecord])] = [:]
            var routedDefault = 0, routedCustom = 0, routedNone = 0
            for record in logRecords {
                let name = PulseSamplingSignalProcessors.logNameForMatching(record)
                let props = PulseSamplingSignalProcessors.attributesToMap(record.attributes)
                let pulseType = props["pulse.type"] as? String ?? "nil"
                let matched = logMap.first(where: { c, _ in
                    parent.signalMatcher.matches(
                        scope: .logs,
                        name: name.isEmpty ? nil : name,
                        props: props,
                        condition: c,
                        sdkName: parent.currentSdkName
                    )
                })
                if let (condition, exporter) = matched {
                    let isCustom = pulseType == PulseAttributes.PulseTypeValues.customEvent
                    if isCustom { routedCustom += 1 } else { routedDefault += 1 }
                    let key = ObjectIdentifier(exporter as AnyObject)
                    if batches[key] == nil { batches[key] = (exporter, []) }
                    batches[key]!.1.append(record)
                } else {
                    routedNone += 1
                }
            }
            var result: ExportResult = .success
            for (_, (exporter, batch)) in batches {
                let r = exporter.export(logRecords: batch, explicitTimeout: explicitTimeout)
                if r == .failure { result = .failure }
            }
            return result
        }

        public func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
            var result: ExportResult = .success
            for (_, exporter) in logMap {
                if exporter.forceFlush(explicitTimeout: explicitTimeout) == .failure { result = .failure }
            }
            return result
        }

        public func shutdown(explicitTimeout: TimeInterval?) {
            for (_, exporter) in logMap {
                exporter.shutdown(explicitTimeout: explicitTimeout)
            }
        }
    }

    // MARK: - SelectedSpanExporter

    /// Routes each span to the first matching exporter. Map order matters: first match wins.
    public final class SelectedSpanExporter: SpanExporter {
        private weak var parent: PulseSignalSelectExporter?
        private let spanMap: [(condition: PulseSignalMatchCondition, exporter: SpanExporter)]

        init(parent: PulseSignalSelectExporter, spanMap: [(PulseSignalMatchCondition, SpanExporter)]) {
            self.parent = parent
            self.spanMap = spanMap.reversed()
        }

        public func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
            guard let parent = parent else {
                return spanMap.first.map { $0.exporter.export(spans: spans, explicitTimeout: explicitTimeout) } ?? .success
            }
            var batches: [ObjectIdentifier: (SpanExporter, [SpanData])] = [:]
            for span in spans {
                let props = PulseSamplingSignalProcessors.attributesToMap(span.attributes)
                if let (_, exporter) = spanMap.first(where: { condition, _ in
                    parent.signalMatcher.matches(
                        scope: .traces,
                        name: span.name.isEmpty ? nil : span.name,
                        props: props,
                        condition: condition,
                        sdkName: parent.currentSdkName
                    )
                }) {
                    let key = ObjectIdentifier(exporter as AnyObject)
                    if batches[key] == nil { batches[key] = (exporter, []) }
                    batches[key]!.1.append(span)
                }
            }
            var result: SpanExporterResultCode = .success
            for (_, (exporter, batch)) in batches {
                if exporter.export(spans: batch, explicitTimeout: explicitTimeout) == .failure { result = .failure }
            }
            return result
        }

        public func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
            var result: SpanExporterResultCode = .success
            for (_, exporter) in spanMap {
                if exporter.flush(explicitTimeout: explicitTimeout) == .failure { result = .failure }
            }
            return result
        }

        public func shutdown(explicitTimeout: TimeInterval?) {
            for (_, exporter) in spanMap {
                exporter.shutdown(explicitTimeout: explicitTimeout)
            }
        }
    }

    // MARK: - Factory methods

    public func makeSelectedLogExporter(logMap: [(PulseSignalMatchCondition, LogRecordExporter)]) -> SelectedLogExporter {
        SelectedLogExporter(parent: self, logMap: logMap)
    }

    public func makeSelectedSpanExporter(spanMap: [(PulseSignalMatchCondition, SpanExporter)]) -> SelectedSpanExporter {
        SelectedSpanExporter(parent: self, spanMap: spanMap)
    }

}
