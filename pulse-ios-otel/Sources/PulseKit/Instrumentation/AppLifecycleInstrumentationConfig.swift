/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

/// Configuration for app lifecycle instrumentation.
/// When enabled, emits `device.app.lifecycle` log events on app state transitions.
public struct AppLifecycleInstrumentationConfig {
    public private(set) var enabled: Bool = true

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }
}

extension AppLifecycleInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        #if os(iOS) || os(tvOS)
        let logger = ctx.loggerProvider.get(
            instrumentationScopeName: PulseKitConstants.instrumentationScopeName
        )
        AppStateWatcher.shared.start()
        let instrumentation = AppLifecycleInstrumentation(logger: logger)
        AppStateWatcher.shared.registerListener(instrumentation)
        AppLifecycleInstrumentationConfig._activeInstrumentation = instrumentation
        #endif
    }

    internal func uninstall() {
        guard self.enabled else { return }
        #if os(iOS) || os(tvOS)
        if let inst = AppLifecycleInstrumentationConfig._activeInstrumentation {
            inst.uninstall()
            AppLifecycleInstrumentationConfig._activeInstrumentation = nil
        }
        #endif
    }

    /// Holds a strong reference so the listener isn't deallocated.
    private static var _activeInstrumentation: AppLifecycleInstrumentation?
}
