/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

#if os(iOS) || os(tvOS)
final class MemoryAppLifecycleBridge: AppStateListener {
    let sampler: MemorySampler

    init(sampler: MemorySampler) {
        self.sampler = sampler
    }

    func appForegrounded() {
        PulseLogger.debug("sdk.system_metrics.memory app_state=foreground active=true")
        sampler.setActive(true)
    }

    func appBackgrounded() {
        PulseLogger.debug("sdk.system_metrics.memory app_state=background active=false")
        sampler.setActive(false)
    }
}
#endif

public struct MemoryInstrumentationConfig {
    public static let loggerScopeName = "io.opentelemetry.memory"

    /// Same values as `MemorySampler.Defaults` — inlined because public stored properties cannot default from internal types.
    private enum BuiltInDefaults {
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
    private static var activeSampler: MemorySampler?
    #if os(iOS) || os(tvOS)
    private static var activeBridge: MemoryAppLifecycleBridge?
    #endif
}

extension MemoryInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard enabled else { return }
        #if os(watchOS)
        return
        #elseif os(iOS) || os(tvOS) || os(macOS)
        Self.gate.lock()
        if Self.activeSampler != nil {
            Self.gate.unlock()
            PulseLogger.debug("sdk.system_metrics.memory install_skipped reason=already_installed")
            return
        }

        let logger = ctx.loggerProvider.get(instrumentationScopeName: Self.loggerScopeName)
        let sampler = MemorySampler(
            logger: logger,
            flushIntervalMs: flushIntervalMs,
            sampleIntervalMs: sampleIntervalMs
        )
        Self.activeSampler = sampler
        Self.gate.unlock()

        #if os(iOS) || os(tvOS)
        let bridge = MemoryAppLifecycleBridge(sampler: sampler)
        Self.gate.lock()
        Self.activeBridge = bridge
        Self.gate.unlock()
        AppStateWatcher.shared.registerListener(bridge)
        AppStateWatcher.shared.start()
        sampler.setActive(AppStateWatcher.shared.currentState != .background)
        PulseLogger.debug(
            "sdk.system_metrics.memory installed=true initial_active=\(AppStateWatcher.shared.currentState != .background)"
        )
        #elseif os(macOS)
        sampler.setActive(true)
        PulseLogger.debug("sdk.system_metrics.memory installed=true platform=macos initial_active=true")
        #endif

        sampler.start()
        #endif
    }

    internal func uninstall() {
        #if os(watchOS)
        return
        #elseif os(iOS) || os(tvOS) || os(macOS)
        Self.gate.lock()
        let sampler = Self.activeSampler
        Self.activeSampler = nil
        #if os(iOS) || os(tvOS)
        let bridge = Self.activeBridge
        Self.activeBridge = nil
        #endif
        Self.gate.unlock()

        PulseLogger.debug("sdk.system_metrics.memory uninstall=true had_sampler=\(sampler != nil)")
        sampler?.shutdown()
        #if os(iOS) || os(tvOS)
        if let bridge {
            AppStateWatcher.shared.removeListener(bridge)
        }
        #endif
        #endif
    }
}
