/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

public struct CrashInstrumentationConfig {
    public private(set) var enabled: Bool = true

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }
}

extension CrashInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        CrashInstrumentation(
            loggerProvider: ctx.loggerProvider,
            flushLogProcessor: ctx.flushLogProcessor
        ).install()
    }

    internal func uninstall() {
        guard self.enabled else { return }
        CrashInstrumentation.uninstall()
    }
}
