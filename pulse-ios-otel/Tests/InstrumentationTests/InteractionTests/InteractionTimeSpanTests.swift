/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import PulseKit

final class InteractionTimeSpanTests: XCTestCase {
    private let thresholdMs: Int64 = 20_000

    func testTimeSpanInNanos_threeEvents_usesFirstAndLast_notFirstAndSecond() {
        let t0: Int64 = 1
        let t1 = t0 + 1
        let t2 = t0 + 2

        let events = [
            InteractionLocalEvent(name: "a", timeInNano: t0),
            InteractionLocalEvent(name: "b", timeInNano: t1),
            InteractionLocalEvent(name: "c", timeInNano: t2),
        ]
        let interaction = Interaction(
            id: "test-id",
            name: "test",
            props: [InteractionAttributes.localEvents: events]
        )

        guard let span = interaction.timeSpanInNanos(thresholdMs: thresholdMs) else {
            XCTFail("expected timeSpanInNanos for 3 events")
            return
        }
        XCTAssertEqual(span.start, t0)
        XCTAssertEqual(span.end, t2, "Wrong end: buggy impl uses second event (\(t1)) instead of last (\(t2))")
    }

    func testTimeSpanInNanos_twoEvents_matchesFirstAndSecond() {
        let t0: Int64 = 1
        let t1 = t0 + 1
        let events = [
            InteractionLocalEvent(name: "a", timeInNano: t0),
            InteractionLocalEvent(name: "b", timeInNano: t1),
        ]
        let interaction = Interaction(
            id: "id",
            name: "n",
            props: [InteractionAttributes.localEvents: events]
        )
        let span = interaction.timeSpanInNanos(thresholdMs: thresholdMs)
        XCTAssertEqual(span?.start, t0)
        XCTAssertEqual(span?.end, t1)
    }

    func testTimeSpanInNanos_singleEvent_extendsEndByThreshold() {
        let t0: Int64 = 1
        let t = Int64(100)
        let events = [InteractionLocalEvent(name: "solo", timeInNano: t0)]
        let interaction = Interaction(
            id: "id",
            name: "n",
            props: [InteractionAttributes.localEvents: events]
        )
        let span = interaction.timeSpanInNanos(thresholdMs: t)
        XCTAssertEqual(span?.start, t0)
        XCTAssertEqual(span?.end, t0 + t * 1_000_000)
    }

    func testTimeSpanInNanos_timeout_usesThresholdPlusSpreadBetweenFirstAndLast() {
        let threshold: Int64 = 1
        let t0: Int64 = 1
        let t1 = t0 + 1
        let events = [
            InteractionLocalEvent(name: "a", timeInNano: t0),
            InteractionLocalEvent(name: "b", timeInNano: t1),
        ]
        let interaction = Interaction(
            id: "id-timeout",
            name: "n",
            props: [
                InteractionAttributes.localEvents: events,
                InteractionAttributes.isError: true,
                InteractionAttributes.errorType: "timeout",
                InteractionAttributes.errorMessage: "Timed out while waiting for event \"b\".",
            ]
        )
        let thresholdNs = threshold * 1_000_000
        let expectedEnd = t0 + thresholdNs + (t1 - t0)
        let span = interaction.timeSpanInNanos(thresholdMs: threshold)
        XCTAssertEqual(span?.start, t0)
        XCTAssertEqual(span?.end, expectedEnd)
        XCTAssertEqual(interaction.errorTypeCode, "timeout")
    }

    func testTimeSpanInNanos_sequenceViolation_usesFirstToLast() {
        let t0: Int64 = 10
        let t1: Int64 = 50
        let events = [
            InteractionLocalEvent(name: "event1", timeInNano: t0),
            InteractionLocalEvent(name: "event3", timeInNano: t1),
        ]
        let interaction = Interaction(
            id: "id-seq",
            name: "n",
            props: [
                InteractionAttributes.localEvents: events,
                InteractionAttributes.isError: true,
                InteractionAttributes.errorType: "sequence_violation",
                InteractionAttributes.errorMessage: "Expected event \"event2\", received \"event3\".",
            ]
        )
        let span = interaction.timeSpanInNanos(thresholdMs: thresholdMs)
        XCTAssertEqual(span?.start, t0)
        XCTAssertEqual(span?.end, t1)
    }

    func testTimeSpanInNanos_emptyEvents_returnsNil() {
        let interaction = Interaction(
            id: "id",
            name: "n",
            props: [InteractionAttributes.localEvents: [] as [InteractionLocalEvent]]
        )
        XCTAssertNil(interaction.timeSpanInNanos(thresholdMs: thresholdMs))
    }
}
