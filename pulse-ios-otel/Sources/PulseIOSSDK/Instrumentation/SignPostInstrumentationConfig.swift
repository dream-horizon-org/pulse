/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import SignPostIntegration

public struct SignPostInstrumentationConfig {
    public private(set) var enabled: Bool = true

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }
}

extension SignPostInstrumentationConfig: InstrumentationInitializer {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }

        if let tracerProviderSdk = ctx.tracerProvider as? TracerProviderSdk {
            if #available(iOS 15.0, macOS 12.0, *) {
                tracerProviderSdk.addSpanProcessor(OSSignposterIntegration())
            } else {
                tracerProviderSdk.addSpanProcessor(SignPostIntegration())
            }
        }
    }
}
