/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// OpenTelemetry log record processor that adds session attributes to all log records
public class SessionLogRecordProcessor: LogRecordProcessor {
  /// Reference to the session manager for retrieving current session
  private var sessionManager: SessionManager
  /// The next processor in the chain
  private var nextProcessor: LogRecordProcessor

  /// Initializes the log record processor with a session manager
  public init(nextProcessor: LogRecordProcessor, sessionManager: SessionManager? = nil) {
    self.nextProcessor = nextProcessor
    self.sessionManager = sessionManager ?? SessionManagerProvider.getInstance()
  }

  /// Called when a log record is emitted - adds session attributes and forwards to next processor
  public func onEmit(logRecord: ReadableLogRecord) {
    var enhancedRecord = logRecord

    // Skip if the record already carries session context (session lifecycle events,
    // crash reports with the original crash-time session, etc.)
    let hasSessionId = logRecord.attributes[SessionConstants.id] != nil
    if let body = logRecord.body,
       case let .string(bodyString) = body,
       bodyString == SessionConstants.sessionStartEvent || bodyString == SessionConstants.sessionEndEvent {
      // Session start/end events already have their intended session ids
    } else if hasSessionId {
      // Record was pre-populated with a specific session (e.g. crash from a prior session)
    } else {
      let session = sessionManager.getSession()
      enhancedRecord.setAttribute(key: SessionConstants.id, value: AttributeValue.string(session.id))
      if let previousId = session.previousId {
        enhancedRecord.setAttribute(key: SessionConstants.previousId, value: AttributeValue.string(previousId))
      }
    }

    nextProcessor.onEmit(logRecord: enhancedRecord)
  }

  /// Shuts down the processor - no cleanup needed
  public func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
    return .success
  }

  /// Forces a flush of any pending data - no action needed
  public func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
    return .success
  }
}