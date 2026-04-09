/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// Configuration for app startup instrumentation.
/// When enabled, emits an AppStart span measuring time from SDK init to first screen appearance.
public struct AppStartupInstrumentationConfig {
    public private(set) var enabled: Bool = true

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }
}

extension AppStartupInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        #if os(iOS) || os(tvOS)
        let tracer = ctx.tracerProvider.get(
            instrumentationName: PulseKitConstants.instrumentationScopeName,
            instrumentationVersion: PulseKitConstants.instrumentationVersion
        )
        VisibleScreenTracker.shared.start(tracer: tracer)
        VisibleScreenTracker.shared.enableAppStartup()
        UIViewControllerSwizzler.swizzle(includeLifecycleMethods: false)
        AppStartupTimer.shared.start(tracer: tracer)
        #endif
    }

    internal func uninstall() {}
}
