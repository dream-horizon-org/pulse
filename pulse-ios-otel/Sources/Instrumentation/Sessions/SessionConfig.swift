/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Centralized default values for session configuration
public struct SessionConfigDefaults {
  public static let backgroundInactivityTimeout: TimeInterval = 15 * 60
  public static let maxLifetime: TimeInterval = 4 * 60 * 60
  public static let shouldPersist: Bool = false
}

public struct SessionConfig {
  public let backgroundInactivityTimeout: TimeInterval?
  public let maxLifetime: TimeInterval?
  public let shouldPersist: Bool
  public let startEventName: String?
  public let endEventName: String?
  
  public init(
    backgroundInactivityTimeout: TimeInterval? = SessionConfigDefaults.backgroundInactivityTimeout,
    maxLifetime: TimeInterval? = SessionConfigDefaults.maxLifetime,
    shouldPersist: Bool = SessionConfigDefaults.shouldPersist,
    startEventName: String? = SessionConstants.sessionStartEvent,
    endEventName: String? = SessionConstants.sessionEndEvent
  ) {
    self.backgroundInactivityTimeout = backgroundInactivityTimeout
    self.maxLifetime = maxLifetime
    self.shouldPersist = shouldPersist
    self.startEventName = startEventName
    self.endEventName = endEventName
  }
  
  public static let `default` = SessionConfig()
}

/// Builder for SessionConfig with fluent API
public class SessionConfigBuilder {
  public private(set) var backgroundInactivityTimeout: TimeInterval? = SessionConfigDefaults.backgroundInactivityTimeout
  public private(set) var maxLifetime: TimeInterval? = SessionConfigDefaults.maxLifetime
  public private(set) var shouldPersist: Bool = SessionConfigDefaults.shouldPersist
  
  public init() {}
  
  public func with(backgroundInactivityTimeout: TimeInterval?) -> Self {
    self.backgroundInactivityTimeout = backgroundInactivityTimeout
    return self
  }
  
  public func with(maxLifetime: TimeInterval?) -> Self {
    self.maxLifetime = maxLifetime
    return self
  }
  
  public func with(shouldPersist: Bool) -> Self {
    self.shouldPersist = shouldPersist
    return self
  }
  
  public func build() -> SessionConfig {
    return SessionConfig(
      backgroundInactivityTimeout: backgroundInactivityTimeout,
      maxLifetime: maxLifetime,
      shouldPersist: shouldPersist
    )
  }
}

extension SessionConfig {
  public static func builder() -> SessionConfigBuilder {
    return SessionConfigBuilder()
  }
}
