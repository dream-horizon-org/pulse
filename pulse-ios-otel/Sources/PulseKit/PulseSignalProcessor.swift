/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

internal class PulseSignalProcessor {
    private var recordedRelevantLogEvents: [String: Int64] = [:]
    private let recordedEventsQueue = DispatchQueue(label: "com.pulse.ios.sdk.recordedEvents")
    
    /// Span processor that adds pulse.type to spans based on span name and attributes
    internal class PulseSpanTypeAttributesAppender: SpanProcessor {
        var isStartRequired: Bool = true
        var isEndRequired: Bool = false
        
        private static let appStartSpanName = "AppStart"
        //TODO: substitute deprecated attributes 
        private static let urlAttributeKeys = [
            SemanticAttributes.httpUrl.rawValue,       // "http.url"
            SemanticAttributes.httpTarget.rawValue,    // "http.target"
        ]

        private static func isNetworkSpan(_ attributes: [String: AttributeValue]) -> Bool {
            return attributes[SemanticAttributes.httpMethod.rawValue] != nil
        }

        func onStart(parentContext: SpanContext?, span: ReadableSpan) {
            let spanData = span.toSpanData()
            guard spanData.attributes[PulseAttributes.pulseType] == nil else {
                return
            }
            
            let pulseType: String?
            var attributesToSet: [String: AttributeValue] = [:]
            if PulseSpanTypeAttributesAppender.isNetworkSpan(spanData.attributes) {
                pulseType = nil
                for key in PulseSpanTypeAttributesAppender.urlAttributeKeys {
                    if let attr = spanData.attributes[key],
                       case .string(let originalValue) = attr {
                        let redacted = PulseSpanTypeAttributesAppender.normalizeUrl(originalValue)
                        if redacted != originalValue {
                            attributesToSet[key] = AttributeValue.string(redacted)
                        } 
                    }
                }
            }
            else if span.name == PulseSpanTypeAttributesAppender.appStartSpanName,
                    let startTypeAttr = spanData.attributes[PulseAttributes.startType],
                    case .string(let startType) = startTypeAttr,
                    startType == "cold" {
                // Fallback: AppStartupTimer sets pulse.type at span creation,
                // but if it arrives here without one, tag cold starts.
                pulseType = PulseAttributes.PulseTypeValues.appStart
            }
            else if span.name == "ViewControllerSession" {
                pulseType = PulseAttributes.PulseTypeValues.screenSession
            }
            else if span.name == "Created" {
                pulseType = PulseAttributes.PulseTypeValues.screenLoad
            }
            else {
                pulseType = nil
            }
            
            if let pulseType = pulseType {
                attributesToSet[PulseAttributes.pulseType] = AttributeValue.string(pulseType)
            }
            
            // Set all attributes at once if any were collected
            if !attributesToSet.isEmpty {
                span.setAttributes(attributesToSet)
            }
        }
        
        func onEnd(span: any OpenTelemetrySdk.ReadableSpan) {
        }
        
        func shutdown(explicitTimeout: TimeInterval?) {
        }
        
        func forceFlush(timeout: TimeInterval?) {
        }
        
        private static func normalizeUrl(_ originalUrl: String) -> String {
            var normalized = originalUrl.components(separatedBy: "?").first ?? originalUrl
            
            let patterns: [(pattern: String, replacement: String)] = [
                ("([0-9a-fA-F]{64})(?=/|$)", "[redacted]"),
                ("([0-9a-fA-F]{40})(?=/|$)", "[redacted]"),
                ("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?=/|$)", "[redacted]"),
                ("([0-9a-fA-F]{32})(?=/|$)", "[redacted]"),
                ("([0-9a-fA-F]{24})(?=/|$)", "[redacted]"),
                ("(\\d{3,})(?=/|$)", "[redacted]"),
                ("([A-Za-z0-9]{16,})(?=/|$)", "[redacted]")
            ]
            
            for (pattern, replacement) in patterns {
                normalized = normalized.replacingOccurrences(
                    of: pattern,
                    with: replacement,
                    options: .regularExpression
                )
            }
            
            return normalized
        }
    }
    
    internal class PulseLogTypeAttributesAppender: LogRecordProcessor {
        private let parent: PulseSignalProcessor
        private let nextProcessor: LogRecordProcessor
        
        private static let slowThresholdMicro: Double = 16.0 / 1000.0
        private static let frozenThresholdMicro: Double = 700.0 / 1000.0
        
        init(parent: PulseSignalProcessor, nextProcessor: LogRecordProcessor) {
            self.parent = parent
            self.nextProcessor = nextProcessor
        }
        
        func onEmit(logRecord: ReadableLogRecord) {
            guard logRecord.attributes[PulseAttributes.pulseType] == nil else {
                nextProcessor.onEmit(logRecord: logRecord)
                return
            }
            
            var enhancedRecord = logRecord
            let pulseType: String?
            
            if let eventName = logRecord.eventName {
                switch eventName {
                case "device.crash":
                    pulseType = PulseAttributes.PulseTypeValues.crash
                    
                case "device.anr":
                    pulseType = PulseAttributes.PulseTypeValues.anr
                    
                case "app.jank":
                    if let thresholdAttr = logRecord.attributes["app.jank.threshold"],
                       case .double(let threshold) = thresholdAttr {
                        if threshold == PulseLogTypeAttributesAppender.frozenThresholdMicro {
                            pulseType = PulseAttributes.PulseTypeValues.frozen
                        } else if threshold == PulseLogTypeAttributesAppender.slowThresholdMicro {
                            pulseType = PulseAttributes.PulseTypeValues.slow
                        } else {
                            pulseType = nil
                        }
                    } else {
                        pulseType = nil
                    }
                    
                case "app.widget.click":
                    pulseType = PulseAttributes.PulseTypeValues.appClick
                    
                case "network.change":
                    pulseType = PulseAttributes.PulseTypeValues.networkChange
                    
                case SessionConstants.sessionStartEvent:
                    pulseType = PulseAttributes.PulseTypeValues.appSessionStart
                    
                case SessionConstants.sessionEndEvent:
                    parent.recordedEventsQueue.sync {
                        parent.recordedRelevantLogEvents.removeAll()
                    }
                    pulseType = PulseAttributes.PulseTypeValues.appSessionEnd
                    
                default:
                    pulseType = eventName
                }
            } else {
                pulseType = nil
            }
            
            if let pulseType = pulseType {
                enhancedRecord.setAttribute(key: PulseAttributes.pulseType, value: AttributeValue.string(pulseType))
                
                parent.recordedEventsQueue.sync {
                    parent.recordedRelevantLogEvents[pulseType, default: 0] += 1
                }
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
    
    func createSpanProcessor() -> PulseSpanTypeAttributesAppender {
        return PulseSpanTypeAttributesAppender()
    }
    
    func createLogProcessor(nextProcessor: LogRecordProcessor) -> PulseLogTypeAttributesAppender {
        return PulseLogTypeAttributesAppender(parent: self, nextProcessor: nextProcessor)
    }
}