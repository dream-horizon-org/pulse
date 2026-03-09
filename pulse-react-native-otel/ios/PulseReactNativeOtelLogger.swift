import Foundation
import PulseKit
import OpenTelemetryApi

@objc(PulseReactNativeOtelLogger)
public class PulseReactNativeOtelLogger: NSObject {

    @objc(trackEvent:observedTimeMs:properties:)
    public static func trackEvent(
        event: String,
        observedTimeMs: Double,
        properties: NSDictionary?
    ) {
        let params = AttributeValueConverter.convertToSwift(properties)
        Pulse.shared.trackEvent(
            name: event,
            observedTimeStampInMs: observedTimeMs,
            params: params
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
        var params = AttributeValueConverter.convertToSwift(attributes)
        params[PulseOtelConstants.ATTR_ERROR_TYPE] = AttributeValue.string(errorType.isEmpty ? PulseOtelConstants.DEFAULT_ERROR_TYPE : errorType)
        params[PulseOtelConstants.ATTR_ERROR_FATAL] = AttributeValue.bool(isFatal)
        params[PulseOtelConstants.ATTR_ERROR_MESSAGE] = AttributeValue.string(errorMessage)
        params[PulseOtelConstants.ATTR_ERROR_STACK] = AttributeValue.string(stackTrace)
        params[PulseOtelConstants.ATTR_THREAD_ID] = AttributeValue.string(getCurrentThreadId())
        params[PulseOtelConstants.ATTR_THREAD_NAME] = AttributeValue.string(Thread.current.name ?? "unknown")
        params[PulseOtelConstants.ATTR_ERROR_SOURCE] = AttributeValue.string(PulseOtelConstants.ERROR_SOURCE_JS)
        params[PulseAttributes.pulseType] = AttributeValue.string(isFatal ? PulseAttributes.PulseTypeValues.crash : PulseAttributes.PulseTypeValues.nonFatal)

        Pulse.shared.trackNonFatal(
            name: errorMessage,
            observedTimeStampInMs: Int64(observedTimeMs),
            params: params
        )
    }
    
    private static func getCurrentThreadId() -> String {
        return String(pthread_mach_thread_np(pthread_self()))
    }
}
