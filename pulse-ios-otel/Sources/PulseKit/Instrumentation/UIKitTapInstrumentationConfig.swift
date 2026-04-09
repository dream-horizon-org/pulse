/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Rage-click detection configuration.
/// Controls sensitivity of rage detection: how many taps within what time window and radius trigger rage.
public struct RageConfig {
    public var timeWindowMs: Int = 2000
    public var rageThreshold: Int = 3
    public var radiusPt: Float = 50.0
    
    public init(timeWindowMs: Int = 2000, rageThreshold: Int = 3, radiusPt: Float = 50.0) {
        self.timeWindowMs = timeWindowMs
        self.rageThreshold = rageThreshold
        self.radiusPt = radiusPt
    }
}

/// Configuration for UIKit tap auto-instrumentation.
/// When enabled, automatically intercepts all user taps (UIControl, gesture-recognized views,
/// table/collection cells) and emits `app.widget.click` log events with rich context:
/// touch coordinates, label, element type, and accessibility identifiers.
public struct UIKitTapInstrumentationConfig {
    public private(set) var enabled: Bool = false

    /// When true, extracts rich label context from the tapped view — including
    /// recursive subview text scan for container views (cards, cells, stacks).
    /// Disable for performance-sensitive apps where view hierarchies are large and deep.
    public private(set) var captureContext: Bool = false
    
    /// Rage-click detection configuration (time window, threshold, radius).
    /// Backend config overrides these defaults if present.
    public private(set) var rage: RageConfig = RageConfig()

    public init(enabled: Bool = false, captureContext: Bool = false, rage: RageConfig = RageConfig()) {
        self.enabled = enabled
        self.captureContext = captureContext
        self.rage = rage
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }

    public mutating func captureContext(_ value: Bool) {
        self.captureContext = value
    }
    
    public mutating func rage(_ configure: (inout RageConfig) -> Void) {
        configure(&rage)
    }
}

extension UIKitTapInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        #if os(iOS) || os(tvOS)
        let logger = ctx.loggerProvider.get(
            instrumentationScopeName: PulseKitConstants.instrumentationScopeName
        )
        UIWindowSwizzler.swizzle(logger: logger, captureContext: captureContext, rageConfig: rage)
        #endif
    }

    internal func uninstall() {
        #if os(iOS) || os(tvOS)
        UIWindowSwizzler.uninstall()
        #endif
    }
}
