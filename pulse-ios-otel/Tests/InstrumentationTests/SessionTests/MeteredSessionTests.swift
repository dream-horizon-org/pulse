import XCTest
@testable import PulseKit
@testable import OpenTelemetrySdk
@testable import OpenTelemetryApi


final class MeteredSessionTests: XCTestCase {
  var meteredManager: SessionManager!
  var otelManager: SessionManager!

  override func setUp() {
    super.setUp()
    SessionStore.teardown()
    
    let meteredConfig = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true,
      startEventName: nil,
      endEventName: nil
    )
    meteredManager = SessionManager(configuration: meteredConfig)
    
    let otelConfig = SessionConfig(
      backgroundInactivityTimeout: 15 * 60,
      maxLifetime: 4 * 60 * 60,
      shouldPersist: false,
      startEventName: SessionConstants.sessionStartEvent,
      endEventName: SessionConstants.sessionEndEvent
    )
    otelManager = SessionManager(configuration: otelConfig)
  }

  override func tearDown() {
    SessionStore.teardown()
    super.tearDown()
  }

  // MARK: - Metered Session Configuration Tests

  func testMeteredSessionConfiguration() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true,
      startEventName: nil,
      endEventName: nil
    )
    let manager = SessionManager(configuration: config)
    
    let session = manager.getSession()
    XCTAssertNotNil(session)
    XCTAssertEqual(session.sessionTimeout, config.maxLifetime)
    XCTAssertNil(config.backgroundInactivityTimeout)
    XCTAssertTrue(config.shouldPersist)
  }

  // MARK: - Metered Session Persistence Tests

  func testMeteredSessionPersistsAcrossRestarts() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true
    )
    
    let manager1 = SessionManager(configuration: config)
    let session1 = manager1.getSession()
    let sessionId1 = session1.id
    
    SessionStore.saveImmediately(session: session1)
    
    let manager2 = SessionManager(configuration: config)
    let session2 = manager2.getSession()
    let sessionId2 = session2.id
    
    XCTAssertEqual(sessionId1, sessionId2)
  }

  func testMeteredSessionRestoredFromDisk() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true
    )
    
    let manager1 = SessionManager(configuration: config)
    let session1 = manager1.getSession()
    SessionStore.saveImmediately(session: session1)
    
    let savedId = UserDefaults.standard.object(forKey: SessionStore.idKey) as? String
    XCTAssertEqual(session1.id, savedId)

    let manager2 = SessionManager(configuration: config)
    let session2 = manager2.getSession()
    
    XCTAssertEqual(session1.id, session2.id)
  }

  func testMeteredSessionExpiredSessionNotRestored() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true
    )
    
    let manager1 = SessionManager(configuration: config)
    let session1 = manager1.getSession()
    SessionStore.saveImmediately(session: session1)
    
    Thread.sleep(forTimeInterval: 0.11)
    
    let manager2 = SessionManager(configuration: config)
    let session2 = manager2.getSession()
    
    XCTAssertNotNil(session2)
    XCTAssertNotEqual(session1.id, session2.id)
  }

  // MARK: - Metered Session Event Emission Tests

  func testMeteredSessionDoesNotEmitEvents() {
    SessionEventInstrumentation.queue = []
    SessionEventInstrumentation.isApplied = false
    
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true,
      startEventName: nil,
      endEventName: nil
    )
    let manager = SessionManager(configuration: config)
    
    let session1 = manager.getSession()
    Thread.sleep(forTimeInterval: 0.11)
    let session2 = manager.getSession()
    
    let meteredEvents = SessionEventInstrumentation.queue.filter { event in
      event.session.id == session1.id || event.session.id == session2.id
    }
    
    XCTAssertEqual(meteredEvents.count, 0, "Metered sessions should not emit events")
  }

  func testMeteredSessionNoEventNames() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true,
      startEventName: nil,
      endEventName: nil
    )
    
    XCTAssertNil(config.startEventName)
    XCTAssertNil(config.endEventName)
  }

  // MARK: - Dual Session System Tests

  func testDualSessionSystemIndependent() {
    let meteredSession = meteredManager.getSession()
    let otelSession = otelManager.getSession()
    
    XCTAssertNotEqual(meteredSession.id, otelSession.id)
    
    let meteredSessionId = meteredSession.id
    let newMeteredManager = SessionManager(configuration: SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true
    ))
    let restoredSession = newMeteredManager.getSession()
    XCTAssertEqual(restoredSession.id, meteredSessionId, "Metered session should be persisted and restored")
    
    let otelSessionId = otelSession.id
    let newOtelManager = SessionManager(configuration: SessionConfig(
      backgroundInactivityTimeout: 3,
      maxLifetime: 4 * 60 * 60,
      shouldPersist: false
    ))
    let newOtelSession = newOtelManager.getSession()
    XCTAssertNotEqual(newOtelSession.id, otelSessionId, "OTEL session should not persist")
  }

  func testDualSessionSystemDifferentExpiration() {
    let meteredSession = meteredManager.getSession()
    let otelSession = otelManager.getSession()
    
    let meteredExpiry = meteredSession.expireTime
    let otelExpiry = otelSession.expireTime
    
    XCTAssertGreaterThan(otelExpiry, meteredExpiry)
  }

  func testDualSessionSystemDifferentStorage() {
    let meteredSession = meteredManager.getSession()
    let meteredSessionId = meteredSession.id
    
    SessionStore.saveImmediately(session: meteredSession)
    
    let savedId = UserDefaults.standard.object(forKey: SessionStore.idKey) as? String
    XCTAssertNotNil(savedId, "Metered session should be saved to UserDefaults")
    XCTAssertEqual(meteredSessionId, savedId, "Saved session ID should match metered session")
    
    let otelSession = otelManager.getSession()
    let otelSessionId = otelSession.id
    
    let currentSavedId = UserDefaults.standard.object(forKey: SessionStore.idKey) as? String
    XCTAssertNotNil(currentSavedId, "UserDefaults should still have metered session")
    XCTAssertEqual(currentSavedId, meteredSessionId, "UserDefaults should still have metered session ID")
    XCTAssertNotEqual(currentSavedId, otelSessionId, "UserDefaults should NOT have OTEL session ID (in-memory only)")
    
    XCTAssertNotEqual(meteredSessionId, otelSessionId, "Metered and OTEL sessions should have different IDs")
    
    let newOtelManager = SessionManager(configuration: SessionConfig(
      backgroundInactivityTimeout: 15 * 60,
      maxLifetime: 4 * 60 * 60,
      shouldPersist: false
    ))
    let newOtelSession = newOtelManager.getSession()
    
    XCTAssertNotEqual(newOtelSession.id, otelSessionId, "OTEL session should not persist - new manager gets new session")
    
    let newMeteredManager = SessionManager(configuration: SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 30 * 60,
      shouldPersist: true
    ))
    let restoredMeteredSession = newMeteredManager.getSession()
    
    XCTAssertEqual(restoredMeteredSession.id, meteredSessionId, "Metered session should be restored from disk")
  }

  // MARK: - Metered Session ID Format Tests

  func testMeteredSessionIdFormat() {
    let session = meteredManager.getSession()
    
    XCTAssertEqual(session.id.count, 32)
    let hexPattern = "^[a-f0-9]{32}$"
    let regex = try! NSRegularExpression(pattern: hexPattern)
    let range = NSRange(location: 0, length: session.id.utf16.count)
    XCTAssertNotNil(regex.firstMatch(in: session.id, options: [], range: range))
  }

  func testMeteredSessionIdMatchesOtelFormat() {
    let meteredSession = meteredManager.getSession()
    let otelSession = otelManager.getSession()
    
    XCTAssertEqual(meteredSession.id.count, 32)
    XCTAssertEqual(otelSession.id.count, 32)
    
    let hexPattern = "^[a-f0-9]{32}$"
    let regex = try! NSRegularExpression(pattern: hexPattern)
    
    let meteredRange = NSRange(location: 0, length: meteredSession.id.utf16.count)
    let otelRange = NSRange(location: 0, length: otelSession.id.utf16.count)
    
    XCTAssertNotNil(regex.firstMatch(in: meteredSession.id, options: [], range: meteredRange))
    XCTAssertNotNil(regex.firstMatch(in: otelSession.id, options: [], range: otelRange))
  }

  // MARK: - Metered Session Expiration Tests

  func testMeteredSessionExpiresAfterMaxLifetime() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true
    )
    let manager = SessionManager(configuration: config)
    
    let session1 = manager.getSession()
    
    Thread.sleep(forTimeInterval: 0.11)
    
    let session2 = manager.getSession()
    
      XCTAssertNotEqual(session1.id, session2.id)
      XCTAssertEqual(session2.previousId, session1.id)
  }

  func testMeteredSessionNotAffectedByBackground() {
    #if canImport(UIKit)
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true
    )
    let manager = SessionManager(configuration: config)
    
    let session1 = manager.getSession()
    let sessionId1 = session1.id
    
    NotificationCenter.default.post(
      name: UIApplication.didEnterBackgroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.11)
    
    NotificationCenter.default.post(
      name: UIApplication.willEnterForegroundNotification,
      object: nil
    )
    
    Thread.sleep(forTimeInterval: 0.1)
    
    let session2 = manager.getSession()
    XCTAssertNotEqual(sessionId1, session2.id)
    #endif
  }

  // MARK: - Metered Session Attribute Key Tests

  func testMeteredSessionAttributeKey() {
    XCTAssertEqual(SessionConstants.meteredId, "pulse.metering.session.id")
  }

  // MARK: - Metered Session Previous ID Tests

  func testMeteredSessionPreviousId() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true
    )
    let manager = SessionManager(configuration: config)
    
    let session1 = manager.getSession()
    XCTAssertNil(session1.previousId)
    
    Thread.sleep(forTimeInterval: 0.11)
    
    let session2 = manager.getSession()
    XCTAssertEqual(session2.previousId, session1.id)
  }

  func testMeteredSessionPreviousIdPersisted() {
    let config = SessionConfig(
      backgroundInactivityTimeout: nil,
      maxLifetime: 0.1,
      shouldPersist: true
    )
    
    let manager1 = SessionManager(configuration: config)
    let session1 = manager1.getSession()
    SessionStore.saveImmediately(session: session1)
    
    Thread.sleep(forTimeInterval: 0.11)
    
    let manager2 = SessionManager(configuration: config)
    let session2 = manager2.getSession()
    
    XCTAssertEqual(session2.previousId, session1.id)
  }
}
