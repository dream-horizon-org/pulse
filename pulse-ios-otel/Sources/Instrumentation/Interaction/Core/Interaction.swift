/*
 * Copyright The Pulse Authors
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

    public var errorTypeCode: String? {
        props[InteractionAttributes.errorType] as? String
    }

    public var errorMessage: String? {
        props[InteractionAttributes.errorMessage] as? String
    }

    public func timeSpanInNanos(thresholdMs: Int64) -> (start: Int64, end: Int64)? {
        let steps = events
        if steps.isEmpty {
            return nil
        }
        if let errorTypeParsed = InteractionErrorType.fromCode(errorTypeCode) {
            guard let firstNs = steps.first?.timeInNano, let lastNs = steps.last?.timeInNano else {
                return nil
            }
            let thresholdNs = thresholdMs * 1_000_000
            let end: Int64
            if errorTypeParsed == .timeout {
                end = firstNs + thresholdNs + (lastNs - firstNs)
            } else {
                end = lastNs
            }
            return (firstNs, end)
        }
        if steps.count == 1 {
            let t0 = steps[0].timeInNano
            return (t0, t0 + thresholdMs * 1_000_000)
        }
        guard let firstNs = steps.first?.timeInNano, let lastNs = steps.last?.timeInNano else {
            return nil
        }
        return (firstNs, lastNs)
    }

    // Equatable conformance
    public static func == (lhs: Interaction, rhs: Interaction) -> Bool {
        return lhs.id == rhs.id && lhs.name == rhs.name
    }
}
