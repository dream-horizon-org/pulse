/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Interaction configuration from the server
/// Defines a sequence of events to track and performance thresholds
public struct InteractionConfig: Codable {
    public let id: Int
    public let name: String
    public let events: [InteractionEvent]
    public let globalBlacklistedEvents: [InteractionEvent]
    public let uptimeLowerLimitInMs: Int64
    public let uptimeMidLimitInMs: Int64
    public let uptimeUpperLimitInMs: Int64
    public let thresholdInMs: Int64

    public var eventsSize: Int {
        events.count
    }

    public var firstEvent: InteractionEvent {
        events.first!
    }

    public init(
        id: Int,
        name: String,
        events: [InteractionEvent],
        globalBlacklistedEvents: [InteractionEvent] = [],
        uptimeLowerLimitInMs: Int64,
        uptimeMidLimitInMs: Int64,
        uptimeUpperLimitInMs: Int64,
        thresholdInMs: Int64
    ) {
        self.id = id
        self.name = name
        self.events = events
        self.globalBlacklistedEvents = globalBlacklistedEvents
        self.uptimeLowerLimitInMs = uptimeLowerLimitInMs
        self.uptimeMidLimitInMs = uptimeMidLimitInMs
        self.uptimeUpperLimitInMs = uptimeUpperLimitInMs
        self.thresholdInMs = thresholdInMs

        // Validation
        #if DEBUG
        assert(events.count { !$0.isBlacklisted } > 0, "event sequence doesn't have any non blacklisted event")
        assert(!events.first!.isBlacklisted, "event first event is blacklisted")
        assert(!events.last!.isBlacklisted, "event last event is blacklisted")
        #endif
    }
}

