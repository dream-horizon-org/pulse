/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk

public struct SessionsInstrumentationConfig {
    public private(set) var enabled: Bool = true
    public private(set) var maxLifetime: TimeInterval? = SessionConfigDefaults.maxLifetime
    public private(set) var backgroundInactivityTimeout: TimeInterval? = SessionConfigDefaults.backgroundInactivityTimeout
    public private(set) var shouldPersist: Bool = SessionConfigDefaults.shouldPersist

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }
    
    public mutating func maxLifetime(_ value: TimeInterval?) {
        self.maxLifetime = value
    }

    public mutating func backgroundInactivityTimeout(_ value: TimeInterval?) {
        self.backgroundInactivityTimeout = value
    }
    
    public mutating func shouldPersist(_ value: Bool) {
        self.shouldPersist = value
    }

    internal func createProcessors(baseLogProcessor: LogRecordProcessor) -> (
        otelSpanProcessor: SpanProcessor,
        otelLogProcessor: LogRecordProcessor
    )? {
        guard self.enabled else { return nil }
        
        let otelConfig = SessionConfig(
            backgroundInactivityTimeout: backgroundInactivityTimeout,
            maxLifetime: maxLifetime,
            shouldPersist: shouldPersist,
            startEventName: SessionConstants.sessionStartEvent,
            endEventName: SessionConstants.sessionEndEvent
        )
        let otelManager = SessionManager(configuration: otelConfig)
        // Register with SessionManagerProvider so session replay uses the same session
        SessionManagerProvider.register(sessionManager: otelManager)
        
        let otelSpanProcessor = SessionSpanProcessor(sessionManager: otelManager)
        let otelLogProcessor = SessionLogRecordProcessor(nextProcessor: baseLogProcessor, sessionManager: otelManager)
        
        return (otelSpanProcessor, otelLogProcessor)
    }
}

extension SessionsInstrumentationConfig: InstrumentationLifecycle {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        _ = SessionEventInstrumentation()
    }

    internal func uninstall() {}
}
