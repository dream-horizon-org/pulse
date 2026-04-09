/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// This processor adds "pulse.metering.session.id" to all telemetry logs
public class MeteredSessionLogProcessor: LogRecordProcessor {
  private var meteredManager: SessionManager
  private var nextProcessor: LogRecordProcessor

  public init(nextProcessor: LogRecordProcessor, meteredManager: SessionManager) {
    self.nextProcessor = nextProcessor
    self.meteredManager = meteredManager
  }

  public func onEmit(logRecord: ReadableLogRecord) {
    var enhancedRecord = logRecord

    let hasMeteredSessionId = logRecord.attributes[SessionConstants.meteredId] != nil
    
    if !hasMeteredSessionId {
      let session = meteredManager.getSession()
      enhancedRecord.setAttribute(
        key: SessionConstants.meteredId,
        value: AttributeValue.string(session.id)
      )
    }

    nextProcessor.onEmit(logRecord: enhancedRecord)
  }

  public func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
    return .success
  }

  public func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
    return .success
  }
}
