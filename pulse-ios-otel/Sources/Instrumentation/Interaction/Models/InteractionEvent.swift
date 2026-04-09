/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Event definition in an interaction configuration
/// Represents a single event in the sequence that should be matched
public struct InteractionEvent: Codable {
    public let name: String
    public let props: [InteractionAttrsEntry]?
    public let isBlacklisted: Bool

    public init(name: String, props: [InteractionAttrsEntry]? = nil, isBlacklisted: Bool = false) {
        self.name = name
        self.props = props
        self.isBlacklisted = isBlacklisted
    }
}

