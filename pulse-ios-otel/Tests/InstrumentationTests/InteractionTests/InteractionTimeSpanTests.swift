/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import PulseKit

final class InteractionTimeSpanTests: XCTestCase {
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

        guard let span = interaction.timeSpanInNanos else {
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
        let span = interaction.timeSpanInNanos
        XCTAssertEqual(span?.start, t0)
        XCTAssertEqual(span?.end, t1)
    }

    func testTimeSpanInNanos_lessThanTwoEvents_returnsNil() {
        let one = Interaction(
            id: "id",
            name: "n",
            props: [InteractionAttributes.localEvents: [InteractionLocalEvent(name: "only", timeInNano: 1)]]
        )
        XCTAssertNil(one.timeSpanInNanos)

        let none = Interaction(id: "id", name: "n", props: [InteractionAttributes.localEvents: []])
        XCTAssertNil(none.timeSpanInNanos)
    }
}
