/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

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
    
    public mutating func excludeOtlpEndpoints(baseUrl: String) {
        let userShouldInstrument = self.shouldInstrument
        self.shouldInstrument = Self.createOtlpExclusionHandler(
            baseUrl: baseUrl,
            userHandler: userShouldInstrument
        )
    }
    
    private static func createOtlpExclusionHandler(
        baseUrl: String,
        userHandler: ((URLRequest) -> Bool)?
    ) -> ((URLRequest) -> Bool) {
        _ = baseUrl  // Reserved for future host-based exclusion if needed
        return { request in
            guard let url = request.url else { return userHandler?(request) ?? true }
            let urlString = url.absoluteString
            if urlString.contains("/v1/traces") || urlString.contains("/v1/logs") || urlString.contains("/v1/metrics") {
                return false
            }
            
            return userHandler?(request) ?? true
        }
    }
}

extension URLSessionInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }

        let finalShouldInstrument = Self.createOtlpExclusionHandler(
            baseUrl: ctx.endpointBaseUrl,
            userHandler: self.shouldInstrument
        )

        let urlSessionConfig = URLSessionInstrumentationConfiguration(
            shouldInstrument: finalShouldInstrument
        )
        _ = URLSessionInstrumentation(configuration: urlSessionConfig)
    }

    internal func uninstall() {}
}
