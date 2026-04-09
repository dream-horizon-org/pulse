/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Wrapper for API responses that follow the pattern:
/// {
///   "data": [...],
///   "error": null
/// }
public struct ApiResponse<T: Codable>: Codable {
    public let data: T
    public let error: String?

    public init(data: T, error: String? = nil) {
        self.data = data
        self.error = error
    }
}

