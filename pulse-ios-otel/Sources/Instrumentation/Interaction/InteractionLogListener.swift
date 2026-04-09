/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

/// LogRecordProcessor that intercepts user events and forwards them to InteractionManager
public class InteractionLogListener: LogRecordProcessor {
    /// Lazy access to interaction manager (accessed when onEmit() is called, after install())
    private var interactionManager: InteractionManager? {
        guard let instrumentation = InteractionInstrumentation.getInstance() else {
            return nil
        }
        return instrumentation.managerInstance
    }
    private let nextProcessor: LogRecordProcessor
    
    /// Events that should be added as markers to interactions
    private static let listOfEventToAddInInteraction = [
        "device.crash",
        "device.anr",
        "app.jank",
        "network.change",
        "app.screen.click"
    ]
    
    public init(nextProcessor: LogRecordProcessor) {
        self.nextProcessor = nextProcessor
    }
    
    public func onEmit(logRecord: ReadableLogRecord) {
        // Access manager lazily (will be available after install() is called)
        guard let manager = interactionManager else {
            // Manager not initialized yet - just forward to next processor
            nextProcessor.onEmit(logRecord: logRecord)
            return
        }
        
        // Check if log body is a string (custom event from Pulse.trackEvent())
        if case .string(let eventName) = logRecord.body {
            
            // Extract attributes as params
            var params: [String: Any?] = [:]
            logRecord.attributes.forEach { key, value in
                params[key] = value.description
            }
            
            // Get observed timestamp from log record
            // Use log record's observed timestamp if available, otherwise fallback to current time
            let observedTimeNanos: Int64
            if let observedTimestamp = logRecord.observedTimestamp {
                observedTimeNanos = Int64(observedTimestamp.timeIntervalSince1970 * 1_000_000_000)
            } else {
                // Fallback to current time if timestamp is missing (shouldn't happen in normal flow)
                observedTimeNanos = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
            }
            
            // Add event to interaction manager
            manager.addEvent(
                eventName: eventName,
                params: params,
                eventTimeInNano: observedTimeNanos
            )
        }
        
        // Check if this is an event that should be added as a marker
        if let eventName = logRecord.eventName,
           Self.listOfEventToAddInInteraction.contains(eventName) {
            // Create mutable copy of log record to add attributes (matches SessionLogRecordProcessor pattern)
            var enhancedRecord = logRecord
            
            // Add interaction attributes to log record
            // This enriches the log record with interaction context
            if let interactionAttrs = createInteractionAttributes(manager) {
                interactionAttrs.forEach { key, value in
                    enhancedRecord.setAttribute(key: key, value: value)
                }
            }
            
            // Get pulse type for marker event
            if let pulseTypeAttr = enhancedRecord.attributes["pulse.type"],
               case .string(let pulseType) = pulseTypeAttr {
                var params: [String: Any?] = [:]
                // Add log record UID if available
                if let uid = enhancedRecord.attributes["log.record.uid"] {
                    params["log.record.uid"] = uid.description
                }
                
                // Get observed timestamp from log record
                let observedTimeNanos: Int64
                if let observedTimestamp = enhancedRecord.observedTimestamp {
                    observedTimeNanos = Int64(observedTimestamp.timeIntervalSince1970 * 1_000_000_000)
                } else {
                    // Fallback to current time if timestamp is missing
                    observedTimeNanos = Int64(Date().timeIntervalSince1970 * 1_000_000_000)
                }
                
                manager.addMarkerEvent(
                    eventName: pulseType,
                    params: params,
                    eventTimeInNano: observedTimeNanos
                )
            }
            // Note: throws error if pulse.type is missing, but iOS silently skips
            // This is acceptable as it's more lenient and won't crash the app
            
            // Forward enhanced record (with interaction attributes) to next processor
            nextProcessor.onEmit(logRecord: enhancedRecord)
            return  // Early return since we've already forwarded the enhanced record
        }
        
        // Forward to next processor in chain (for non-marker events)
        nextProcessor.onEmit(logRecord: logRecord)
    }
    
    public func shutdown(explicitTimeout: TimeInterval?) -> ExportResult {
        return .success
    }
    
    public func forceFlush(explicitTimeout: TimeInterval?) -> ExportResult {
        return .success
    }
    
    /// Create interaction attributes from current running interactions
    private func createInteractionAttributes(_ manager: InteractionManager) -> [String: AttributeValue]? {
        let states = manager.currentStates
        let runningIds = states.compactMap { state -> String? in
            if case .ongoingMatch(_, let id, _, _) = state {
                return id
            }
            return nil
        }
        let runningNames = states.compactMap { state -> String? in
            if case .ongoingMatch(_, _, let config, _) = state {
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

