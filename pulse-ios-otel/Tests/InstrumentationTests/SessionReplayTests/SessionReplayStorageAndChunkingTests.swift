/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import PulseKit

class SessionReplayStorageAndChunkingTests: XCTestCase {
    private var tempDirectory: URL!
    private var transport: MockSessionReplayTransport!
    
    override func setUp() {
        super.setUp()
        
        let tempDirURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: tempDirURL, withIntermediateDirectories: true)
        self.tempDirectory = tempDirURL
        
        transport = MockSessionReplayTransport()
    }
    
    override func tearDown() {
        super.tearDown()
        try? FileManager.default.removeItem(at: tempDirectory)
        transport = nil
    }
    
    // MARK: - Testing File Cleanup and Limits
    
    func testDiskTrimOnInit() {
        let maxBatchSize = 5
        
        // Arrange: Simulate 10 leftover screenshots physically left on disk from a crashed session.
        for i in 0..<10 {
            let fileURL = tempDirectory.appendingPathComponent(UUID().uuidString).appendingPathExtension("replay")
            try? "{\"id\": \\(i)}".data(using: .utf8)?.write(to: fileURL)
            // Stagger file timestamps physically slightly to ensure deterministic age sorting
            usleep(10_000)
        }
        
        let filesBefore = (try? FileManager.default.contentsOfDirectory(at: tempDirectory, includingPropertiesForKeys: nil))?.count ?? 0
        XCTAssertEqual(filesBefore, 10, "There should be exactly 10 orphaned files on disk before initialization.")
        
        // Act: Initialize the emitter which triggers cleanup.
        // We use flushAt 50 to ensure NO active flushing interferes with the pure disk trim mechanism test!
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: transport,
            encryption: NoOpSessionReplayEncryption(),
            flushIntervalSeconds: 60,
            flushAt: 50,
            maxBatchSize: maxBatchSize
        )
        
        // Allow time for initialization dispatch queues
        usleep(100_000)
        
        // Assert: 5 oldest files were purged during init() solely due to maxBatchSize constraints
        let filesAfter = (try? FileManager.default.contentsOfDirectory(at: tempDirectory, includingPropertiesForKeys: nil))?.count ?? 0
        XCTAssertEqual(filesAfter, maxBatchSize, "The initializer should have strictly trimmed stray files down to \(maxBatchSize)")
        
        // Retain reference to avoid premature deallocation during test execution
        _ = emitter
    }
    
    func testMaxBatchSizeEnforcesStorageCap() {
        let maxBatchSize = 10
        
        // Act: Initialize emitter with flushAt=100 (disrupts network so we purely test disk storage bounds)
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: transport,
            encryption: NoOpSessionReplayEncryption(),
            flushIntervalSeconds: 60,
            flushAt: 100, // disables auto-flushing logic
            maxBatchSize: maxBatchSize
        )
        
        // Emit 15 payloads via emit loop
        for i in 0..<15 {
            let payload = """
            {"type": "meta", "timestamp": \(i)}
            """
            emitter.emit(payloadJson: payload)
            // Small delay to ensure clean sequential file writes
            usleep(10_000) 
        }
        
        // Give time for async operations to completely finish I/O writing and queue trimming
        usleep(200_000)
        
        // Verify disk file count
        let files = (try? FileManager.default.contentsOfDirectory(
            at: tempDirectory,
            includingPropertiesForKeys: nil
        ).filter { $0.pathExtension == "replay" }) ?? []
        
        XCTAssertEqual(files.count, maxBatchSize, "Should strictly enforce storage cap of \(maxBatchSize) independent of the network")
    }
    
    // MARK: - Explicit Chunking Behaviors
    
    func testSendCachedEventsDividesPayloadsIntoChunks() {
        let flushAt = 3
        
        // Arrange: Direct disk writes to explicitly guarantee exactly 9 files exist before testing chunk distribution
        for i in 0..<9 {
            let payload = """
            {"type": "meta", "id": \(i)}
            """
            let fileURL = tempDirectory.appendingPathComponent(UUID().uuidString).appendingPathExtension("replay")
            try? payload.data(using: .utf8)?.write(to: fileURL)
        }
        
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: transport,
            encryption: NoOpSessionReplayEncryption(),
            flushIntervalSeconds: 60, // Network sweeps only once a minute to avoid Timer races
            flushAt: flushAt, // Chunk size is explicitly tested at exactly 3 here
            maxBatchSize: 50
        )
        usleep(50_000)
        
        // Act: Manually fire the network sweep pipeline to collect our 9 mock orphaned files. 
        emitter.sendCachedEvents()
        
        // Wait slightly longer so HTTP mock blocks complete all network completions
        usleep(500_000)
        
        // Assert: 9 orphaned files / chunking at size 3 = 3 separate HTTP requests dispatched
        XCTAssertEqual(transport.sendRawCallCount, 3, "sendCachedEvents should divide 9 items into exactly 3 requests (flushAt=3)")
    }
    
    func testFlushIfNeededUsesFlushAtLimit() {
        let flushAt = 4
        // Emitting exactly enough payloads to cross the boundary logic limit
        let emitter = SessionReplayPersistingEmitter(
            storageDir: tempDirectory,
            transport: transport,
            encryption: NoOpSessionReplayEncryption(),
            flushIntervalSeconds: 60,
            flushAt: flushAt,
            maxBatchSize: 50
        )
        
        // Fire 5 payloads over the max requirement (4). So network triggering SHOULD fire actively.
        for i in 0..<5 {
            emitter.emit(payloadJson: "{\"id\": \\(i)}")
        }
        
        // wait for background enqueue to process and fire the auto-flush task
        usleep(250_000)
        
        XCTAssertGreaterThan(transport.sendRawCallCount, 0, "Network calls should trigger automatically when the queue pushes past flushAt")
    }
}

// MARK: - Mock Transport for Testing
class MockSessionReplayTransport: SessionReplayTransport {
    var sendRawCallCount = 0
    var shouldFailAfterCallCount: Int? = nil
    
    init() {
        super.init(endpointBaseUrl: "http://localhost:8080", headers: [:])!
    }
    
    override func sendRaw(jsonString: String, completion: @escaping (Bool) -> Void) {
        sendRawCallCount += 1
        
        let shouldFail: Bool
        if let failAfter = shouldFailAfterCallCount {
            shouldFail = sendRawCallCount > failAfter
        } else {
            shouldFail = false
        }
        
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) {
            completion(!shouldFail)
        }
    }
    
    func resetCallCount() {
        sendRawCallCount = 0
    }
}
