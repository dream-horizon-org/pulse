/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Local event tracked by the SDK
/// Represents an event that occurred in the app with its properties and timestamp
public struct InteractionLocalEvent {
    public let name: String
    public let timeInNano: Int64
    public let props: [String: String]?

    public init(name: String, timeInNano: Int64, props: [String: String]? = nil) {
        self.name = name
        self.timeInNano = timeInNano
        self.props = props
    }
}

