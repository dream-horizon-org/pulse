/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

internal protocol MemoryMetricsSource: Sendable {
    func systemMemoryUsedBytes() -> Int64?
    func appMemoryUsedBytes() -> Int64?
}

internal struct DarwinMemoryMetricsSource: MemoryMetricsSource {
    func systemMemoryUsedBytes() -> Int64? {
        DarwinMemoryMetrics.systemMemoryUsedBytes()
    }

    func appMemoryUsedBytes() -> Int64? {
        DarwinMemoryMetrics.appPhysFootprintBytes()
    }
}

internal final class MemorySampler: @unchecked Sendable {
    private let logger: OpenTelemetryApi.Logger
    private let flushIntervalNs: UInt64
    private let sampleIntervalNs: UInt64
    private let metricsSource: MemoryMetricsSource
    private let flushIntervalMs: Int64
    private let sampleIntervalMs: Int64

    private let lock = NSLock()
    private var systemUtilization: [Int64] = []
    private var appUtilization: [Int64] = []
    private var timestampsMs: [Int64] = []

    private final class AtomicBool {
        private let lock = NSLock()
        private var value: Bool
        init(_ initial: Bool) { self.value = initial }
        func get() -> Bool {
            lock.lock()
            defer { lock.unlock() }
            return value
        }

        func set(_ newValue: Bool) {
            lock.lock()
            defer { lock.unlock() }
            value = newValue
        }
    }

    private let active = AtomicBool(true)

    private var sampleTask: Task<Void, Never>?
    private var flushTask: Task<Void, Never>?

    init(
        logger: OpenTelemetryApi.Logger,
        flushIntervalMs: Int64,
        sampleIntervalMs: Int64,
        metricsSource: MemoryMetricsSource = DarwinMemoryMetricsSource()
    ) {
        self.logger = logger
        self.flushIntervalMs = flushIntervalMs
        self.sampleIntervalMs = sampleIntervalMs
        self.flushIntervalNs = UInt64(flushIntervalMs) * 1_000_000
        self.sampleIntervalNs = UInt64(sampleIntervalMs) * 1_000_000
        self.metricsSource = metricsSource
    }

    func start() {
        PulseLogger.debug(
            "sdk.system_metrics.memory start=true sample_interval_ms=\(sampleIntervalMs) flush_interval_ms=\(flushIntervalMs)"
        )
        sampleTask?.cancel()
        flushTask?.cancel()
        sampleTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                if self.active.get() {
                    self.collectSample()
                }
                try? await Task.sleep(nanoseconds: self.sampleIntervalNs)
            }
        }
        flushTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: self.flushIntervalNs)
                self.flushSamples()
            }
        }
    }

    func shutdown() {
        PulseLogger.debug("sdk.system_metrics.memory shutdown=true")
        sampleTask?.cancel()
        flushTask?.cancel()
        sampleTask = nil
        flushTask = nil
    }

    func setActive(_ isActive: Bool) {
        PulseLogger.verbose("sdk.system_metrics.memory active=\(isActive)")
        active.set(isActive)
    }

    internal func collectSample() {
        guard active.get() else { return }
        guard let sys = metricsSource.systemMemoryUsedBytes(),
              let app = metricsSource.appMemoryUsedBytes() else { return }
        let ts = Int64(Date().timeIntervalSince1970 * 1000)
        lock.lock()
        defer { lock.unlock() }
        systemUtilization.append(sys)
        appUtilization.append(app)
        timestampsMs.append(ts)
    }

    internal func flushSamples() {
        let batch = drainSamples()
        guard !batch.timestamps.isEmpty else { return }
        PulseLogger.debug(
            "sdk.system_metrics.memory flush=true samples=\(batch.timestamps.count) sys_last=\(batch.system.last ?? -1) app_last=\(batch.app.last ?? -1)"
        )

        var attrs: [String: AttributeValue] = [
            PulseDeviceAttributes.systemMemoryUtilizationArray: Self.longArrayAttribute(batch.system),
            PulseDeviceAttributes.systemMemoryTimestampArray: Self.longArrayAttribute(batch.timestamps),
            PulseDeviceAttributes.appMemoryUtilizationArray: Self.longArrayAttribute(batch.app),
            PulseAttributes.pulseType: .string(PulseAttributes.PulseTypeValues.memory)
        ]

        logger.logRecordBuilder()
            .setEventName(PulseAttributes.PulseTypeValues.memory)
            .setAttributes(attrs)
            .emit()
    }

    private func drainSamples() -> (
        system: [Int64],
        app: [Int64],
        timestamps: [Int64]
    ) {
        lock.lock()
        defer { lock.unlock() }
        let s = systemUtilization
        let a = appUtilization
        let t = timestampsMs
        systemUtilization.removeAll(keepingCapacity: true)
        appUtilization.removeAll(keepingCapacity: true)
        timestampsMs.removeAll(keepingCapacity: true)
        return (s, a, t)
    }

    private static func longArrayAttribute(_ values: [Int64]) -> AttributeValue {
        AttributeValue.array(
            AttributeArray(values: values.map { AttributeValue.int(Int(truncatingIfNeeded: $0)) })
        )
    }

    internal enum Defaults {
        /// Matches Android: shorter intervals in DEBUG builds.
        static var sampleIntervalMs: Int64 {
            #if DEBUG
            return 5_000
            #else
            return 30_000
            #endif
        }

        static var flushIntervalMs: Int64 {
            #if DEBUG
            return 120_000
            #else
            return 900_000
            #endif
        }
    }
}
