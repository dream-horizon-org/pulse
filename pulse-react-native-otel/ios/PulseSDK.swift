import Foundation
import PulseKit
import OpenTelemetryApi

@objc(PulseSDK)
public class PulseSDK: NSObject {
    
    @objc(initializeWithEndpointBaseUrl:endpointHeaders:globalAttributes:)
    public static func initialize(
        endpointBaseUrl: String,
        endpointHeaders: [String: String]?,
        globalAttributes: [String: String]?
    ) {
        PulseKit.shared.initialize(
            endpointBaseUrl: endpointBaseUrl,
            endpointHeaders: endpointHeaders,
            globalAttributes: globalAttributes,
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
    public static func setUserProperty(name: String, value: String?) {
        PulseKit.shared.setUserProperty(name: name, value: value)
    }
    
    @objc(setUserProperties:)
    public static func setUserProperties(_ properties: NSDictionary?) {
        let convertedProperties = convertDictionary(properties)
        PulseKit.shared.setUserProperties(convertedProperties)
    }
    
    @objc(trackEventWithName:observedTimeStampInMs:params:)
    public static func trackEvent(
        name: String,
        observedTimeStampInMs: Double,
        params: NSDictionary?
    ) {
        let convertedParams = convertDictionary(params)
        PulseKit.shared.trackEvent(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: convertedParams
        )
    }
    
    @objc(trackNonFatalWithName:observedTimeStampInMs:params:)
    public static func trackNonFatal(
        name: String,
        observedTimeStampInMs: Int64,
        params: NSDictionary?
    ) {
        let convertedParams = convertDictionary(params)
        PulseKit.shared.trackNonFatal(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: convertedParams
        )
    }
    
    static func getOtelOrThrow() -> OpenTelemetry {
        return PulseKit.shared.getOtelOrThrow()
    }
    
    private static func convertDictionary(_ dict: NSDictionary?) -> [String: Any?] {
        guard let dict = dict else { return [:] }
        var result: [String: Any?] = [:]
        for (key, value) in dict {
            if let keyString = key as? String {
                if value is NSNull {
                    result[keyString] = nil
                } else {
                    result[keyString] = value
                }
            }
        }
        return result
    }
}
