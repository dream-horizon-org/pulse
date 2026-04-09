/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Contains the info about generated interaction
public struct Interaction: Equatable {
    public let id: String
    public let name: String
    public let props: [String: Any?]
    
    public init(id: String, name: String, props: [String: Any?] = [:]) {
        self.id = id
        self.name = name
        self.props = props
    }
    
    /// Get events from props
    public var events: [InteractionLocalEvent] {
        guard let events = props[InteractionAttributes.localEvents] as? [InteractionLocalEvent] else {
            return []
        }
        return events
    }
    
    /// Get marker events from props
    public var markerEvents: [InteractionLocalEvent] {
        guard let markers = props[InteractionAttributes.markerEvents] as? [InteractionLocalEvent] else {
            return []
        }
        return markers
    }
    
    /// Check if interaction is errored
    public var isErrored: Bool {
        guard let isError = props[InteractionAttributes.isError] as? Bool else {
            return false
        }
        return isError
    }
    
    /// Nanoseconds from earliest to latest matched event in `events`.
    public var timeSpanInNanos: (start: Int64, end: Int64)? {
        let steps = events
        guard steps.count >= 2, let first = steps.first, let last = steps.last else {
            return nil
        }
        return (first.timeInNano, last.timeInNano)
    }
    
    // Equatable conformance
    public static func == (lhs: Interaction, rhs: Interaction) -> Bool {
        return lhs.id == rhs.id && lhs.name == rhs.name
    }
}

