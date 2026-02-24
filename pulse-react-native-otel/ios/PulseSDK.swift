import Foundation
import PulseKit
import OpenTelemetryApi
import OpenTelemetrySdk

@objc(PulseSDK)
public class PulseSDK: NSObject {
    
    // Swift-only method (not exposed to Objective-C because closures can't be represented in ObjC)
    public static func initialize(
        endpointBaseUrl: String,
        projectId: String,
        endpointHeaders: [String: String]?,
        globalAttributes: [String: PulseAttributeValue]?,
        resource: ((inout [String: AttributeValue]) -> Void)? = nil,
        instrumentations: ((inout InstrumentationConfiguration) -> Void)? = nil,
        tracerProviderCustomizer: ((TracerProviderBuilder) -> TracerProviderBuilder)? = nil,
        loggerProviderCustomizer: (([LogRecordProcessor]) -> [LogRecordProcessor])? = nil
    ) {
        let convertedAttributes: [String: AttributeValue]? = globalAttributes?.toSwiftAttributes()
        
        let rnTracerProviderCustomizer: ((TracerProviderBuilder) -> TracerProviderBuilder) = { builder in
            return builder.add(spanProcessor: ReactNativeScreenAttributesSpanProcessor())
        }
        
        let mergedTracerProviderCustomizer: ((TracerProviderBuilder) -> TracerProviderBuilder)? = 
            if let userCustomizer = tracerProviderCustomizer {
                { builder in
                    let builderWithRn = rnTracerProviderCustomizer(builder)
                    return userCustomizer(builderWithRn)
                }
            } else {
                rnTracerProviderCustomizer
            }
        
        let rnLoggerProviderCustomizer: (([LogRecordProcessor]) -> [LogRecordProcessor]) = { processors in
            guard let lastIndex = processors.indices.last else {
                return processors
            }
            var modified = processors
            modified[lastIndex] = ReactNativeScreenAttributesLogRecordProcessor(nextProcessor: processors[lastIndex])
            return modified
        }
        
        let mergedLoggerProviderCustomizer: (([LogRecordProcessor]) -> [LogRecordProcessor])? = 
            if let userCustomizer = loggerProviderCustomizer {
                { processors in
                    let withRn = rnLoggerProviderCustomizer(processors)
                    return userCustomizer(withRn)
                }
            } else {
                rnLoggerProviderCustomizer
            }
        
        let rnResource: ((inout [String: AttributeValue]) -> Void) = { attributes in
            attributes[ResourceAttributes.telemetrySdkName.rawValue] = AttributeValue.string(PulseAttributes.PulseSdkNames.iosRn)
            resource?(&attributes)
        }
        
        PulseKit.shared.initialize(
            endpointBaseUrl: endpointBaseUrl,
            projectId: projectId,
            endpointHeaders: endpointHeaders,
            globalAttributes: convertedAttributes,
            resource: rnResource,
            instrumentations: instrumentations,
            tracerProviderCustomizer: mergedTracerProviderCustomizer,
            loggerProviderCustomizer: mergedLoggerProviderCustomizer
        )
    }
    
    @objc(initializeWithEndpointBaseUrl:projectId:endpointHeaders:globalAttributes:)
    public static func initialize(
        endpointBaseUrl: String,
        projectId: String,
        endpointHeaders: [String: String]?,
        globalAttributes: [String: PulseAttributeValue]?
    ) {
        initialize(
            endpointBaseUrl: endpointBaseUrl,
            projectId: projectId,
            endpointHeaders: endpointHeaders,
            globalAttributes: globalAttributes,
            resource: nil,
            instrumentations: nil,
            tracerProviderCustomizer: nil,
            loggerProviderCustomizer: nil
        )
    }

    @objc(initializeWithEndpointBaseUrl:projectId:)
    public static func initialize(endpointBaseUrl: String, projectId: String) {
        initialize(
            endpointBaseUrl: endpointBaseUrl,
            projectId: projectId,
            endpointHeaders: nil,
            globalAttributes: nil,
            resource: nil,
            instrumentations: nil,
            tracerProviderCustomizer: nil,
            loggerProviderCustomizer: nil
        )
    }
    
    @objc public static func isSDKInitialized() -> Bool {
        return PulseKit.shared.isSDKInitialized()
    }

    /// Returns feature flags from persisted SDK config, filtered for pulse_ios_rn.
    @objc(getAllFeatures)
    public static func getAllFeatures() -> [String: Bool]? {
        let storage = PulseSdkConfigStorage()
        guard let config = storage.load() else {
            return nil
        }
        var featureMap: [String: Bool] = [:]
        for featureConfig in config.features {
            if featureConfig.sdks.contains(.pulse_ios_rn) {
                if featureConfig.featureName == .unknown { continue }
                let featureNameStr = featureConfig.featureName.rawValue
                let isEnabled = featureConfig.sessionSampleRate > 0
                featureMap[featureNameStr] = isEnabled
            }
        }
        let requiredFeatures = [
            "rn_screen_load",
            "screen_session",
            "rn_screen_interactive",
            "network_instrumentation",
            "custom_events",
            "js_crash"
        ]
        var result: [String: Bool] = [:]
        for featureName in requiredFeatures {
            result[featureName] = featureMap[featureName] ?? false
        }
        return result
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
    
    public static func startSpan(
        name: String,
        params: [String: PulseAttributeValue] = [:]
    ) -> Span {
        return PulseKit.shared.startSpan(
            name: name,
            params: params.toSwiftAttributes()
        )
    }
    
    public static func trackSpan<T>(
        name: String,
        params: [String: PulseAttributeValue] = [:],
        action: () throws -> T
    ) rethrows -> T {
        return try PulseKit.shared.trackSpan(
            name: name,
            params: params.toSwiftAttributes(),
            action: action
        )
    }
    
    static func getOtelOrThrow() -> OpenTelemetry {
        return PulseKit.shared.getOtelOrThrow()
    }
}
