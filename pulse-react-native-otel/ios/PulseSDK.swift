import Foundation
import PulseKit
import OpenTelemetryApi
import OpenTelemetrySdk

@objc(PulseSDK)
public class PulseSDK: NSObject {

    // MARK: - Swift API
  
    public static func initialize(
        endpointBaseUrl: String,
        projectId: String,
        configEndpointUrl: String? = nil,
        customEventCollectorUrl: String? = nil,
        endpointHeaders: [String: String]? = nil,
        globalAttributes: [String: AttributeValue]? = nil,
        resource: ((inout [String: AttributeValue]) -> Void)? = nil,
        configuration: ((inout PulseKitConfiguration) -> Void)? = nil,
        instrumentations: ((inout InstrumentationConfiguration) -> Void)? = nil,
        tracerProviderCustomizer: ((TracerProviderBuilder) -> TracerProviderBuilder)? = nil,
        loggerProviderCustomizer: (([LogRecordProcessor]) -> [LogRecordProcessor])? = nil
    ) {
        let convertedAttributes = globalAttributes

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

        Pulse.shared.initialize(
            endpointBaseUrl: endpointBaseUrl,
            projectId: projectId,
            configEndpointUrl: configEndpointUrl,
            customEventCollectorUrl: customEventCollectorUrl,
            endpointHeaders: endpointHeaders,
            globalAttributes: convertedAttributes,
            resource: rnResource,
            configuration: configuration,
            instrumentations: instrumentations,
            tracerProviderCustomizer: mergedTracerProviderCustomizer,
            loggerProviderCustomizer: mergedLoggerProviderCustomizer
        )
    }
  
    public func isSDKInitialized() -> Bool {
      return Pulse.shared.isSDKInitialized()
    }
  
    public func setUserId(_ id: String?) {
        Pulse.shared.setUserId(id)
    }

    public static func setUserProperty(name: String, value: AttributeValue?) {
        Pulse.shared.setUserProperty(name: name, value: value)
    }

    public static func setUserProperties(_ properties: [String: AttributeValue]) {
        let asOptional = properties.mapValues { Optional.some($0) }
        Pulse.shared.setUserProperties(asOptional)
    }

    public static func setUserProperties(_ builderAction: (inout [String: AttributeValue?]) -> Void) {
        Pulse.shared.setUserProperties(builderAction)
    }

    public static func trackEvent(
        name: String,
        observedTimeStampInMs: Double,
        params: [String: AttributeValue] = [:]
    ) {
        Pulse.shared.trackEvent(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params
        )
    }

    public static func trackNonFatal(
        name: String,
        observedTimeStampInMs: Int64,
        params: [String: AttributeValue] = [:]
    ) {
        Pulse.shared.trackNonFatal(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params
        )
    }

    public static func trackNonFatal(
        error: Error,
        observedTimeStampInMs: Int64,
        params: [String: AttributeValue] = [:]
    ) {
        Pulse.shared.trackNonFatal(
            error: error,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params
        )
    }

    public static func startSpan(
        name: String,
        params: [String: AttributeValue] = [:]
    ) -> Span {
        return Pulse.shared.startSpan(name: name, params: params)
    }

    public static func trackSpan<T>(
        name: String,
        params: [String: AttributeValue] = [:],
        action: () throws -> T
    ) rethrows -> T {
        return try Pulse.shared.trackSpan(name: name, params: params, action: action)
    }

    public static func getOpenTelemetry() -> OpenTelemetry? {
        return Pulse.shared.getOpenTelemetry()
    }

    public static func getOtelOrNull() -> OpenTelemetry? {
        return Pulse.shared.getOtelOrNull()
    }

    public static func getOtelOrThrow() -> OpenTelemetry {
        return Pulse.shared.getOtelOrThrow()
    }

    // MARK: - RN bridge only (@objc; PulseAttributeValue overloads)

    @objc public static func shutdown() -> Bool {
        guard Pulse.shared.isSDKInitialized() else { return false }
        let runShutdown = {
            Pulse.shared.shutdown()
        }
        if Thread.isMainThread {
            runShutdown()
        } else {
            DispatchQueue.main.async(execute: runShutdown)
        }
        return true
    }

    @objc(pulseSetUserId:)
    public static func setUserId(_ userId: String?) {
        Pulse.shared.setUserId(userId)
    }
  
    @objc(pulseSetUserProperty:value:)
    public static func setUserProperty(name: String, value: PulseAttributeValue?) {
        Pulse.shared.setUserProperty(name: name, value: value?.swiftValue)
    }

    @objc(pulseSetUserProperties:)
    public static func setUserProperties(_ properties: [String: PulseAttributeValue]) {
        Pulse.shared.setUserProperties(properties.toSwiftAttributes())
    }

    @objc(pulseTrackEvent:observedTimeStampInMs:params:)
    public static func trackEvent(
        name: String,
        observedTimeStampInMs: Double,
        params: [String: PulseAttributeValue]
    ) {
        Pulse.shared.trackEvent(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params.toSwiftAttributes()
        )
    }

    @objc(pulseTrackNonFatal:observedTimeStampInMs:params:)
    public static func trackNonFatal(
        name: String,
        observedTimeStampInMs: Int64,
        params: [String: PulseAttributeValue]
    ) {
        Pulse.shared.trackNonFatal(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params.toSwiftAttributes()
        )
    }
  
    @objc(pulseIsInitialized)
    public static func isSDKInitialized() -> Bool {
        return Pulse.shared.isSDKInitialized()
    }

    @objc(pulseGetAllFeatures)
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
}
