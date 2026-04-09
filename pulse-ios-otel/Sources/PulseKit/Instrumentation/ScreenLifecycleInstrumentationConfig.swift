/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// Configuration for screen lifecycle instrumentation.
/// When enabled, emits Created, Restarted, Stopped, and ViewControllerSession spans
/// on UIViewController lifecycle transitions (analogous to Android's activity instrumentation).
public struct ScreenLifecycleInstrumentationConfig {
    public private(set) var enabled: Bool = true

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }
}

extension ScreenLifecycleInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        #if os(iOS) || os(tvOS)
        let tracer = ctx.tracerProvider.get(
            instrumentationName: PulseKitConstants.instrumentationScopeName,
            instrumentationVersion: PulseKitConstants.instrumentationVersion
        )
        VisibleScreenTracker.shared.start(tracer: tracer)
        VisibleScreenTracker.shared.enableLifecycleSpans()
        UIViewControllerSwizzler.swizzle(includeLifecycleMethods: true)
        #endif
    }

    internal func uninstall() {}
}
