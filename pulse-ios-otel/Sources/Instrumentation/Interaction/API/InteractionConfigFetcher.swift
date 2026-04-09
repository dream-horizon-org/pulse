/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Protocol for fetching interaction configurations
/// Allows for different implementations (REST API, local file, etc.)
public protocol InteractionConfigFetcher {
    /// Fetches the list of interaction configurations from the server
    /// Returns nil in case of error
    func getConfigs() async throws -> [InteractionConfig]?
}

