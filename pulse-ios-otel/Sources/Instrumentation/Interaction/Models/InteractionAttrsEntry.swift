/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Property attribute entry for matching event properties
/// Used to filter events based on property values with operators
public struct InteractionAttrsEntry: Codable {
    public let name: String
    public let value: String
    public let `operator`: String

    public init(name: String, value: String, operator: String) {
        self.name = name
        self.value = value
        self.operator = `operator`
    }

    private enum CodingKeys: String, CodingKey {
        case name
        case value
        case `operator` = "operator"
    }
}

