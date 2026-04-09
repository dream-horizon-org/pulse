/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
#if os(iOS) || os(tvOS)
import UIKit
#endif

public struct SessionReplayInstrumentationConfig {
    public private(set) var enabled: Bool = false
    public private(set) var config: SessionReplayConfig = SessionReplayConfig()

    /// Set from `Pulse.initialize` before `installInstrumentations`. Defaults match legacy “always allowed” if unset.
    internal private(set) var pulseIsSessionReplayCaptureAllowed: () -> Bool = { true }
    internal private(set) var pulseSessionReplayStartActiveAtInstall: Bool = true

    public init(enabled: Bool = false, config: SessionReplayConfig = SessionReplayConfig()) {
        self.enabled = enabled
        self.config = config
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }

    public mutating func configure(_ configure: (inout SessionReplayConfig) -> Void) {
        configure(&self.config)
    }

    internal mutating func attachPulseSessionReplayConsent(
        isCaptureAllowed: @escaping () -> Bool,
        startActiveAtInstall: Bool
    ) {
        pulseIsSessionReplayCaptureAllowed = isCaptureAllowed
        pulseSessionReplayStartActiveAtInstall = startActiveAtInstall
    }
}

extension SessionReplayInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        
        let replayEndpoint = self.config.replayEndpointBaseUrl ?? ctx.endpointBaseUrl
        guard let exporter = SessionReplayExporter(
            endpointBaseUrl: replayEndpoint,
            headers: ctx.endpointHeaders,
            projectId: ctx.projectId,
            userIdProvider: ctx.userIdProvider
        ) else {
            return
        }

        let instrumentation = SessionReplayInstrumentation(
            config: self.config,
            exporter: exporter,
            isSessionReplayCaptureAllowed: self.pulseIsSessionReplayCaptureAllowed
        )
        instrumentation.install(shouldStartActive: self.pulseSessionReplayStartActiveAtInstall)
    }
    internal func uninstall() {
        SessionReplayInstrumentation.getInstance()?.uninstall()
    }
}
