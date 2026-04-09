/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// SpanProcessor that adds interaction attributes to spans and forwards span events to InteractionManager
internal class InteractionAttributesSpanAppender: SpanProcessor {
    private static let pulseTypeKey = "pulse.type"
    private static let pulseSpanIdKey = "pulse.span.id"
    private static let networkTypePrefix = "network"

    /// Lazy access to interaction manager (accessed when onStart/onEnd is called, after install())
    private var interactionManager: InteractionManager? {
        guard let instrumentation = InteractionInstrumentation.getInstance() else {
            return nil
        }
        return instrumentation.managerInstance
    }
    
    var isStartRequired: Bool = true
    var isEndRequired: Bool = true
    
    /// Span pulse types that should be added as events to interactions
    /// Note: Network types are checked separately via isNetworkType() to handle "network.XXX" patterns
    private static let listOfSpanPulseTypeToAddInInteraction = [
        "screen_load",
        "app_start",
        "screen_session"
    ]
    
    init() {
        // Manager will be accessed lazily when onStart/onEnd is called
    }
    
    func onStart(parentContext: SpanContext?, span: ReadableSpan) {
        // Access manager lazily (will be available after install() is called)
        guard let manager = interactionManager else {
            return
        }
        
        // Add interaction attributes to span
        if let interactionAttrs = Self.createInteractionAttributes(manager) {
            interactionAttrs.forEach { key, value in
                span.setAttribute(key: key, value: value)
            }
        }
    }
    
    func onEnd(span: any OpenTelemetrySdk.ReadableSpan) {
        // Access manager lazily (will be available after install() is called)
        guard let manager = interactionManager else {
            return
        }
        
        // Get span data to access attributes and end time
        let spanData = span.toSpanData()
        
        // Check if span has a pulse type that should be added to interactions
        if let pulseTypeAttr = spanData.attributes[Self.pulseTypeKey],
           case .string(let pulseTypeString) = pulseTypeAttr,
           Self.shouldAddToInteraction(pulseType: pulseTypeString) {
            
            var params: [String: Any?] = [:]
            params[Self.pulseSpanIdKey] = span.context.spanId.hexString
            
            let endTimeNanos = Int64(spanData.endTime.timeIntervalSince1970 * 1_000_000_000)
            
            manager.addEvent(
                eventName: pulseTypeString,
                params: params,
                eventTimeInNano: endTimeNanos
            )
        }
    }
    
    /// Check if a pulse type should be added to interactions
    /// Handles exact matches and network types (including "network.XXX" patterns)
    private static func shouldAddToInteraction(pulseType: String) -> Bool {
        if pulseType == networkTypePrefix || pulseType.hasPrefix("\(networkTypePrefix).") {
            return true
        }
        
        return listOfSpanPulseTypeToAddInInteraction.contains(pulseType)
    }
    
    func shutdown(explicitTimeout: TimeInterval?) {
        // No cleanup needed
    }
    
    func forceFlush(timeout: TimeInterval?) {
        // No cleanup needed
    }
    
    /// Create interaction attributes from current running interactions
    static func createInteractionAttributes(_ manager: InteractionManager) -> [String: AttributeValue]? {
        let states = manager.currentStates
        let runningIds = states.compactMap { state -> String? in
            if case .ongoingMatch(_, let id, _, let interaction) = state, interaction == nil {
                return id
            }
            return nil
        }
        let runningNames = states.compactMap { state -> String? in
            if case .ongoingMatch(_, _, let config, let interaction) = state, interaction == nil {
                return config.name
            }
            return nil
        }
        
        guard !runningIds.isEmpty else {
            return nil
        }
        
        // Create string array attributes
        let namesValues = runningNames.map { AttributeValue.string($0) }
        let idsValues = runningIds.map { AttributeValue.string($0) }
        
        return [
            "pulse.interaction.names": AttributeValue.array(AttributeArray(values: namesValues)),
            "pulse.interaction.ids": AttributeValue.array(AttributeArray(values: idsValues))
        ]
    }
}

