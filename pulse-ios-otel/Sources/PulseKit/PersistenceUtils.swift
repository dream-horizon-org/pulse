/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
#if canImport(PersistenceExporter)
import PersistenceExporter
#endif

internal class PersistenceUtils {
    private static let storagePath = "com.pulse.persistence"

    private static var storageBaseURL: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            .appendingPathComponent(storagePath, isDirectory: true)
    }

    /// Creates persistence-backed span, log, and metric exporters. On failure, returns the original exporters.
    /// Export from disk is gated by network availability on non-watchOS.
    static func createPersistentExporters(
        spanExporter: SpanExporter,
        logExporter: LogRecordExporter,
        metricExporter: MetricExporter
    ) -> (spanExporter: SpanExporter, logExporter: LogRecordExporter, metricExporter: MetricExporter) {
        let persistenceStorageBase = storageBaseURL

        #if !os(watchOS)
        let monitor: NetworkMonitorProtocol? = try? NetworkMonitor()
        #else
        let monitor: NetworkMonitorProtocol? = nil
        #endif

        let exportCondition: () -> Bool = {
            guard let monitor = monitor else { return true }
            return monitor.getConnection() != .unavailable
        }

        do {
            let persistentSpan = try PersistenceSpanExporterDecorator(
                spanExporter: spanExporter,
                storageURL: persistenceStorageBase.appendingPathComponent("traces", isDirectory: true),
                exportCondition: exportCondition,
                performancePreset: .lowRuntimeImpact
            )
            let persistentLog = try PersistenceLogExporterDecorator(
                logRecordExporter: logExporter,
                storageURL: persistenceStorageBase.appendingPathComponent("logs", isDirectory: true),
                exportCondition: exportCondition,
                performancePreset: .lowRuntimeImpact
            )
            let persistentMetric = try PersistenceMetricExporterDecorator(
                metricExporter: metricExporter,
                storageURL: persistenceStorageBase.appendingPathComponent("metrics", isDirectory: true),
                exportCondition: exportCondition,
                performancePreset: .lowRuntimeImpact
            )
            return (persistentSpan, persistentLog, persistentMetric)
        } catch {
            return (spanExporter, logExporter, metricExporter)
        }
    }

    /// Removes the entire persistence directory from Caches.
    static func clearStorage() {
        let base = storageBaseURL
        guard FileManager.default.fileExists(atPath: base.path) else { return }
        try? FileManager.default.removeItem(at: base)
    }
}
