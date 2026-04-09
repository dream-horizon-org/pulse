/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import PulseKit

/// Tests for Session Replay Emitter Shutdown lifecycle.
/// Ensures graceful shutdown and prevents race conditions during app termination.
/// Mirrors Android PersistingReplayEmitter shutdown behavior (AtomicBoolean guarding with final flush).
class SessionReplayEmitterShutdownTests: XCTestCase {
    
    private var tempDirectory: URL!
    
    override func setUp() {
        super.setUp()
        tempDirectory = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
    }
    
    override func tearDown() {
        try? FileManager.default.removeItem(at: tempDirectory)
        tempDirectory = nil
        super.tearDown()
    }
    
    // MARK: - Synchronous shutdown scheduling (release-safety)

    /// Regression: `shutdown()` must run final-flush work on the persisting queue before returning, so
    /// releasing the owner (e.g. `SessionReplayRecorder`) does not race `[weak self]` async teardown.
    func testShutdownInvokesSendRawBeforeReturning() {
        let mockTransport = MockSessionReplayTransport()
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: mockTransport,
            flushIntervalSeconds: 300,
            flushAt: 10_000,
            maxBatchSize: 100
        )

        emitter.emit(payloadJson: "{\"test\": \"pending\"}")

        XCTAssertEqual(mockTransport.sendRawCallCount, 0, "Precondition: payload should not flush before shutdown")

        emitter.shutdown()

        XCTAssertGreaterThanOrEqual(
            mockTransport.sendRawCallCount,
            1,
            "Final flush should be scheduled synchronously during shutdown, before the call returns"
        )
    }

    // MARK: - Shutdown Prevents New Emissions (Android parity)
    
    /// Verifies that after shutdown(), new emit() calls are rejected without entering the queue.
    /// This matches Android's AtomicBoolean guard preventing any emit after shutdown.
    func testShutdownPreventsNewEmissions() {
        let mockTransport = MockSessionReplayTransport()
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: mockTransport,
            flushIntervalSeconds: 60,
            flushAt: 10,
            maxBatchSize: 50
        )
        
        // Signal shutdown
        emitter.shutdown()
        
        // Try to emit after shutdown - this should be rejected synchronously
        emitter.emit(payloadJson: "{\"test\": \"payload\"}")
        emitter.emit(payloadJson: "{\"test\": \"payload2\"}")
        
        // Give time for any queued operations
        let expectation = self.expectation(description: "Wait for queue processing")
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.2) {
            expectation.fulfill()
        }
        waitForExpectations(timeout: 1.0)
        
        // No payloads should have been sent
        XCTAssertEqual(mockTransport.sendRawCallCount, 0, "Transport should not receive any payloads after shutdown")
    }
    
    // MARK: - Idempotent Shutdown (Android parity)
    
    /// Multiple shutdown() calls should be safe (no crashes, no race conditions).
    /// Mirrors Android's guard check preventing redundant shutdown.
    func testMultipleShutdownCallsAreSafe() {
        let mockTransport = MockSessionReplayTransport()
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: mockTransport
        )
        
        // Multiple shutdown calls should not crash
        emitter.shutdown()
        emitter.shutdown()
        emitter.shutdown()
        
        // Verify no payloads sent (safe idempotency)
        XCTAssertEqual(mockTransport.sendRawCallCount, 0, "Multiple shutdowns should not cause emissions")
    }
    
    // MARK: - Rapid Init/Deinit Cycles (Android parity)
    
    /// Tests stress scenario: rapid emitter creation and shutdown prevents crashes.
    /// Simulates app backgrounding/foregrounding cycles on app termination.
    func testRapidShutdownCyclesDoNotCrash() {
        for iteration in 0..<20 {
            let mockTransport = MockSessionReplayTransport()
            let emitter = SessionReplayPersistingEmitter(
                storageDir: tempDirectory.appendingPathComponent("iter\(iteration)"),
                transport: mockTransport,
                flushIntervalSeconds: 10,
                flushAt: 5,
                maxBatchSize: 25
            )
            
            // Emit some payloads
            for i in 0..<3 {
                emitter.emit(payloadJson: "{\"id\": \(i)}")
            }
            
            // Immediately shutdown
            emitter.shutdown()
            
            // Deinit happens automatically
            // If we reach here without crash, this iteration passed
        }
        
        XCTAssert(true, "20 rapid init/deinit/shutdown cycles completed without crashes")
    }
    
    // MARK: - Final Flush on Shutdown (Android parity)
    
    /// Verifies that pending payloads are flushed when shutdown() is called.
    /// Ensures no data loss during app termination (matches Android behavior).
    func testShutdownTriggerssFinalFlush() {
        let mockTransport = MockSessionReplayTransport()
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: mockTransport,
            flushIntervalSeconds: 300,  // Very long interval, prevent time-based flush
            flushAt: 100,                // High threshold, prevent size-based flush
            maxBatchSize: 100
        )
        
        // Emit a payload that wouldn't normally flush (below thresholds)
        emitter.emit(payloadJson: "{\"test\": \"final_payload\"}")
        
        // Shutdown should trigger final flush immediately
        emitter.shutdown()
        
        // Wait for async flush to complete
        let expectation = self.expectation(description: "Final flush completes")
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.5) {
            expectation.fulfill()
        }
        waitForExpectations(timeout: 2.0)
        
        // Final flush should have sent the payload
        XCTAssert(mockTransport.sendRawCallCount >= 1, "Shutdown should trigger final flush of pending payloads")
    }
    
    // MARK: - No Emissions After Shutdown (Synchronous Guard)
    
    /// Tests that the synchronous isShutDown check prevents queue entry.
    /// Verifies iOS implementation matches Android's AtomicBoolean semantics.
    func testEmitAfterShutdownIsBlockedSynchronously() {
        let mockTransport = MockSessionReplayTransport()
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: mockTransport,
            flushIntervalSeconds: 60,
            flushAt: 1,
            maxBatchSize: 50
        )
        
        // Shutdown first
        emitter.shutdown()
        
        // Then emit multiple times in quick succession
        for i in 0..<50 {
            emitter.emit(payloadJson: "{\"id\": \(i)}")
        }
        
        // Allow queue to process (none should be accepted)
        let expectation = self.expectation(description: "Queue drain")
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.3) {
            expectation.fulfill()
        }
        waitForExpectations(timeout: 1.0)
        
        // No payloads should have been sent
        XCTAssertEqual(mockTransport.sendRawCallCount, 0, "All emissions after shutdown should be rejected")
    }
    
    // MARK: - Emit Before Shutdown Succeeds
    
    /// Ensures payloads emitted before shutdown are still flushed.
    /// Tests the window of acceptance between emit and shutdown.
    func testEmissionsBeforeShutdownAreProcessed() {
        let mockTransport = MockSessionReplayTransport()
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: mockTransport,
            flushIntervalSeconds: 60,
            flushAt: 2,
            maxBatchSize: 50
        )
        
        // Emit before shutdown
        emitter.emit(payloadJson: "{\"payload\": 1}")
        emitter.emit(payloadJson: "{\"payload\": 2}")
        
        // Now shutdown (should flush these)
        emitter.shutdown()
        
        // Wait for final flush
        let expectation = self.expectation(description: "Final flush")
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.5) {
            expectation.fulfill()
        }
        waitForExpectations(timeout: 2.0)
        
        // Should have sent the payloads via final flush
        XCTAssert(mockTransport.sendRawCallCount >= 1, "Emissions before shutdown should be flushed")
    }
}
