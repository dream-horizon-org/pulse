/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// Span processor that appends global attributes to every span.
/// 
/// Note: User properties (userId and userProperties) can be updated after initialization.
/// Static global attributes set during initialization are immutable after init.
internal class GlobalAttributesSpanProcessor: SpanProcessor {
    var isStartRequired: Bool = true
    var isEndRequired: Bool = false
    
    private weak var pulse: Pulse?

    init(pulse: Pulse) {
        self.pulse = pulse
    }
    
    func onStart(parentContext: SpanContext?, span: ReadableSpan) {
        guard let pulse = pulse else { return }
        
        var allAttributes: [String: AttributeValue] = [:]
        
        // Add static global attributes (set during initialization)
        if let globalAttributes = pulse._globalAttributes {
            allAttributes.merge(globalAttributes) { _, new in new }
        }
        
        // Add installation ID (persists across app launches until uninstall)
        allAttributes[PulseAttributes.appInstallationId] = AttributeValue.string(pulse.installationIdManager.installationId)
        
        // Add dynamic user properties (can be updated after initialization)
        if let userId = pulse.userSessionEmitter.userId {
            allAttributes[PulseAttributes.userId] = AttributeValue.string(userId)
        }
        
        for (key, value) in pulse.userSessionEmitter.userProperties {
            allAttributes[PulseAttributes.pulseUserParameter(key)] = value
        }
        
        // Set all attributes at once (uses extension method)
        if !allAttributes.isEmpty {
            span.setAttributes(allAttributes)
        }
    }
    
    func onEnd(span: any OpenTelemetrySdk.ReadableSpan) {
    }
    
    func shutdown(explicitTimeout: TimeInterval?) {
    }
    
    func forceFlush(timeout: TimeInterval?) {
    }
}

