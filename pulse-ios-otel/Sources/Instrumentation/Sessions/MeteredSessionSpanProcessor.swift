/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// This processor adds "pulse.metering.session.id" to all spans
public class MeteredSessionSpanProcessor: SpanProcessor {
  public var isStartRequired = true
  public var isEndRequired: Bool = false
  private var meteredManager: SessionManager

  public init(meteredManager: SessionManager) {
    self.meteredManager = meteredManager
  }

  public func onStart(parentContext: SpanContext?, span: ReadableSpan) {
    let session = meteredManager.getSession()
    span.setAttribute(key: SessionConstants.meteredId, value: session.id)
  }

  public func onEnd(span: any OpenTelemetrySdk.ReadableSpan) {
    // No action needed
  }

  public func shutdown(explicitTimeout: TimeInterval?) {
    // No cleanup needed
  }

  public func forceFlush(timeout: TimeInterval?) {
    // No action needed
  }
}
