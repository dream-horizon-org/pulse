import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi
import PulseKit

/**
 * Span processor that overrides screen.name with React Native screen name.
 */
internal class ReactNativeScreenAttributesSpanProcessor: SpanProcessor {
    var isStartRequired: Bool = true
    var isEndRequired: Bool = false
    
    func onStart(parentContext: SpanContext?, span: ReadableSpan) {
        if let screenName = ReactNativeScreenNameTracker.getCurrentScreenName() {
            span.setAttribute(key: PulseAttributes.screenName, value: AttributeValue.string(screenName))
        }
        if let previousScreenName = ReactNativeScreenNameTracker.getPreviousScreenName() {
            span.setAttribute(key: PulseAttributes.lastScreenName, value: AttributeValue.string(previousScreenName))
        }
    }
    
    func onEnd(span: any OpenTelemetrySdk.ReadableSpan) {
        // No-op
    }
    
    func shutdown(explicitTimeout: TimeInterval?) {
    }
    
    func forceFlush(timeout: TimeInterval?) {
    }
}

