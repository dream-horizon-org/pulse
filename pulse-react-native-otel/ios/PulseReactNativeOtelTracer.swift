import Foundation
import PulseKit
import OpenTelemetryApi

@objc(PulseReactNativeOtelTracer)
public class PulseReactNativeOtelTracer: NSObject {

    private static func getTracer() -> Tracer {
        if let otel = Pulse.shared.getOtelOrNull() {
            return otel.tracerProvider.get(
                instrumentationName: PulseOtelConstants.INSTRUMENTATION_SCOPE,
                instrumentationVersion: PulseOtelConstants.INSTRUMENTATION_VERSION
            )
        }
        return DefaultTracerProvider.instance.get(
            instrumentationName: PulseOtelConstants.INSTRUMENTATION_SCOPE,
            instrumentationVersion: PulseOtelConstants.INSTRUMENTATION_VERSION
        )
    }
    
    private static let spanStore = NSMutableDictionary()
    private static let spanStoreQueue = DispatchQueue(label: "com.pulse.spanstore")
    
    @objc(startSpan:inheritContext:attributes:)
    public static func startSpan(name: String, inheritContext: Bool, attributes: NSDictionary?) -> String {
        let builder = getTracer().spanBuilder(spanName: name)
            .setSpanKind(spanKind: SpanKind.internal)

        if let attributes = attributes {
            let swiftAttributes = AttributeValueConverter.convertToSwift(attributes)
            if !swiftAttributes.isEmpty {
                swiftAttributes.forEach { key, value in
                    builder.setAttribute(key: key, value: value)
                }
            }
        }

        // When active, the span will become parent of future spans
        if inheritContext {
            builder.setActive(true)
        }

        let span = builder.startSpan()
        
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
                let swiftAttributes = AttributeValueConverter.convertToSwift(attributes)
                if !swiftAttributes.isEmpty {
                    span.setAttributes(swiftAttributes)
                }
            }
            
            span.addEvent(name: name)
        }
    }
    
    @objc(setAttributes:attributes:)
    public static func setAttributes(spanId: String, attributes: NSDictionary?) {
        spanStoreQueue.sync {
            guard let span = spanStore[spanId] as? Span else { return }
            
            if let attributes = attributes {
                let swiftAttributes = AttributeValueConverter.convertToSwift(attributes)
                if !swiftAttributes.isEmpty {
                    span.setAttributes(swiftAttributes)
                }
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
            span.setAttribute(key: PulseOtelConstants.ATTR_ERROR_MESSAGE, value: OpenTelemetryApi.AttributeValue.string(errorMessage))
            
            if let stackTrace = stackTrace, !stackTrace.isEmpty {
                span.setAttribute(key: PulseOtelConstants.ATTR_ERROR_STACK, value: OpenTelemetryApi.AttributeValue.string(stackTrace))
            }
        }
    }
    
    @objc(discardSpan:)
    public static func discardSpan(spanId: String) {
        spanStoreQueue.sync {
            guard let span = spanStore[spanId] as? Span else { return }
            span.setAttribute(key: "pulse.internal", value: OpenTelemetryApi.AttributeValue.bool(true))
            span.end()
            spanStore.removeObject(forKey: spanId)
        }
    }
    
}
