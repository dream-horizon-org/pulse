/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Rage-click detection configuration.
/// Configured exclusively via backend remote config — not exposed in the public initialization API.
struct RageConfig {
    var timeWindowMs: Int = 2000
    var rageThreshold: Int = 3
    var radiusPt: Float = 50.0
}

/// Configuration for UIKit tap auto-instrumentation.
/// Intercepted automatically for UIControl, gesture-recognized views, and
/// table/collection cells. Emits `app.widget.click` log events with rich context:
/// touch coordinates, label, element type, and accessibility identifiers.
/// Whether this is active is controlled by the backend feature flag, not by app code.
public struct UIKitTapInstrumentationConfig {
    internal private(set) var enabled: Bool = false

    /// When true, extracts rich label context from the tapped view — including
    /// recursive subview text scan for container views (cards, cells, stacks).
    /// Disable for performance-sensitive apps where view hierarchies are large and deep.
    public private(set) var captureContext: Bool = false

    /// Rage-click detection configuration (time window, threshold, radius).
    /// Set exclusively via backend remote config.
    internal private(set) var rage: RageConfig = RageConfig()

    public init(captureContext: Bool = false) {
        self.captureContext = captureContext
    }

    internal mutating func enabled(_ value: Bool) {
        self.enabled = value
    }

    public mutating func captureContext(_ value: Bool) {
        self.captureContext = value
    }

    internal mutating func rage(_ configure: (inout RageConfig) -> Void) {
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
