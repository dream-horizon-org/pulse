import XCTest
@testable import PulseKit
#if canImport(UIKit)
import UIKit
#endif

final class SessionManagerBackgroundTests: XCTestCase {
  var sessionManager: SessionManager!
  var config: SessionConfig!

  override func setUp() {
    super.setUp()
    SessionStore.teardown()
    
    config = SessionConfig(
      backgroundInactivityTimeout: 1,
      maxLifetime: 4 * 60 * 60,
      shouldPersist: false
    )
    sessionManager = SessionManager(configuration: config)
  }

  override func tearDown() {
    SessionStore.teardown()
    super.tearDown()
  }

  // MARK: - Background Timeout Tests

  func testBackgroundTimeoutNotSetWhenNil() {
    let configWithoutTimeout = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 4 * 60 * 60,
      shouldPersist: false
    )
    let manager = SessionManager(configuration: configWithoutTimeout)
    let session = manager.getSession()
    XCTAssertNotNil(session)
  }

  func testSessionDoesNotExpireInForeground() {
    let session = sessionManager.getSession()
    let sessionId = session.id
    
    Thread.sleep(forTimeInterval: 1.5)
    
    let newSession = sessionManager.getSession()
    XCTAssertEqual(sessionId, newSession.id)
  }

  #if canImport(UIKit)
  func testBackgroundTimeoutExpiration() {
    let expectation = XCTestExpectation(description: "Session expired in background")
    expectation.isInverted = true
    
    let session = sessionManager.getSession()
    let sessionId = session.id
    
    NotificationCenter.default.post(
      name: UIApplication.didEnterBackgroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 1.1)
    
    NotificationCenter.default.post(
      name: UIApplication.willEnterForegroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.1)
    
    let newSession = sessionManager.getSession()
    XCTAssertNotEqual(sessionId, newSession.id)
    XCTAssertEqual(newSession.previousId, sessionId)
  }

  func testBackgroundTimeoutNotExpiredIfWithinTimeout() {
    let session = sessionManager.getSession()
    let sessionId = session.id
    
    NotificationCenter.default.post(
      name: UIApplication.didEnterBackgroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.9)
    
    NotificationCenter.default.post(
      name: UIApplication.willEnterForegroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 1)
    
    let newSession = sessionManager.getSession()
    XCTAssertEqual(sessionId, newSession.id)
  }

  func testBackgroundStartTimeCaptured() {
    SessionEventInstrumentation.queue = []
    SessionEventInstrumentation.isApplied = false
    
    let session = sessionManager.getSession()
    let sessionId = session.id
    let backgroundTimeBeforeNotification = Date()
    
    NotificationCenter.default.post(
      name: UIApplication.didEnterBackgroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.05)
    Thread.sleep(forTimeInterval: 1.1)
    
    NotificationCenter.default.post(
      name: UIApplication.willEnterForegroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.1)
    
    let newSession = sessionManager.getSession()
    
    XCTAssertNotEqual(session.id, newSession.id)
    XCTAssertEqual(newSession.previousId, sessionId)
    
    let endEvents = SessionEventInstrumentation.queue.filter {
      $0.eventType == .end && $0.session.id == sessionId
    }
    
    XCTAssertGreaterThanOrEqual(endEvents.count, 1, "Should have at least one session.end event")
    
    if let endEvent = endEvents.first {
      XCTAssertNotNil(endEvent.endTimestamp, "End timestamp should be set for background expiration")
      
      if let endTimestamp = endEvent.endTimestamp {
        let timeDiff = abs(endTimestamp.timeIntervalSince(backgroundTimeBeforeNotification))
        XCTAssertLessThan(timeDiff, 0.1, "End timestamp should be close to background start time (within 100ms)")
        XCTAssertLessThan(endTimestamp, Date(), "End timestamp should be in the past (background start time)")
      }
    }
  }
  #endif

  // MARK: - Background + MaxLifetime Tests

  #if canImport(UIKit)
  func testSessionExpiresByMaxLifetimeInBackground() {
    SessionEventInstrumentation.queue = []
    SessionEventInstrumentation.isApplied = false
    
    let config = SessionConfig(
      backgroundInactivityTimeout: 3,
      maxLifetime: 1,
      shouldPersist: false,
      startEventName: SessionConstants.sessionStartEvent,
      endEventName: SessionConstants.sessionEndEvent
    )
    sessionManager = SessionManager(configuration: config)
    
    let session = sessionManager.getSession()
    let sessionId = session.id
    let sessionStartTime = session.startTime
    let backgroundTimeBeforeNotification = Date()
    
    NotificationCenter.default.post(
      name: UIApplication.didEnterBackgroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.05)
    Thread.sleep(forTimeInterval: 1.1)
    
    let expectedExpirationTime = sessionStartTime.addingTimeInterval(1.0)
    XCTAssertLessThanOrEqual(expectedExpirationTime, Date(), "Session should have expired by maxLifetime")
    
    NotificationCenter.default.post(
      name: UIApplication.willEnterForegroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.1)
    
    let newSession = sessionManager.getSession()
    
    XCTAssertNotEqual(sessionId, newSession.id, "Session should expire by maxLifetime in background")
    XCTAssertEqual(newSession.previousId, sessionId, "New session should have previous session ID")
    
    let endEvents = SessionEventInstrumentation.queue.filter {
      $0.eventType == .end && $0.session.id == sessionId
    }
    
    XCTAssertGreaterThanOrEqual(endEvents.count, 1, "Should have session.end event for maxLifetime expiration in background")
    
    if let endEvent = endEvents.first {
      XCTAssertNotNil(endEvent.endTimestamp, "End timestamp should be set when session expires by maxLifetime in background")
      
      if let endTimestamp = endEvent.endTimestamp {
        let timeDiff = abs(endTimestamp.timeIntervalSince(backgroundTimeBeforeNotification))
        XCTAssertLessThan(timeDiff, 0.1, "End timestamp should be background start time (within 100ms)")
        
        let maxLifetimeExpirationTime = sessionStartTime.addingTimeInterval(1.0)
        XCTAssertNotEqual(endTimestamp, maxLifetimeExpirationTime, "End timestamp should be background start, not maxLifetime expiration")
      }
    }
  }

  func testSessionExpiresByBackgroundTimeoutBeforeMaxLifetime() {
    let config = SessionConfig(
      backgroundInactivityTimeout: 1,
      maxLifetime: 2,
      shouldPersist: false
    )
    sessionManager = SessionManager(configuration: config)
    
    let session = sessionManager.getSession()
    let sessionId = session.id
    
    NotificationCenter.default.post(
      name: UIApplication.didEnterBackgroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 1.1)
    
    NotificationCenter.default.post(
      name: UIApplication.willEnterForegroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.1)
    
    let newSession = sessionManager.getSession()
    XCTAssertNotEqual(sessionId, newSession.id)
    XCTAssertEqual(newSession.previousId, sessionId)
  }
  #endif

  // MARK: - Event Timestamp Tests

  #if canImport(UIKit)
  func testSessionEndEventHasBackgroundTimestamp() {
    SessionEventInstrumentation.queue = []
    SessionEventInstrumentation.isApplied = false
    
    let session = sessionManager.getSession()
    let sessionId = session.id
    let backgroundTimeBeforeNotification = Date()
    
    NotificationCenter.default.post(
      name: UIApplication.didEnterBackgroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.05)
    Thread.sleep(forTimeInterval: 1.1)
    
    NotificationCenter.default.post(
      name: UIApplication.willEnterForegroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.1)
    
    _ = sessionManager.getSession()
    
    let endEvents = SessionEventInstrumentation.queue.filter {
      $0.eventType == .end && $0.session.id == sessionId
    }
    
    XCTAssertGreaterThanOrEqual(endEvents.count, 1, "Should have at least one session.end event")
    
    if let endEvent = endEvents.first {
      XCTAssertNotNil(endEvent.endTimestamp, "End timestamp should be set for background expiration")
      
      if let endTimestamp = endEvent.endTimestamp {
        let timeDiff = abs(endTimestamp.timeIntervalSince(backgroundTimeBeforeNotification))
        XCTAssertLessThan(timeDiff, 0.1, "End timestamp should be close to background start time (within 100ms)")
        
        let currentTime = Date()
        XCTAssertLessThan(endTimestamp, currentTime, "End timestamp should be in the past (background start time)")
        XCTAssertGreaterThan(endTimestamp, session.startTime, "End timestamp should be after session start")
      }
    }
  }
  #endif
}
