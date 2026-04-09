/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Represents an OpenTelemetry session with lifecycle management.
public struct Session: Equatable {
  public let id: String
  public let expireTime: Date
  public let previousId: String?
  public let startTime: Date
  public let sessionTimeout: TimeInterval

  public init(id: String,
              expireTime: Date,
              previousId: String? = nil,
              startTime: Date = Date(),
              sessionTimeout: TimeInterval = SessionConfig.default.maxLifetime ?? SessionConfigDefaults.maxLifetime) {
    self.id = id
    self.expireTime = expireTime
    self.previousId = previousId
    self.startTime = startTime
    self.sessionTimeout = sessionTimeout
  }

  /// Two sessions are considered equal if they have the same ID, prevID, startTime, and expiry timestamp
  public static func == (lhs: Session, rhs: Session) -> Bool {
    return lhs.expireTime == rhs.expireTime &&
      lhs.id == rhs.id &&
      lhs.previousId == rhs.previousId &&
      lhs.startTime == rhs.startTime &&
      lhs.sessionTimeout == rhs.sessionTimeout
  }

  /// Checks if the session has expired
  /// - Returns: True if the current time is past the session's expireTime time
  public func isExpired() -> Bool {
    return expireTime <= Date()
  }

  public var endTime: Date? {
    guard isExpired() else { return nil }
    let expectedForegroundExpiration = startTime.addingTimeInterval(sessionTimeout)
    if expireTime < expectedForegroundExpiration {
      return expireTime
    } else {
      return expectedForegroundExpiration
    }
  }

  /// The total duration the session was active (only available for expired sessions).
  public var duration: TimeInterval? {
    guard let endTime = endTime else { return nil }
    return endTime.timeIntervalSince(startTime)
  }
}
