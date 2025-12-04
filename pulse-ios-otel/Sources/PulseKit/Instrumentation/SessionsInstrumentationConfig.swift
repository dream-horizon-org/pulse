/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import Sessions
import OpenTelemetrySdk

public struct SessionsInstrumentationConfig {
    public private(set) var enabled: Bool = true

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    public mutating func enabled(_ value: Bool) {
        self.enabled = value
    }

    internal func createProcessors(baseLogProcessor: LogRecordProcessor) -> (spanProcessor: SpanProcessor, logProcessor: LogRecordProcessor)? {
        guard self.enabled else { return nil }
        let sessionSpanProcessor = SessionSpanProcessor()
        let sessionLogProcessor = SessionLogRecordProcessor(nextProcessor: baseLogProcessor)
        return (sessionSpanProcessor, sessionLogProcessor)
    }
}

extension SessionsInstrumentationConfig: InstrumentationInitializer {
    internal func initialize(ctx: InstallationContext) {
        guard self.enabled else { return }
        _ = SessionEventInstrumentation()
    }
}
