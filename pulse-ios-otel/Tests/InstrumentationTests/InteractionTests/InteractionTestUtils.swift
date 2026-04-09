/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
@testable import PulseKit

/// Test utilities for creating fake interaction configs and events
enum InteractionTestUtils {
    /// Create a fake interaction event
    static func createFakeInteractionEvent(
        name: String,
        props: [InteractionAttrsEntry]? = nil,
        isBlacklisted: Bool = false
    ) -> InteractionEvent {
        return InteractionEvent(name: name, props: props, isBlacklisted: isBlacklisted)
    }
    
    /// Create a fake interaction attributes entry
    static func createFakeInteractionAttrsEntry(
        _ name: String,
        _ value: String,
        operator: String = "EQUALS"
    ) -> InteractionAttrsEntry {
        return InteractionAttrsEntry(name: name, value: value, operator: `operator`)
    }
    
    /// Create a fake interaction config
    static func createFakeInteractionConfig(
        id: Int = 1,
        name: String = "TestInteraction",
        eventSequence: [InteractionEvent],
        globalBlacklistedEvents: [InteractionEvent] = [],
        uptimeLowerLimitInMs: Int64 = 100,
        uptimeMidLimitInMs: Int64 = 500,
        uptimeUpperLimitInMs: Int64 = 1000,
        thresholdInMs: Int64 = 20000
    ) throws -> InteractionConfig {
        // Validation: ensure at least one non-blacklisted event
        struct InteractionConfigError: Error, CustomStringConvertible {
            let message: String
            var description: String { message }
        }
        
        if eventSequence.isEmpty {
            struct EmptySequenceError: Error {}
            throw EmptySequenceError()
        }
        
        let nonBlacklistedCount = eventSequence.filter { !$0.isBlacklisted }.count
        if nonBlacklistedCount == 0 {
            throw InteractionConfigError(message: "event sequence doesn't have any non blacklisted event")
        }
        
        // Validation: first and last events should not be blacklisted
        if let first = eventSequence.first {
            if first.isBlacklisted {
                throw InteractionConfigError(message: "event first event is blacklisted")
            }
        }
        if let last = eventSequence.last {
            if last.isBlacklisted {
                throw InteractionConfigError(message: "event last event is blacklisted")
            }
        }
        
        return InteractionConfig(
            id: id,
            name: name,
            events: eventSequence,
            globalBlacklistedEvents: globalBlacklistedEvents,
            uptimeLowerLimitInMs: uptimeLowerLimitInMs,
            uptimeMidLimitInMs: uptimeMidLimitInMs,
            uptimeUpperLimitInMs: uptimeUpperLimitInMs,
            thresholdInMs: thresholdInMs
        )
    }
}

/// Mock config fetcher for testing
class MockInteractionConfigFetcher: InteractionConfigFetcher {
    private let configs: [InteractionConfig]
    
    init(configs: [InteractionConfig]) {
        self.configs = configs
    }
    
    func getConfigs() async throws -> [InteractionConfig]? {
        // Simulate small delay
        try await Task.sleep(nanoseconds: 10_000_000) // 0.01 seconds
        return configs
    }
}

