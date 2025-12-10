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
            params: properties
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
        let params = NSMutableDictionary()
        
        if let attributes = attributes {
            for (key, value) in attributes {
                params[key] = value
            }
        }
        
        params[PulseOtelConstants.ATTR_ERROR_TYPE] = errorType.isEmpty ? PulseOtelConstants.DEFAULT_ERROR_TYPE : errorType
        params[PulseOtelConstants.ATTR_ERROR_FATAL] = isFatal
        params[PulseOtelConstants.ATTR_ERROR_MESSAGE] = errorMessage
        params[PulseOtelConstants.ATTR_ERROR_STACK] = stackTrace
        params[PulseOtelConstants.ATTR_THREAD_ID] = getCurrentThreadId()
        params[PulseOtelConstants.ATTR_THREAD_NAME] = Thread.current.name ?? "unknown"
        params[PulseOtelConstants.ATTR_ERROR_SOURCE] = PulseOtelConstants.ERROR_SOURCE_JS
        
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
