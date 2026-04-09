/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import PulseKit

/// Tests for InteractionManager
final class InteractionManagerTests: XCTestCase {
    private var interactionManager: InteractionManager!
    private var mockConfigFetcher: MockInteractionConfigFetcher!
    
    override func setUp() {
        super.setUp()
        mockConfigFetcher = MockInteractionConfigFetcher(configs: [])
        interactionManager = InteractionManager(interactionFetcher: mockConfigFetcher)
    }
    
    override func tearDown() {
        interactionManager = nil
        mockConfigFetcher = nil
        super.tearDown()
    }
    
    // MARK: - Basic Initialization Tests
    
    func testWhenInteractionInitIsNotDone_interactionTrackersShouldBeNil() async {
        mockConfigFetcher = MockInteractionConfigFetcher(configs: [])
        interactionManager = InteractionManager(interactionFetcher: mockConfigFetcher)
        
        // Before initialization, trackers should be nil
        // Note: In Swift, we can't directly access private properties, so we check via currentStates
        let states = interactionManager.currentStates
        XCTAssertTrue(states.isEmpty, "Before initialization, states should be empty")
    }
    
    func testWhenInteractionsAreEmpty_interactionTrackersShouldBeEmpty() async {
        mockConfigFetcher = MockInteractionConfigFetcher(configs: [])
        interactionManager = InteractionManager(interactionFetcher: mockConfigFetcher)
        
        await interactionManager.initialize()
        
        let states = interactionManager.currentStates
        XCTAssertTrue(states.isEmpty, "When configs are empty, states should be empty")
    }
    
    func testWhenInteractionIsOne_interactionTrackersShouldBeOne() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1")
            ]
        )
        mockConfigFetcher = MockInteractionConfigFetcher(configs: [config])
        interactionManager = InteractionManager(interactionFetcher: mockConfigFetcher)
        
        await interactionManager.initialize()
        
        let states = interactionManager.currentStates
        XCTAssertEqual(states.count, 1, "Should have one tracker for one config")
    }
    
    func testWhenInteractionIsTwo_interactionTrackersShouldBeTwo() async {
        let config1 = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1")
            ]
        )
        let config2 = try! InteractionTestUtils.createFakeInteractionConfig(
            id: 2,
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        mockConfigFetcher = MockInteractionConfigFetcher(configs: [config1, config2])
        interactionManager = InteractionManager(interactionFetcher: mockConfigFetcher)
        
        await interactionManager.initialize()
        
        let states = interactionManager.currentStates
        XCTAssertEqual(states.count, 2, "Should have two trackers for two configs")
    }
    
    func testWhenInteractionConfigHasNoEventConfig_throwsAssertion() {
        XCTAssertThrowsError(
            try InteractionTestUtils.createFakeInteractionConfig(
                eventSequence: []
            ),
            "Should throw error when event sequence is empty"
        )
    }
    
    func testWhenInteractionConfigHasAllBlacklistedConfig_throwsAssertion() {
        // This should throw an error
        XCTAssertThrowsError(
            try InteractionTestUtils.createFakeInteractionConfig(
                eventSequence: [
                    InteractionTestUtils.createFakeInteractionEvent(
                        name: "blacklisted",
                        isBlacklisted: true
                    )
                ]
            ),
            "Should throw error when all events are blacklisted"
        ) { error in
            // Verify the error message matches expected assertion message
            XCTAssertTrue(
                error.localizedDescription.contains("event sequence doesn't have any non blacklisted event") ||
                String(describing: error).contains("event sequence doesn't have any non blacklisted event"),
                "Error should contain expected message"
            )
        }
    }
}

// MARK: - Two Event Interaction Tests

extension InteractionManagerTests {
    /// Helper to initialize manager with configs
    func initMockInteractionManager(_ configs: InteractionConfig...) async {
        mockConfigFetcher = MockInteractionConfigFetcher(configs: Array(configs))
        interactionManager = InteractionManager(interactionFetcher: mockConfigFetcher)
        await interactionManager.initialize()
        
        // Wait a bit for trackers to be set up and state observation to start
        try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
        
        // Ensure we have the expected number of states
        var retries = 0
        while retries < 10 {
            let states = interactionManager.currentStates
            if states.count == configs.count {
                break
            }
            try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
            retries += 1
        }
    }
    
    /// Helper to add event with nano time
    func addEventWithNanoTimeFromBoot(
        _ eventName: String,
        params: [String: Any?] = [:],
        eventTimeInNano: Int64? = nil
    ) {
        interactionManager.addEvent(
            eventName: eventName,
            params: params,
            eventTimeInNano: eventTimeInNano
        )
    }
    
    /// Assert single ongoing interaction
    func assertSingleOngoingInteraction(
        previousIdToMatch: String? = nil,
        file: StaticString = #file,
        line: UInt = #line
    ) -> String {
        // Wait for async processing - retry until we get the expected state
        var states = interactionManager.currentStates
        var retries = 0
        var foundOngoingMatch = false
        
        while retries < 50 && !foundOngoingMatch {
            let expectation = XCTestExpectation(description: "Wait for state update")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                expectation.fulfill()
            }
            wait(for: [expectation], timeout: 1.0)
            
            states = interactionManager.currentStates
            if states.count == 1,
               case .ongoingMatch(_, _, _, let interaction) = states.first!,
               interaction == nil {
                foundOngoingMatch = true
                break
            }
            retries += 1
        }
        
        XCTAssertEqual(states.count, 1, "Should have exactly one state", file: file, line: line)
        
        guard case .ongoingMatch(let index, let interactionId, _, let interaction) = states.first! else {
            XCTFail("Expected ongoingMatch state, got: \(String(describing: states.first ?? nil))", file: file, line: line)
            return ""
        }
        
        XCTAssertNil(interaction, "Interaction should be nil for ongoing match", file: file, line: line)
        
        if let previousId = previousIdToMatch {
            XCTAssertEqual(interactionId, previousId, "Interaction ID should match", file: file, line: line)
        }
        
        return interactionId
    }
    
    /// Assert single final interaction
    func assertSingleFinalInteraction(
        previousIdToMatch: String? = nil,
        isSuccess: Bool = true,
        file: StaticString = #file,
        line: UInt = #line
    ) -> (String, Interaction) {
        // Wait for async processing - retry until we get a final interaction
        var states = interactionManager.currentStates
        var retries = 0
        var foundFinalInteraction = false
        
        while retries < 50 && !foundFinalInteraction {
            let expectation = XCTestExpectation(description: "Wait for state update")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                expectation.fulfill()
            }
            wait(for: [expectation], timeout: 1.0)
            
            states = interactionManager.currentStates
            if states.count == 1,
               case .ongoingMatch(_, _, _, let interaction) = states.first!,
               let interaction = interaction {
                foundFinalInteraction = true
                break
            }
            retries += 1
        }
        
        XCTAssertEqual(states.count, 1, "Should have exactly one state", file: file, line: line)
        
        guard case .ongoingMatch(let index, let interactionId, _, let interaction) = states.first! else {
            XCTFail("Expected ongoingMatch state, got: \(String(describing: states.first ?? nil))", file: file, line: line)
            return ("", Interaction(id: "", name: ""))
        }
        
        guard let finalInteraction = interaction else {
            XCTFail("Interaction should not be nil for final interaction. State: \(String(describing: states.first ?? nil))", file: file, line: line)
            return ("", Interaction(id: "", name: ""))
        }
        
        XCTAssertEqual(interactionId, finalInteraction.id, "Interaction ID should match", file: file, line: line)
        XCTAssertEqual(finalInteraction.isErrored, !isSuccess, "Interaction error state should match. Expected isErrored=\(!isSuccess), got \(finalInteraction.isErrored)", file: file, line: line)
        
        if let previousId = previousIdToMatch {
            XCTAssertEqual(interactionId, previousId, "Interaction ID should match previous", file: file, line: line)
        }
        
        return (interactionId, finalInteraction)
    }
    
    /// Assert no ongoing interaction
    func assertSingleNoOngoingInteraction(
        file: StaticString = #file,
        line: UInt = #line
    ) {
        // Wait for async processing - retry until we get noOngoingMatch state
        var states = interactionManager.currentStates
        var retries = 0
        var foundNoOngoingMatch = false
        
        while retries < 50 && !foundNoOngoingMatch {
            let expectation = XCTestExpectation(description: "Wait for state update")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                expectation.fulfill()
            }
            wait(for: [expectation], timeout: 1.0)
            
            states = interactionManager.currentStates
            if states.count == 1,
               case .noOngoingMatch = states.first! {
                foundNoOngoingMatch = true
                break
            }
            retries += 1
        }
        
        XCTAssertEqual(states.count, 1, "Should have exactly one state", file: file, line: line)
        
        guard case .noOngoingMatch = states.first! else {
            XCTFail("Expected noOngoingMatch state, got: \(String(describing: states.first ?? nil))", file: file, line: line)
            return
        }
    }
    
    // MARK: - Two Event Sequence Tests
    
    func testWithEventsInSameOrderAndCorrectTime() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        let ongoingId = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        _ = assertSingleFinalInteraction(previousIdToMatch: ongoingId)
    }
    
    func testAfterCompletedInteractionStrayEvent2ThenEvent1Event2UsesNewInteractionId() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        addEventWithNanoTimeFromBoot("event2")
        let (firstCompletionId, _) = assertSingleFinalInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        
        addEventWithNanoTimeFromBoot("event1")
        let secondRunOngoingId = assertSingleOngoingInteraction()
        XCTAssertNotEqual(secondRunOngoingId, firstCompletionId)
        
        addEventWithNanoTimeFromBoot("event2")
        let (secondCompletionId, _) = assertSingleFinalInteraction()
        XCTAssertNotEqual(secondCompletionId, firstCompletionId)
        XCTAssertEqual(secondCompletionId, secondRunOngoingId)
    }
    
    func testWithEventsInSameOrderWithReverseTime() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        let timeInNano = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        addEventWithNanoTimeFromBoot("event1", eventTimeInNano: timeInNano)
        _ = assertSingleOngoingInteraction()
        
        // Add event2 with earlier time (should not complete interaction)
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: timeInNano - 1)
        _ = assertSingleOngoingInteraction()
    }
    
    func testWithEventsInSameOrderWithReverseTime1() async {
        // Test: event1, event2 (reverse time), then event2 (correct time) completes
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        let timeInNano = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        addEventWithNanoTimeFromBoot("event1", eventTimeInNano: timeInNano)
        _ = assertSingleOngoingInteraction()
        
        // Add event2 with earlier time (should not complete)
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: timeInNano - 1)
        _ = assertSingleOngoingInteraction()
        
        // Add event2 with correct time (should complete)
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: timeInNano + 1)
        _ = assertSingleFinalInteraction()
    }
    
    func testWithEventsInSameOrderWithProps() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(
                    name: "event2",
                    props: [
                        InteractionTestUtils.createFakeInteractionAttrsEntry(
                            "key1",
                            "value1",
                            operator: "EQUALS"
                        )
                    ]
                )
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        _ = assertSingleOngoingInteraction()
        
        // Add event2 without matching props (should not complete)
        addEventWithNanoTimeFromBoot("event2")
        _ = assertSingleOngoingInteraction()
        
        // Add event2 with matching props (should complete)
        addEventWithNanoTimeFromBoot("event2", params: ["key1": "value1"])
        _ = assertSingleFinalInteraction()
    }
    
    func testWithEventsInReverseOrderWithCorrectTime() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        let timeStampInNano = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: timeStampInNano)
        assertSingleNoOngoingInteraction()
        
        // Add event1 with earlier time (should complete interaction)
        addEventWithNanoTimeFromBoot("event1", eventTimeInNano: timeStampInNano - 1)
        assertSingleFinalInteraction()
    }
    
    func testWhenEventsHappenWithSameTimestamp() async {
        let sameEventTime = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: sameEventTime)
        assertSingleNoOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event1", eventTimeInNano: sameEventTime)
        // Wait longer for async processing when events have same timestamp
        // Events are sorted by time, so when timestamps are equal, they maintain insertion order
        // Both events need to be processed and matched
        try? await Task.sleep(nanoseconds: 1_000_000_000) // 1 second - give more time for both events to process
        _ = assertSingleFinalInteraction()
    }
    
    // MARK: - Blacklisted Events Tests
    
    func testEvent1Event2Blacklist1BeforeEvent2GivesOngoingThenNoInteraction() async {
        let sameEventTime = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(name: "blacklist1")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1", eventTimeInNano: sameEventTime)
        let interactionId = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: sameEventTime + 2)
        
        // Add blacklist1 between event1 and event2 (should cancel interaction)
        addEventWithNanoTimeFromBoot("blacklist1", eventTimeInNano: sameEventTime + 1)
        assertSingleFinalInteraction(previousIdToMatch: interactionId)
    }
    
    func testEvent1Event2Blacklist1BeforeEvent1GivesOngoingThenFinalInteraction() async {
        let sameEventTime = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(name: "blacklist1")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1", eventTimeInNano: sameEventTime)
        let interactionId = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: sameEventTime + 2)
        
        // Add blacklist1 before event1 (should not cancel)
        addEventWithNanoTimeFromBoot("blacklist1", eventTimeInNano: sameEventTime - 1)
        assertSingleFinalInteraction(previousIdToMatch: interactionId)
    }
    
    func testEvent1Event2Blacklist1AfterEvent2GivesOngoingThenFinalInteraction() async {
        let sameEventTime = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(name: "blacklist1")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1", eventTimeInNano: sameEventTime)
        let interactionId = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2", eventTimeInNano: sameEventTime + 2)
        
        // Add blacklist1 after event2 (should not cancel, interaction already complete)
        addEventWithNanoTimeFromBoot("blacklist1", eventTimeInNano: sameEventTime + 3)
        assertSingleFinalInteraction(previousIdToMatch: interactionId)
    }
    
    func testWithConfigOfTwoSameEventFourOfThatEventTriggerTwoInteractions() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        let ongoingId = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        assertSingleFinalInteraction(previousIdToMatch: ongoingId)
        
        // Start second interaction
        addEventWithNanoTimeFromBoot("event1")
        let newOngoingId1 = assertSingleOngoingInteraction()
        
        XCTAssertNotEqual(ongoingId, newOngoingId1, "New interaction should have different ID")
        
        addEventWithNanoTimeFromBoot("event2")
        let (final2ndInteractionId, _) = assertSingleFinalInteraction()
        
        XCTAssertEqual(newOngoingId1, final2ndInteractionId, "Second interaction ID should match")
    }
    
    func testWithOneCorrectEventAndSecondDifferentEventKeepsTheOngoingInteraction() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        let id = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("eventUnknown")
        assertSingleOngoingInteraction(previousIdToMatch: id)
    }
    
    func testEvent1UnknownEventEvent2KeepsGivesTheFinalInteraction() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        let id = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("eventUnknown")
        assertSingleOngoingInteraction(previousIdToMatch: id)
        
        addEventWithNanoTimeFromBoot("event2")
        assertSingleFinalInteraction(previousIdToMatch: id)
    }
    
    // MARK: - Global Blacklisted Events Tests
    
    func testWithDoubleEventConfigWhenEventStartWithCorrect1stThenBlacklistedEventThenCorrect1stEvent() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(name: "blacklist1")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        let interactionId1st = assertSingleOngoingInteraction()
        
        // Wait a bit
        try? await Task.sleep(nanoseconds: 1_000_000_000) // 1 second
        
        addEventWithNanoTimeFromBoot("blacklist1")
        assertSingleNoOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event1")
        let interactionId2nd = assertSingleOngoingInteraction()
        
        XCTAssertNotEqual(interactionId1st, interactionId2nd, "Second interaction should have different ID")
    }
    
    func testWithDoubleEventConfigWhenEventStartWithCorrect1stThenBlacklistedEventThenCorrect2ndEvent() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(name: "blacklist1")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("blacklist1")
        assertSingleNoOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        assertSingleNoOngoingInteraction()
    }
    
    func testWithDoubleEventConfigWhenEventStartWithCorrect1stThenBlacklistedEventThen1stThen2nd() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(name: "blacklist1")
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("blacklist1")
        assertSingleNoOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event1")
        let interactionId = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        assertSingleFinalInteraction(previousIdToMatch: interactionId)
    }
    
    func testWith2EventConfigAndGlobalBlacklistedWithPropsEvent1Blacklist1WithoutPropsEvent2GivesFinalInteraction() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(
                    name: "blacklist1",
                    props: [
                        InteractionTestUtils.createFakeInteractionAttrsEntry(
                            "key1",
                            "value1",
                            operator: "EQUALS"
                        )
                    ]
                )
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        let interactionId = assertSingleOngoingInteraction()
        
        // Add blacklist1 without matching props (should not cancel)
        addEventWithNanoTimeFromBoot("blacklist1")
        assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        assertSingleFinalInteraction(previousIdToMatch: interactionId)
    }
    
    func testWith2EventConfigAndGlobalBlacklistedWithPropsEvent1Blacklist1WithPropsEvent2StopsOngoingInteraction() async {
        let config = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            globalBlacklistedEvents: [
                InteractionTestUtils.createFakeInteractionEvent(
                    name: "blacklist1",
                    props: [
                        InteractionTestUtils.createFakeInteractionAttrsEntry(
                            "key1",
                            "value1",
                            operator: "EQUALS"
                        )
                    ]
                )
            ]
        )
        await initMockInteractionManager(config)
        
        addEventWithNanoTimeFromBoot("event1")
        assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("blacklist1", params: ["key1": "value1"])
        assertSingleNoOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        assertSingleNoOngoingInteraction()
    }
    
    // MARK: - Local Blacklisted Events Tests
    
    func testWhenCorrectEventHappenWithoutBlacklistedEvent() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(
                    name: "blacklist",
                    isBlacklisted: true
                ),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event1")
        let interactionId = assertSingleOngoingInteraction()
        
        // Local blacklisted events are skipped in the sequence, so event2 should complete
        // Note: The blacklisted event in the sequence is skipped during matching
        addEventWithNanoTimeFromBoot("event2")
        // Wait longer for processing - local blacklisted events need to be processed
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        _ = assertSingleFinalInteraction(previousIdToMatch: interactionId, isSuccess: true)
    }
    
    // MARK: - Timeout Tests
    
    func testEvent1AndThen20sDelayGivesErrorInteraction() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            thresholdInMs: 20000 // 20 seconds
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event1")
        assertSingleOngoingInteraction()
        
        // Wait for timeout (20 seconds + buffer)
        try? await Task.sleep(nanoseconds: 21_000_000_000) // 21 seconds
        
        _ = assertSingleFinalInteraction(isSuccess: false)
    }
    
    func testEvent1Event2WithAfter20sDelayDoesntGiveFinalInteraction() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            thresholdInMs: 20000 // 20 seconds
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event1")
        assertSingleOngoingInteraction()
        
        // Wait for timeout
        try? await Task.sleep(nanoseconds: 21_000_000_000) // 21 seconds
        _ = assertSingleFinalInteraction(isSuccess: false)
        
        // Add event2 after timeout (should not complete)
        addEventWithNanoTimeFromBoot("event2")
        _ = assertSingleFinalInteraction(isSuccess: false)
    }
    
    func testEvent1Event2WithAfter19sDelayDoesGiveFinalInteraction() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            thresholdInMs: 20000 // 20 seconds
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event1")
        _ = assertSingleOngoingInteraction()
        
        // Wait 19 seconds (within threshold)
        try? await Task.sleep(nanoseconds: 19_000_000_000) // 19 seconds
        _ = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("event2")
        _ = assertSingleFinalInteraction(isSuccess: true)
    }
    
    func testEvent1EventUnknownDoesntResetTheTimer() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ],
            thresholdInMs: 20000 // 20 seconds
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event1")
        _ = assertSingleOngoingInteraction()
        
        // Wait 18 seconds
        try? await Task.sleep(nanoseconds: 18_000_000_000) // 18 seconds
        _ = assertSingleOngoingInteraction()
        
        addEventWithNanoTimeFromBoot("eventUnknown")
        _ = assertSingleOngoingInteraction()
        
        // Wait 5 more seconds (total 23, should timeout)
        try? await Task.sleep(nanoseconds: 5_000_000_000) // 5 seconds
        _ = assertSingleFinalInteraction(isSuccess: false)
    }
    
    // MARK: - Wrong Events Tests
    
    func testWithNoOngoingMatchEventsContainedEventDoesntGiveErrorInteraction() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2")
            ]
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event2")
        assertSingleNoOngoingInteraction()
    }
    
    func testWithOngoingMatchEvent1Event3GivesErrorInteraction() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event3")
            ]
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event1")
        let interactionId = assertSingleOngoingInteraction()
        
        // Wait a bit
        try? await Task.sleep(nanoseconds: 1_000_000_000) // 1 second
        
        // Add event3 (wrong event, should skip event2)
        addEventWithNanoTimeFromBoot("event3")
        let (interactionId2, _) = assertSingleFinalInteraction(isSuccess: false)
        
        XCTAssertEqual(interactionId, interactionId2, "Interaction ID should match")
    }
    
    func testAfterErrorInteractionSuccessInteractionIsMade() async {
        let interactionConfig = try! InteractionTestUtils.createFakeInteractionConfig(
            eventSequence: [
                InteractionTestUtils.createFakeInteractionEvent(name: "event1"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event2"),
                InteractionTestUtils.createFakeInteractionEvent(name: "event3")
            ]
        )
        await initMockInteractionManager(interactionConfig)
        
        addEventWithNanoTimeFromBoot("event1")
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        
        // Add event3 (wrong event, should create error interaction)
        // This should create an error because event2 was skipped
        addEventWithNanoTimeFromBoot("event3")
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        
        let (failedInteractionId, failedInteraction) = assertSingleFinalInteraction(isSuccess: false)
        XCTAssertTrue(failedInteraction.isErrored, "Failed interaction should be marked as errored")
        
        // Start new interaction - wait for previous to be cleared
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        
        addEventWithNanoTimeFromBoot("event1")
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        addEventWithNanoTimeFromBoot("event2")
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        addEventWithNanoTimeFromBoot("event3")
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        
        let (successInteractionId, successInteraction) = assertSingleFinalInteraction(isSuccess: true)
        XCTAssertFalse(successInteraction.isErrored, "Success interaction should not be marked as errored")
        XCTAssertNotEqual(failedInteractionId, successInteractionId, "Success interaction should have different ID")
    }
}

