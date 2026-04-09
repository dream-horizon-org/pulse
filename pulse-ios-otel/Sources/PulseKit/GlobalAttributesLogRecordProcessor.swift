/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// Log processor that appends global attributes to every log record.
/// 
/// Note: User properties (userId and userProperties) can be updated after initialization.
/// Static global attributes set during initialization are immutable after init.
internal class GlobalAttributesLogRecordProcessor: LogRecordProcessor {
    private weak var pulse: Pulse?
    private let nextProcessor: LogRecordProcessor

    init(pulse: Pulse, nextProcessor: LogRecordProcessor) {
        self.pulse = pulse
        self.nextProcessor = nextProcessor
    }
    
    func onEmit(logRecord: ReadableLogRecord) {
        guard let pulse = pulse else {
            nextProcessor.onEmit(logRecord: logRecord)
            return
        }
        
        var enhancedRecord = logRecord
        
        // Add static global attributes (set during initialization)
        if let globalAttributes = pulse._globalAttributes {
            for (key, value) in globalAttributes {
                enhancedRecord.setAttribute(key: key, value: value)
            }
        }

        if let aspectRatio = PulseUtils.currentViewportAspectRatio() {
            enhancedRecord.setAttribute(
                key: PulseAttributes.deviceScreenAspectRatio,
                value: .string(aspectRatio)
            )
        }
        
        // Add installation ID (persists across app launches until uninstall)
        enhancedRecord.setAttribute(
            key: PulseAttributes.appInstallationId,
            value: AttributeValue.string(pulse.installationIdManager.installationId)
        )
        
        // Add dynamic user properties (can be updated after initialization)
        if let userId = pulse.userSessionEmitter.userId {
            enhancedRecord.setAttribute(key: PulseAttributes.userId, value: AttributeValue.string(userId))
        }
        
        for (key, value) in pulse.userSessionEmitter.userProperties {
            enhancedRecord.setAttribute(key: PulseAttributes.pulseUserParameter(key), value: value)
        }
        
        nextProcessor.onEmit(logRecord: enhancedRecord)
    }
    
    func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.shutdown(explicitTimeout: explicitTimeout)
    }
    
    func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        return nextProcessor.forceFlush(explicitTimeout: explicitTimeout)
    }
}

