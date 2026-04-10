import XCTest
@testable import PulseKit
@testable import OpenTelemetrySdk
@testable import OpenTelemetryApi


final class MeteredSessionProcessorTests: XCTestCase {
  var meteredManager: SessionManager!
  var mockNextProcessor: MockMeteredLogRecordProcessor!
  var meteredLogProcessor: MeteredSessionLogProcessor!
  var meteredSpanProcessor: MeteredSessionSpanProcessor!

  override func setUp() {
    super.setUp()
    SessionStore.teardown()
    
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true
    )
    meteredManager = SessionManager(configuration: config)
    
    mockNextProcessor = MockMeteredLogRecordProcessor()
    meteredLogProcessor = MeteredSessionLogProcessor(
      nextProcessor: mockNextProcessor,
      meteredManager: meteredManager
    )
    meteredSpanProcessor = MeteredSessionSpanProcessor(meteredManager: meteredManager)
  }

  override func tearDown() {
    SessionStore.teardown()
    super.tearDown()
  }

  // MARK: - MeteredSessionLogProcessor Tests

  func testMeteredLogProcessorAddsSessionId() {
    let session = meteredManager.getSession()
    let logRecord = createMockLogRecord()
    
    meteredLogProcessor.onEmit(logRecord: logRecord)
    
    XCTAssertEqual(mockNextProcessor.emitCount, 1)
    
    guard let processedRecord = mockNextProcessor.lastLogRecord else {
      XCTFail("Processed log record should not be nil")
      return
    }
    
    let attributes = processedRecord.attributes
    let meteredId = attributes[SessionConstants.meteredId]
    XCTAssertNotNil(meteredId, "Metered session ID should be added to log record")
    
    guard case .string(let sessionId) = meteredId else {
      XCTFail("Metered session ID should be a string")
      return
    }
    
    XCTAssertEqual(sessionId, session.id, "Session ID should match the current session")
    XCTAssertEqual(sessionId.count, 32, "Session ID should be 32 characters (hex)")
  }

  func testMeteredLogProcessorDoesNotOverwriteExistingId() {
    let existingId = "existing-id-123456789012345678901234567890"
    let logRecord = createMockLogRecord()
    var attributes = logRecord.attributes
    attributes[SessionConstants.meteredId] = AttributeValue.string(existingId)
    
    let logRecordWithId = ReadableLogRecord(
      resource: logRecord.resource,
      instrumentationScopeInfo: logRecord.instrumentationScopeInfo,
      timestamp: logRecord.timestamp,
      observedTimestamp: logRecord.observedTimestamp,
      spanContext: logRecord.spanContext,
      severity: logRecord.severity,
      body: logRecord.body,
      attributes: attributes
    )
    
    meteredLogProcessor.onEmit(logRecord: logRecordWithId)
    
    XCTAssertEqual(mockNextProcessor.emitCount, 1)
    
    guard let processedRecord = mockNextProcessor.lastLogRecord else {
      XCTFail("Processed log record should not be nil")
      return
    }
    
    let processedAttributes = processedRecord.attributes
    let meteredId = processedAttributes[SessionConstants.meteredId]
    XCTAssertNotNil(meteredId, "Metered session ID should still be present")
    
    guard case .string(let sessionId) = meteredId else {
      XCTFail("Metered session ID should be a string")
      return
    }
    
    XCTAssertEqual(sessionId, existingId, "Existing session ID should not be overwritten")
    
    let currentSession = meteredManager.getSession()
    XCTAssertNotEqual(sessionId, currentSession.id, "Existing ID should not match current session")
  }

  func testMeteredLogProcessorUpdatesOnSessionExpiration() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true
    )
    let manager = SessionManager(configuration: config)
    let processor = MeteredSessionLogProcessor(
      nextProcessor: mockNextProcessor,
      meteredManager: manager
    )
    
    let session1 = manager.getSession()
    let logRecord1 = createMockLogRecord()
    processor.onEmit(logRecord: logRecord1)
    
    guard let firstRecord = mockNextProcessor.lastLogRecord else {
      XCTFail("First processed log record should not be nil")
      return
    }
    let firstAttributes = firstRecord.attributes
    let firstId = firstAttributes[SessionConstants.meteredId]
    
    guard case .string(let sessionId1) = firstId else {
      XCTFail("First metered session ID should be a string")
      return
    }
    XCTAssertEqual(sessionId1, session1.id, "First record should have first session ID")
    
    Thread.sleep(forTimeInterval: 0.11)
    
    let session2 = manager.getSession()
    let logRecord2 = createMockLogRecord()
    processor.onEmit(logRecord: logRecord2)
    
    XCTAssertEqual(mockNextProcessor.emitCount, 2)
    
    guard let secondRecord = mockNextProcessor.lastLogRecord else {
      XCTFail("Second processed log record should not be nil")
      return
    }
    let secondAttributes = secondRecord.attributes
    let secondId = secondAttributes[SessionConstants.meteredId]
    
    guard case .string(let sessionId2) = secondId else {
      XCTFail("Second metered session ID should be a string")
      return
    }
    XCTAssertEqual(sessionId2, session2.id, "Second record should have second session ID")
    
    XCTAssertNotEqual(session1.id, session2.id, "Sessions should be different after expiration")
    XCTAssertNotEqual(sessionId1, sessionId2, "Session IDs in log records should be different")
  }

  func testMeteredLogProcessorForwardsToNext() {
    let logRecord = createMockLogRecord()
    
    meteredLogProcessor.onEmit(logRecord: logRecord)
    
    XCTAssertEqual(mockNextProcessor.emitCount, 1)
    XCTAssertNotNil(mockNextProcessor.lastLogRecord)
  }

  func testMeteredLogProcessorShutdown() {
    let result = meteredLogProcessor.shutdown(explicitTimeout: nil)
    XCTAssertEqual(result, .success)
  }

  func testMeteredLogProcessorForceFlush() {
    let result = meteredLogProcessor.forceFlush(explicitTimeout: nil)
    XCTAssertEqual(result, .success)
  }

  // MARK: - MeteredSessionSpanProcessor Tests

  func testMeteredSpanProcessorAddsSessionId() {
    let session = meteredManager.getSession()
    let span = createMockSpan()
    
    meteredSpanProcessor.onStart(parentContext: nil, span: span)
    
    let attributes = span.getAttributes()
    let meteredId = attributes[SessionConstants.meteredId]
    XCTAssertNotNil(meteredId)
    
guard let meteredId else {
XCTFail("Metered session ID should not be nil")
return
}

guard case .string(let sessionId) = meteredId else {
XCTFail("Metered session ID should be a string")
return
}

XCTAssertEqual(sessionId, session.id)
XCTAssertEqual(sessionId.count, 32)
  }

  func testMeteredSpanProcessorUpdatesOnSessionExpiration() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true
    )
    let manager = SessionManager(configuration: config)
    let processor = MeteredSessionSpanProcessor(meteredManager: manager)
    
    let session1 = manager.getSession()
    let span1 = createMockSpan()
    processor.onStart(parentContext: nil, span: span1)
    
    let firstAttributes = span1.getAttributes()
    let firstId = firstAttributes[SessionConstants.meteredId]
    
    Thread.sleep(forTimeInterval: 0.11)
    
    let session2 = manager.getSession()
    let span2 = createMockSpan()
    processor.onStart(parentContext: nil, span: span2)
    
    let secondAttributes = span2.getAttributes()
    let secondId = secondAttributes[SessionConstants.meteredId]
    
    XCTAssertNotEqual(firstId, secondId)
    
    if case .string(let id1) = firstId,
       case .string(let id2) = secondId {
      XCTAssertEqual(id1, session1.id)
      XCTAssertEqual(id2, session2.id)
    }
  }

  func testMeteredSpanProcessorOnEnd() {
    let span = createMockSpan()
    meteredSpanProcessor.onEnd(span: span)
  }

  func testMeteredSpanProcessorShutdown() {
    meteredSpanProcessor.shutdown(explicitTimeout: nil)
  }

  func testMeteredSpanProcessorForceFlush() {
    meteredSpanProcessor.forceFlush(timeout: nil)
  }

  func testMeteredSpanProcessorProperties() {
    XCTAssertTrue(meteredSpanProcessor.isStartRequired)
    XCTAssertFalse(meteredSpanProcessor.isEndRequired)
  }

  // MARK: - Helper Methods

  private func createMockLogRecord() -> ReadableLogRecord {
    return ReadableLogRecord(
      resource: Resource(attributes: [:]),
      instrumentationScopeInfo: InstrumentationScopeInfo(),
      timestamp: Date(),
      observedTimestamp: Date(),
      spanContext: nil,
      severity: .info,
      body: AttributeValue.string("test log"),
      attributes: [:]
    )
  }

  private func createMockSpan() -> MockReadableSpan {
    return MockReadableSpan()
  }
}

// MARK: - Mock Classes

class MockMeteredLogRecordProcessor: LogRecordProcessor {
  var emitCount = 0
  var lastLogRecord: ReadableLogRecord?
  
  func onEmit(logRecord: ReadableLogRecord) {
    emitCount += 1
    lastLogRecord = logRecord
  }
  
  func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
    return .success
  }
  
  func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
    return .success
  }
}
