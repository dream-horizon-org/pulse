/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
#if os(iOS) || os(tvOS)
import UIKit
#endif

internal struct BatterySample: Equatable {
    let levelPercent: Double
    let plugged: String
}

internal protocol BatterySnapshotSource {
    func readSnapshot() -> BatterySample?
}

#if os(iOS) || os(tvOS)
internal struct UIDeviceBatterySnapshotSource: BatterySnapshotSource {
    func readSnapshot() -> BatterySample? {
        assert(Thread.isMainThread, "UIDevice battery must be read on the main thread")
        UIDevice.current.isBatteryMonitoringEnabled = true
        let level = UIDevice.current.batteryLevel
        if level < 0 {
            return nil
        }
        let percent = Double(level) * 100.0
        let plugged: String
        switch UIDevice.current.batteryState {
        case .unknown:
            plugged = "unknown"
        case .unplugged:
            plugged = "battery"
        case .charging, .full:
            plugged = "ac"
        @unknown default:
            plugged = "unknown"
        }
        return BatterySample(levelPercent: percent, plugged: plugged)
    }
}
#endif

/// Periodic battery sampling; consecutive identical level+plug readings are skipped (Android parity).
internal final class BatterySampler: @unchecked Sendable {
    private let logger: OpenTelemetryApi.Logger
    private let flushIntervalMs: Int64
    private let sampleIntervalMs: Int64
    private let snapshotSource: BatterySnapshotSource

    private let lock = NSLock()
    private var levels: [Double] = []
    private var plugged: [String] = []
    private var timestampsMs: [Int64] = []
    private var lastRecordedSample: BatterySample?

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

    private var sampleTimer: Timer?
    private var flushTimer: Timer?

    init(
        logger: OpenTelemetryApi.Logger,
        flushIntervalMs: Int64,
        sampleIntervalMs: Int64,
        snapshotSource: BatterySnapshotSource
    ) {
        self.logger = logger
        self.flushIntervalMs = flushIntervalMs
        self.sampleIntervalMs = sampleIntervalMs
        self.snapshotSource = snapshotSource
    }

    func start() {
        PulseLogger.debug(
            "sdk.system_metrics.battery start=true sample_interval_ms=\(sampleIntervalMs) flush_interval_ms=\(flushIntervalMs)"
        )
        stopTimers()
        DispatchQueue.main.async { [weak self] in
            PulseLogger.debug("sdk.system_metrics.battery called in start -> DispatchQueue.main.async")
            guard let self else { return }
            self.sampleTimer = Timer.scheduledTimer(
                withTimeInterval: TimeInterval(self.sampleIntervalMs) / 1000.0,
                repeats: true
            ) { [weak self] _ in
                self?.collectSampleOnMain()
            }
            self.flushTimer = Timer.scheduledTimer(
                withTimeInterval: TimeInterval(self.flushIntervalMs) / 1000.0,
                repeats: true
            ) { [weak self] _ in
                self?.flushSamples()
            }
            if let sampleTimer = self.sampleTimer {
                RunLoop.main.add(sampleTimer, forMode: .common)
            }
            if let flushTimer = self.flushTimer {
                RunLoop.main.add(flushTimer, forMode: .common)
            }
        }
    }

    func shutdown() {
        PulseLogger.debug("sdk.system_metrics.battery shutdown=true")
        stopTimers()
    }

    private func stopTimers() {
        DispatchQueue.main.async { [weak self] in
            self?.sampleTimer?.invalidate()
            self?.flushTimer?.invalidate()
            self?.sampleTimer = nil
            self?.flushTimer = nil
        }
    }

    func setActive(_ isActive: Bool) {
        PulseLogger.verbose("sdk.system_metrics.battery active=\(isActive)")
        active.set(isActive)
    }

    internal func collectSampleOnMain() {
        guard active.get() else { return }
        guard let sample = snapshotSource.readSnapshot() else {
            PulseLogger.debug("sdk.system_metrics.battery sample_skipped reason=nil_snapshot")
            return
        }
        lock.lock()
        if sample == lastRecordedSample {
            lock.unlock()
            PulseLogger.debug("sdk.system_metrics.battery sample_dropped reason=duplicate")
            return
        }
        lastRecordedSample = sample
        levels.append(sample.levelPercent)
        plugged.append(sample.plugged)
        timestampsMs.append(Int64(Date().timeIntervalSince1970 * 1000))
        PulseLogger.debug("sdk.system_metrics.battery sample.levelPercent = \(sample.levelPercent), sample.plugged = \(sample.plugged)")
        lock.unlock()
    }

    internal func flushSamples() {
        let batch = drainSamples()
        guard !batch.timestamps.isEmpty else { return }
        PulseLogger.debug(
            "sdk.system_metrics.battery flush=true samples=\(batch.timestamps.count) level_last=\(batch.levels.last ?? -1) plugged_last=\(batch.plugged.last ?? "")"
        )

        let attrs: [String: AttributeValue] = [
            PulseDeviceAttributes.systemBatteryLevelArray: Self.doubleArrayAttribute(batch.levels),
            PulseDeviceAttributes.systemBatteryPluggedArray: Self.stringArrayAttribute(batch.plugged),
            PulseDeviceAttributes.systemBatteryTimestampArray: Self.longArrayAttribute(batch.timestamps),
            PulseAttributes.pulseType: .string(PulseAttributes.PulseTypeValues.battery)
        ]

        logger.logRecordBuilder()
            .setEventName(PulseAttributes.PulseTypeValues.battery)
            .setAttributes(attrs)
            .emit()
    }

    private func drainSamples() -> (
        levels: [Double],
        plugged: [String],
        timestamps: [Int64]
    ) {
        lock.lock()
        defer { lock.unlock() }
        let l = levels
        let p = plugged
        let t = timestampsMs
        levels.removeAll(keepingCapacity: true)
        plugged.removeAll(keepingCapacity: true)
        timestampsMs.removeAll(keepingCapacity: true)
        return (l, p, t)
    }

    private static func longArrayAttribute(_ values: [Int64]) -> AttributeValue {
        AttributeValue.array(
            AttributeArray(values: values.map { AttributeValue.int(Int(truncatingIfNeeded: $0)) })
        )
    }

    private static func doubleArrayAttribute(_ values: [Double]) -> AttributeValue {
        AttributeValue.array(
            AttributeArray(values: values.map { AttributeValue.double($0) })
        )
    }

    private static func stringArrayAttribute(_ values: [String]) -> AttributeValue {
        AttributeValue.array(
            AttributeArray(values: values.map { AttributeValue.string($0) })
        )
    }

    internal enum Defaults {
        static var sampleIntervalMs: Int64 {
            #if DEBUG
            return 5_000
            #else
            return 90_000
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
