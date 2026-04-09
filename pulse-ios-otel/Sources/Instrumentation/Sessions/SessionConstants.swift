/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

/// Constants for OpenTelemetry session instrumentation.
///
/// Provides standardized attribute names and event types following OpenTelemetry
/// semantic conventions for session tracking.
///
/// Reference: https://opentelemetry.io/docs/specs/semconv/general/session/
public class SessionConstants {
  // MARK: - OpenTelemetry Semantic Conventions
  public static let sessionStartEvent = "session.start"
  public static let sessionEndEvent = "session.end"
  public static let id = "session.id"
  public static let previousId = "session.previous_id"
  public static let meteredId = "pulse.metering.session.id"

  // MARK: - Extension Attributes
  public static let startTime = "session.start_time"
  public static let endTime = "session.end_time"
  public static let duration = "session.duration"

  // MARK: - Internal Constants
  public static let sessionEventNotification = "SessionEventInstrumentation.SessionEvent"
  
  // MARK: - HTTP Headers
  public static let meteredSessionIdHeader = "X-Pulse-Metering-Session-ID"
}
