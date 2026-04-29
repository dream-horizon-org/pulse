/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
#if os(iOS) || os(tvOS)
import UIKit
#endif

public struct SessionReplayInstrumentationConfig {
    public private(set) var config: SessionReplayConfig = SessionReplayConfig()
    internal private(set) var enabled: Bool = false

    /// Set from `Pulse.initialize` before `installInstrumentations`. Defaults match legacy “always allowed” if unset.
    internal private(set) var pulseIsSessionReplayCaptureAllowed: () -> Bool = { true }
    internal private(set) var pulseSessionReplayStartActiveAtInstall: Bool = true

    public init(config: SessionReplayConfig = SessionReplayConfig()) {
        self.config = config
    }

    internal mutating func enabled(_ value: Bool) {
        self.enabled = value
    }

    /// Add a view class (fully-qualified name) to always mask. Subclasses are also masked.
    public mutating func addMaskViewClass(_ className: String) {
        config.addMaskViewClass(className)
    }

    /// Add a view class (fully-qualified name) to never mask by global config.
    public mutating func addUnmaskViewClass(_ className: String) {
        config.addUnmaskViewClass(className)
    }

    internal mutating func attachPulseSessionReplayConsent(
        isCaptureAllowed: @escaping () -> Bool,
        startActiveAtInstall: Bool
    ) {
        pulseIsSessionReplayCaptureAllowed = isCaptureAllowed
        pulseSessionReplayStartActiveAtInstall = startActiveAtInstall
    }

    /// SDK-internal: applies resolved backend config after merge. Not for public use.
    internal mutating func internalSetRuntimeConfig(_ config: SessionReplayConfig) {
        self.config = config
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
