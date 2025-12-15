import Foundation
import PulseKit

@objc(PulseReactNativeOtelLogger)
public class PulseReactNativeOtelLogger: NSObject {
    
    @objc(trackEvent:observedTimeMs:properties:)
    public static func trackEvent(
        event: String,
        observedTimeMs: Double,
        properties: NSDictionary?
    ) {
        PulseSDK.trackEvent(
            name: event,
            observedTimeStampInMs: observedTimeMs,
            params: AttributeValueConverter.convert(properties)
        )
    }
    
    @objc(reportException:observedTimeMs:stackTrace:isFatal:errorType:attributes:)
    public static func reportException(
        errorMessage: String,
        observedTimeMs: Double,
        stackTrace: String,
        isFatal: Bool,
        errorType: String,
        attributes: NSDictionary?
    ) {
        var params = AttributeValueConverter.convert(attributes)
        
        params[PulseOtelConstants.ATTR_ERROR_TYPE] = PulseAttributeValue.string(errorType.isEmpty ? PulseOtelConstants.DEFAULT_ERROR_TYPE : errorType)
        params[PulseOtelConstants.ATTR_ERROR_FATAL] = PulseAttributeValue.bool(isFatal)
        params[PulseOtelConstants.ATTR_ERROR_MESSAGE] = PulseAttributeValue.string(errorMessage)
        params[PulseOtelConstants.ATTR_ERROR_STACK] = PulseAttributeValue.string(stackTrace)
        params[PulseOtelConstants.ATTR_THREAD_ID] = PulseAttributeValue.string(getCurrentThreadId())
        params[PulseOtelConstants.ATTR_THREAD_NAME] = PulseAttributeValue.string(Thread.current.name ?? "unknown")
        params[PulseOtelConstants.ATTR_ERROR_SOURCE] = PulseAttributeValue.string(PulseOtelConstants.ERROR_SOURCE_JS)
        
        PulseSDK.trackNonFatal(
            name: errorMessage,
            observedTimeStampInMs: Int64(observedTimeMs),
            params: params
        )
    }
    
    private static func getCurrentThreadId() -> String {
        return String(pthread_mach_thread_np(pthread_self()))
    }
}
