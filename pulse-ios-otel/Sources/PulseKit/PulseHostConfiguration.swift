/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Derives Pulse collector and config URLs from the API key.
internal enum PulseHostConfiguration {
    private static let pulseEndpointURL = "https://pulse-otel-collector.pulse-ux.com"
    private static let pulseEndpointURLLocal = "http://127.0.0.1"

    internal static func isApiLocalDev(apiKey: String) -> Bool {
        apiKey.hasPrefix("default-project_")
    }

    internal static func baseUrl(apiKey: String) -> String {
        if isApiLocalDev(apiKey: apiKey) {
            return "\(pulseEndpointURLLocal):4318"
        }
        return pulseEndpointURL
    }

    internal static func activeConfigUrl(apiKey: String, projectId: String) -> String {
        if isApiLocalDev(apiKey: apiKey) {
            return "\(pulseEndpointURLLocal):8080/v1/configs/active/"
        }
        return "\(pulseEndpointURL)/config/projects/\(projectId)/pulse-config.json"
    }

    internal static func interactionConfigUrl(apiKey: String, projectId: String) -> String {
        if isApiLocalDev(apiKey: apiKey) {
            return "\(pulseEndpointURLLocal):8080/v1/interaction-configs/"
        }
        return "\(pulseEndpointURL)/config/projects/\(projectId)/interaction-config.json"
    }
}
