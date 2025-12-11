import Foundation
import PulseKit
import OpenTelemetryApi

@objc(PulseReactNativeOtelTracer)
public class PulseReactNativeOtelTracer: NSObject {
    
    private static var _tracer: Tracer?
    private static let tracerInitQueue = DispatchQueue(label: "com.pulse.tracer.init")
    private static var tracer: Tracer {
        return tracerInitQueue.sync {
            if _tracer == nil {
                _tracer = PulseSDK.getOtelOrThrow()
                    .tracerProvider
                    .get(instrumentationName: PulseOtelConstants.INSTRUMENTATION_SCOPE, instrumentationVersion: nil)
            }
            return _tracer!
        }
    }
    
    private static let spanStore = NSMutableDictionary()
    private static let spanStoreQueue = DispatchQueue(label: "com.pulse.spanstore")
    
    @objc(startSpan:attributes:)
    public static func startSpan(name: String, attributes: NSDictionary?) -> String {
        let span = tracer.spanBuilder(spanName: name)
            .setSpanKind(spanKind: SpanKind.internal)
            .setActive(true)
            .startSpan()
        
        if let attributes = attributes {
            applyAttributes(span: span, attributes: attributes)
        }
        
        let spanId = UUID().uuidString
        spanStoreQueue.sync {
            spanStore[spanId] = span
        }
        
        return spanId
    }
    
    @objc(endSpan:statusCode:)
    public static func endSpan(spanId: String, statusCode: String?) {
        spanStoreQueue.sync {
            guard let span = spanStore[spanId] as? Span else { return }
            
            if let statusCode = statusCode {
                let status = statusCode.uppercased()
                if status == "OK" {
                    span.status = .ok
                } else if status == "ERROR" {
                    span.status = .error(description: "")
                } else {
                    span.status = .unset
                }
            } else {
                span.status = .unset
            }
            
            span.end()
            spanStore.removeObject(forKey: spanId)
        }
    }
    
    @objc(addEvent:name:attributes:)
    public static func addEvent(spanId: String, name: String, attributes: NSDictionary?) {
        spanStoreQueue.sync {
            guard let span = spanStore[spanId] as? Span else { return }
            
            if let attributes = attributes {
                applyAttributes(span: span, attributes: attributes)
            }
            
            span.addEvent(name: name)
        }
    }
    
    @objc(setAttributes:attributes:)
    public static func setAttributes(spanId: String, attributes: NSDictionary?) {
        spanStoreQueue.sync {
            guard let span = spanStore[spanId] as? Span else { return }
            
            if let attributes = attributes {
                applyAttributes(span: span, attributes: attributes)
            }
        }
    }
    
    @objc(recordException:errorMessage:stackTrace:)
    public static func recordException(spanId: String, errorMessage: String, stackTrace: String?) {
        spanStoreQueue.sync {
            guard let span = spanStore[spanId] as? Span else { return }
            
            let error = NSError(domain: "PulseSDK", code: -1, userInfo: [
                NSLocalizedDescriptionKey: errorMessage
            ])
            
            span.recordException(error, attributes: [:])
            span.setAttribute(key: PulseOtelConstants.ATTR_ERROR_MESSAGE, value: AttributeValue.string(errorMessage))
            
            if let stackTrace = stackTrace, !stackTrace.isEmpty {
                span.setAttribute(key: PulseOtelConstants.ATTR_ERROR_STACK, value: AttributeValue.string(stackTrace))
            }
        }
    }
    
    private static func applyAttributes(span: Span, attributes: NSDictionary) {
        for (key, value) in attributes {
            guard let keyString = key as? String else { continue }
            
            if let stringValue = value as? String {
                span.setAttribute(key: keyString, value: AttributeValue.string(stringValue))
            } else if let boolValue = value as? Bool {
                span.setAttribute(key: keyString, value: AttributeValue.bool(boolValue))
            } else if let numberValue = value as? NSNumber {
                if CFGetTypeID(numberValue) == CFBooleanGetTypeID() {
                    span.setAttribute(key: keyString, value: AttributeValue.bool(numberValue.boolValue))
                } else {
                    span.setAttribute(key: keyString, value: AttributeValue.double(numberValue.doubleValue))
                }
            } else if let arrayValue = value as? NSArray {
                applyArrayAttribute(span: span, key: keyString, array: arrayValue)
            } else {
                span.setAttribute(key: keyString, value: AttributeValue.string(String(describing: value)))
            }
        }
    }
    
    private static func applyArrayAttribute(span: Span, key: String, array: NSArray) {
        if array.count == 0 {
          span.setAttribute(key: key, value: AttributeValue.array(AttributeArray(values: [])))
            return
        }
        
        if let firstElement = array.firstObject {
            if firstElement is String {
                let stringArray = array.compactMap { $0 as? String }
              let attrArray = AttributeArray(values: stringArray.map { AttributeValue.string($0) })
                span.setAttribute(key: key, value: AttributeValue.array(attrArray))
            } else if firstElement is NSNumber {
                let numberArray = array.compactMap { ($0 as? NSNumber)?.doubleValue }
              let attrArray = AttributeArray(values: numberArray.map { AttributeValue.double($0) })
                span.setAttribute(key: key, value: AttributeValue.array(attrArray))
            } else if firstElement is Bool || (firstElement as? NSNumber)?.boolValue != nil {
                let boolArray = array.compactMap { element -> Bool? in
                    if let bool = element as? Bool { return bool }
                    if let number = element as? NSNumber { return number.boolValue }
                    return nil
                }
              let attrArray = AttributeArray(values: boolArray.map { AttributeValue.bool($0) })
                span.setAttribute(key: key, value: AttributeValue.array(attrArray))
            } else {
                span.setAttribute(key: key, value: AttributeValue.string(array.description))
            }
        }
    }
}
