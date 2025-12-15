import Foundation
import PulseKit
import OpenTelemetryApi

@objc(PulseSDK)
public class PulseSDK: NSObject {
    
    @objc(initializeWithEndpointBaseUrl:endpointHeaders:globalAttributes:)
    public static func initialize(
        endpointBaseUrl: String,
        endpointHeaders: [String: String]?,
        globalAttributes: [String: PulseAttributeValue]?
    ) {
        let convertedAttributes: [String: AttributeValue]? = globalAttributes?.toSwiftAttributes()
        PulseKit.shared.initialize(
            endpointBaseUrl: endpointBaseUrl,
            endpointHeaders: endpointHeaders,
            globalAttributes: convertedAttributes,
            instrumentations: nil
        )
    }
    
    @objc(initializeWithEndpointBaseUrl:)
    public static func initialize(endpointBaseUrl: String) {
        PulseKit.shared.initialize(
            endpointBaseUrl: endpointBaseUrl,
            endpointHeaders: nil,
            globalAttributes: nil,
            instrumentations: nil
        )
    }
    
    @objc public static func isSDKInitialized() -> Bool {
        return PulseKit.shared.isSDKInitialized()
    }
    
    @objc(setUserId:)
    public static func setUserId(_ userId: String?) {
        PulseKit.shared.setUserId(userId)
    }
    
    @objc(setUserProperty:value:)
    public static func setUserProperty(name: String, value: PulseAttributeValue?) {
        PulseKit.shared.setUserProperty(name: name, value: value?.swiftValue)
    }
    
    @objc(setUserProperties:)
    public static func setUserProperties(_ properties: [String: PulseAttributeValue]) {
        PulseKit.shared.setUserProperties(properties.toSwiftAttributes())
    }
    
    @objc(trackEventWithName:observedTimeStampInMs:params:)
    public static func trackEvent(
        name: String,
        observedTimeStampInMs: Double,
        params: [String: PulseAttributeValue]
    ) {
        PulseKit.shared.trackEvent(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params.toSwiftAttributes()
        )
    }
    
    @objc(trackNonFatalWithName:observedTimeStampInMs:params:)
    public static func trackNonFatal(
        name: String,
        observedTimeStampInMs: Int64,
        params: [String: PulseAttributeValue]
    ) {
        PulseKit.shared.trackNonFatal(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params.toSwiftAttributes()
        )
    }
    
    static func getOtelOrThrow() -> OpenTelemetry {
        return PulseKit.shared.getOtelOrThrow()
    }
}
