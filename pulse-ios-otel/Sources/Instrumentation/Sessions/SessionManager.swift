/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
#if canImport(UIKit)
import UIKit
#endif

/// Manages OpenTelemetry sessions with automatic expiration and persistence.
/// Provides thread-safe access to session information and handles session lifecycle.
/// Sessions are automatically extended on access and persisted to UserDefaults.
/// Supports background inactivity timeout for OTEL sessions (not metered sessions).
public class SessionManager {
  private var configuration: SessionConfig
  private var session: Session?
  private var lock = NSLock()
  private var sessionStorage: SessionStorage
  private var defaultMaxlifetime: TimeInterval = 4 * 60 * 60
  private var backgroundStartTime: Date?
  private var sessionExpiredInBackground: Bool = false
  private var backgroundObserver: NSObjectProtocol?
  private var foregroundObserver: NSObjectProtocol?
  
  public init(configuration: SessionConfig = .default) {
    self.configuration = configuration
    self.sessionStorage = configuration.shouldPersist ? PersistentSessionStorage() : InMemorySessionStorage()
    restoreSessionFromDisk()
    
    if configuration.backgroundInactivityTimeout != nil {
      setupAppLifecycleObservers()
    }
  }
  
  /// Cleans up notification observers when SessionManager is deallocated
  /// This prevents memory leaks by removing observers registered with NotificationCenter
  deinit {
    [backgroundObserver, foregroundObserver].compactMap { $0 }.forEach {
      NotificationCenter.default.removeObserver($0)
    }
  }
  
  /// Sets up iOS notification observers for app background/foreground transitions
  /// Uses UIApplication.didEnterBackgroundNotification and willEnterForegroundNotification
  private func setupAppLifecycleObservers() {
    #if canImport(UIKit)
    backgroundObserver = NotificationCenter.default.addObserver(
      forName: UIApplication.didEnterBackgroundNotification,
      object: nil,
      queue: .main
    ) { [weak self] _ in
      self?.lock.withLock { self?.backgroundStartTime = Date() }
    }
    
    foregroundObserver = NotificationCenter.default.addObserver(
      forName: UIApplication.willEnterForegroundNotification,
      object: nil,
      queue: .main
    ) { [weak self] _ in
      guard let self = self,
            let backgroundStart = self.backgroundStartTime,
            let timeout = self.configuration.backgroundInactivityTimeout,
            let currentSession = self.session,
            Date().timeIntervalSince(backgroundStart) >= timeout || currentSession.isExpired() else {
        self?.lock.withLock { self?.backgroundStartTime = nil }
        return
      }
      
      // Session expired in background - emit session.end with background start timestamp
      self.lock.withLock {
        if let endEventName = self.configuration.endEventName {
          let expiredSession = Session(
            id: currentSession.id,
            expireTime: backgroundStart,
            previousId: currentSession.previousId,
            startTime: currentSession.startTime,
            sessionTimeout: currentSession.sessionTimeout
          )
          SessionEventInstrumentation.addSession(
            session: expiredSession,
            eventType: .end,
            eventName: endEventName,
            endTimestamp: backgroundStart
          )
        }
        // Mark session as expired but keep it so we can use its ID as previousId for next session
        self.sessionExpiredInBackground = true
        self.backgroundStartTime = nil
      }
    }
    #endif
  }

  /// This method is thread-safe 
  /// - Returns: The current active session
  @discardableResult
  public func getSession() -> Session {
    // We only lock once when fetching the current session to expire with thread safety
    return lock.withLock {
      refreshSession()
      return session!
    }
  }

  /// Gets the current session without extending its expireTime time
  /// - Returns: The current session if one exists, nil otherwise
  public func peekSession() -> Session? {
    return session
  }

  private func startSession() {
    let now = Date()
    let previousId = session?.id
    let newId = TraceId.random().hexString

    /// Queue the previous session for a session.end event
    if session != nil, let endEventName = configuration.endEventName, !sessionExpiredInBackground {
        SessionEventInstrumentation.addSession(session: session!, eventType: .end, eventName: endEventName, endTimestamp: session?.expireTime)
    }

    session = Session(
      id: newId,
      expireTime: now.addingTimeInterval(
        Double(
            configuration.maxLifetime ?? defaultMaxlifetime
        )
      ),
      previousId: previousId,
      startTime: now,
      sessionTimeout: configuration.maxLifetime ?? defaultMaxlifetime
    )

    // Queue the new session for a session.start event
    if let startEventName = configuration.startEventName {
      SessionEventInstrumentation.addSession(session: session!, eventType: .start, eventName: startEventName)
    }
  }

  /// Refreshes the current session, creating new one if expired
  /// Checks both maxLifetime expiration and background inactivity timeout
  private func refreshSession() {
    let expiredByMaxLifetime = session == nil || session!.isExpired()
    let expiredByBackground = sessionExpiredInBackground
    
    if expiredByMaxLifetime || expiredByBackground {
      startSession()
      sessionExpiredInBackground = false
    } else {
      session = Session(
        id: session!.id,
        expireTime: session!.expireTime,
        previousId: session!.previousId,
        startTime: session!.startTime,
        sessionTimeout: TimeInterval(configuration.maxLifetime ?? defaultMaxlifetime)
      )
    }
    saveSessionToDisk()
  }

  /// Schedules the current session to be persisted to UserDefaults
  private func saveSessionToDisk() {
    if let currentSession = session {
      sessionStorage.save(currentSession)
    }
  }

  /// Restores a previously saved session from UserDefaults
  private func restoreSessionFromDisk() {
      session = sessionStorage.get()
  }
}
