/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import URLSessionInstrumentation

public struct URLSessionInstrumentationConfig {
    public private(set) var enabled: Bool = true
    public private(set) var shouldInstrument: ((URLRequest) -> Bool)?

    public init(enabled: Bool = true, shouldInstrument: ((URLRequest) -> Bool)? = nil) {
        self.enabled = enabled
        self.shouldInstrument = shouldInstrument
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }

    public mutating func setShouldInstrument(_ handler: @escaping (URLRequest) -> Bool) {
        self.shouldInstrument = handler
    }
}

extension URLSessionInstrumentationConfig: InstrumentationInitializer {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }

        let urlSessionConfig = URLSessionInstrumentationConfiguration(
            shouldInstrument: self.shouldInstrument
        )
        _ = URLSessionInstrumentation(configuration: urlSessionConfig)
    }
}
