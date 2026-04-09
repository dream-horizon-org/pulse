/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Mock implementation of InteractionConfigFetcher for testing
/// Returns hardcoded interaction configurations unless `customConfigs` is set
public class InteractionConfigMockFetcher: InteractionConfigFetcher {
    private let customConfigs: [InteractionConfig]?

    public init(customConfigs: [InteractionConfig]? = nil) {
        self.customConfigs = customConfigs
    }

    public func getConfigs() async throws -> [InteractionConfig]? {
        if let customConfigs {
            return customConfigs
        }
        // Simulate network delay
        try await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds

        return [
            InteractionConfig(
                id: 1,
                name: "TestInteractiopn",
                events: [
                    InteractionEvent(name: "event1", props: nil, isBlacklisted: false),
                    InteractionEvent(name: "event2", props: nil, isBlacklisted: false)
                ],
                globalBlacklistedEvents: [],
                uptimeLowerLimitInMs: 100,
                uptimeMidLimitInMs: 500,
                uptimeUpperLimitInMs: 1000,
                thresholdInMs: 20000
            ),
            InteractionConfig(
                id: 2,
                name: "FancodeCrashTesting",
                events: [
                    InteractionEvent(name: "fancode_event1", props: nil, isBlacklisted: false),
                    InteractionEvent(name: "fancode_event2", props: nil, isBlacklisted: false)
                ],
                globalBlacklistedEvents: [],
                uptimeLowerLimitInMs: 100,
                uptimeMidLimitInMs: 500,
                uptimeUpperLimitInMs: 1000,
                thresholdInMs: 20000
            )
        ]
    }
}

