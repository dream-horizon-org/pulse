/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// Configuration for Interaction Instrumentation
public struct InteractionInstrumentationConfiguration {
    /// URL provider for fetching interaction configurations from the server
    /// Default: "http://10.0.2.2:8080/v1/interactions/all-active-interactions"
    public var configUrlProvider: () -> String

    /// HTTP headers for the config fetch request (e.g. X-API-KEY)
    public var headers: [String: String]

    /// Custom attribute extractor for adding additional attributes to interaction spans
    /// Takes an Interaction object and returns a dictionary of attributes
    /// Note: Interaction type will be defined in core module
    public var attributeExtractor: ((Any) -> [String: AttributeValue])?

    /// Internal: Use mock fetcher instead of real API (for testing only)
    internal var useMockFetcher: Bool = false

    /// When using the mock fetcher, optional configs to return (defaults to built-in mock data when nil)
    internal var mockConfigs: [InteractionConfig]? = nil

    public init(
        configUrlProvider: @escaping () -> String = {
            "http://10.0.2.2:8080/v1/interactions/all-active-interactions"
        },
        headers: [String: String] = [:],
        attributeExtractor: ((Any) -> [String: AttributeValue])? = nil
    ) {
        self.configUrlProvider = configUrlProvider
        self.headers = headers
        self.attributeExtractor = attributeExtractor
    }
}

