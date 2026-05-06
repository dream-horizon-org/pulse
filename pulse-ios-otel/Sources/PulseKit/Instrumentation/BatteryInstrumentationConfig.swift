/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

#if os(iOS) || os(tvOS)
final class BatteryAppLifecycleBridge: AppStateListener {
    let sampler: BatterySampler

    init(sampler: BatterySampler) {
        self.sampler = sampler
    }

    func appForegrounded() {
        PulseLogger.debug("sdk.system_metrics.battery app_state=foreground active=true")
        sampler.setActive(true)
    }

    func appBackgrounded() {
        PulseLogger.debug("sdk.system_metrics.battery app_state=background active=false")
        sampler.setActive(false)
    }
}
#endif

/// Periodic battery level instrumentation (`pulse.type` = `battery`). iOS / tvOS only (`UIDevice`).
public struct BatteryInstrumentationConfig {
    public static let loggerScopeName = "io.opentelemetry.battery"

    /// Same values as `BatterySampler.Defaults` — inlined because public stored properties cannot default from internal types.
    private enum BuiltInDefaults {
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

    public private(set) var enabled: Bool = true
    public private(set) var sampleIntervalMs: Int64 = BuiltInDefaults.sampleIntervalMs
    public private(set) var flushIntervalMs: Int64 = BuiltInDefaults.flushIntervalMs

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }

    public mutating func sampleIntervalMs(_ ms: Int64) {
        guard ms > 0 else { return }
        sampleIntervalMs = ms
    }

    public mutating func flushIntervalMs(_ ms: Int64) {
        guard ms > 0 else { return }
        flushIntervalMs = ms
    }

    private static let gate = NSLock()
    #if os(iOS) || os(tvOS)
    private static var activeSampler: BatterySampler?
    private static var activeBridge: BatteryAppLifecycleBridge?
    #endif
}

extension BatteryInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard enabled else { return }
        #if os(iOS) || os(tvOS)
        Self.gate.lock()
        if Self.activeSampler != nil {
            Self.gate.unlock()
            PulseLogger.debug("sdk.system_metrics.battery install_skipped reason=already_installed")
            return
        }

        let logger = ctx.loggerProvider.get(instrumentationScopeName: Self.loggerScopeName)
        let sampler = BatterySampler(
            logger: logger,
            flushIntervalMs: flushIntervalMs,
            sampleIntervalMs: sampleIntervalMs,
            snapshotSource: UIDeviceBatterySnapshotSource()
        )
        Self.activeSampler = sampler
        let bridge = BatteryAppLifecycleBridge(sampler: sampler)
        Self.activeBridge = bridge
        Self.gate.unlock()

        AppStateWatcher.shared.registerListener(bridge)
        AppStateWatcher.shared.start()
        sampler.setActive(AppStateWatcher.shared.currentState != .background)
        PulseLogger.debug(
            "sdk.system_metrics.battery installed=true initial_active=\(AppStateWatcher.shared.currentState != .background)"
        )
        sampler.start()
        #endif
    }

    internal func uninstall() {
        #if os(iOS) || os(tvOS)
        Self.gate.lock()
        let sampler = Self.activeSampler
        let bridge = Self.activeBridge
        Self.activeSampler = nil
        Self.activeBridge = nil
        Self.gate.unlock()

        PulseLogger.debug("sdk.system_metrics.battery uninstall=true had_sampler=\(sampler != nil)")
        sampler?.shutdown()
        if let bridge {
            AppStateWatcher.shared.removeListener(bridge)
        }
        #endif
    }
}
