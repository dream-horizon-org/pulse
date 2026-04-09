/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

/// Manages metered session processors independently from OTEL session instrumentation
public struct MeteredSessionConfig {
    private var meteredManager: SessionManager?
    
    public init() {}
    
    /// Creates metered session manager with default internal config
    internal mutating func createMeteredManager() -> SessionManager {
        if let existing = meteredManager {
            return existing
        }
        
        let config = SessionConfig(
            backgroundInactivityTimeout: nil,
            maxLifetime: 30 * 60,
            shouldPersist: true,
            startEventName: nil,
            endEventName: nil
        )
        let manager = SessionManager(configuration: config)
        meteredManager = manager
        return manager
    }
    
    internal func createProcessors(
        baseLogProcessor: LogRecordProcessor,
        meteredManager: SessionManager
    ) -> (meteredSpanProcessor: SpanProcessor, meteredLogProcessor: LogRecordProcessor) {
        let meteredSpanProcessor = MeteredSessionSpanProcessor(meteredManager: meteredManager)
        let meteredLogProcessor = MeteredSessionLogProcessor(nextProcessor: baseLogProcessor, meteredManager: meteredManager)
        
        return (meteredSpanProcessor, meteredLogProcessor)
    }
    
    internal func addMeteredSessionHeader(to headers: [String: String]?, meteredManager: SessionManager) -> [String: String] {
        var updatedHeaders = headers ?? [:]
        let sessionId = meteredManager.getSession().id
        updatedHeaders[SessionConstants.meteredSessionIdHeader] = sessionId
        return updatedHeaders
    }
}
