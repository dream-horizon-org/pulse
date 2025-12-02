import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk
import OpenTelemetryProtocolExporterHttp
import StdoutExporter
import ResourceExtension
import Sessions
import URLSessionInstrumentation
import NetworkStatus
import SignPostIntegration

public class PulseSDK {
    public static let shared = PulseSDK()
    
    private var isInitialized = false
    private var openTelemetry: OpenTelemetry?
    
    private lazy var logger: Logger = {
        guard let otel = openTelemetry else {
            fatalError("Pulse SDK is not initialized. Please call PulseSDK.initialize")
        }
        return otel.loggerProvider.get(instrumentationScopeName: "com.pulse.ios.sdk")
    }()
    
    private lazy var tracer: Tracer = {
        guard let otel = openTelemetry else {
            fatalError("Pulse SDK is not initialized. Please call PulseSDK.initialize")
        }
        return otel.tracerProvider.get(instrumentationName: "com.pulse.ios.sdk", instrumentationVersion: "1.0.0")
    }()
    
    private init() {}
    
    public func initialize(
        endpointBaseUrl: String,
        globalAttributes: [String: String]? = nil,
        enableURLSession: Bool = true,
        enableSessions: Bool = true,
        enableNetworkStatus: Bool = true,
        enableSignPost: Bool = true
    ) {
        guard !isInitialized else {
            print("PulseSDK: Already initialized, ignoring duplicate call")
            return
        }
        
        let resource = DefaultResources().get()
        var resourceAttributes = resource.attributes
        if let globalAttributes = globalAttributes {
            for (key, value) in globalAttributes {
                resourceAttributes[key] = AttributeValue.string(value)
            }
        }
        let finalResource = Resource(attributes: resourceAttributes)
        
        let tracesEndpoint = URL(string: "\(endpointBaseUrl)/v1/traces")!
        let logsEndpoint = URL(string: "\(endpointBaseUrl)/v1/logs")!
        let otlpHttpTraceExporter = OtlpHttpTraceExporter(endpoint: tracesEndpoint)
        let otlpHttpLogExporter = OtlpHttpLogExporter(endpoint: logsEndpoint)
        let stdoutSpanExporter = StdoutSpanExporter()
        let spanExporter = MultiSpanExporter(spanExporters: [otlpHttpTraceExporter, stdoutSpanExporter])
        
        let spanProcessor = SimpleSpanProcessor(spanExporter: spanExporter)
        let baseLogProcessor = SimpleLogRecordProcessor(logRecordExporter: otlpHttpLogExporter)
        
        var finalSpanProcessors: [SpanProcessor] = [spanProcessor]
        var finalLogProcessors: [LogRecordProcessor]
        
        if enableSessions {
            let sessionSpanProcessor = SessionSpanProcessor()
            finalSpanProcessors.append(sessionSpanProcessor)
            
            // SessionLogRecordProcessor wraps baseLogProcessor, so we only add the wrapper
            let sessionLogProcessor = SessionLogRecordProcessor(nextProcessor: baseLogProcessor)
            finalLogProcessors = [sessionLogProcessor]
        } else {
            finalLogProcessors = [baseLogProcessor]
        }
        
        // Step 5: Tracer Provider
        var tracerProviderBuilder = TracerProviderBuilder()
            .with(resource: finalResource)
        
        for processor in finalSpanProcessors {
            tracerProviderBuilder = tracerProviderBuilder.add(spanProcessor: processor)
        }
        
        let tracerProvider = tracerProviderBuilder.build()
        
        // Step 6: Logger Provider
        let loggerProvider = LoggerProviderBuilder()
            .with(resource: finalResource)
            .with(processors: finalLogProcessors)
            .build()
        
        // Step 7: Register providers
        OpenTelemetry.registerTracerProvider(tracerProvider: tracerProvider)
        OpenTelemetry.registerLoggerProvider(loggerProvider: loggerProvider)
        
        // Step 8: Initialize SessionEventInstrumentation if enabled
        if enableSessions {
            _ = SessionEventInstrumentation()
        }
        
        // Step 9: Initialize URLSession instrumentation if enabled
        if enableURLSession {
            // Exclude OTLP exporter requests to prevent recursive spans
            let configuration = URLSessionInstrumentationConfiguration(
                shouldInstrument: { request in
                    guard let url = request.url?.absoluteString else { return true }
                    // Don't instrument requests to the OTLP endpoint
                    if url.contains(endpointBaseUrl) {
                        return false
                    }
                    return true
                }
            )
            _ = URLSessionInstrumentation(configuration: configuration)
        }
        
        // Step 10: Add SignPost if enabled
        if enableSignPost {
            if let tracerProviderSdk = tracerProvider as? TracerProviderSdk {
                if #available(iOS 15.0, macOS 12.0, *) {
                    tracerProviderSdk.addSpanProcessor(OSSignposterIntegration())
                } else {
                    tracerProviderSdk.addSpanProcessor(SignPostIntegration())
                }
            }
        }
        
        self.openTelemetry = OpenTelemetry.instance
        self.isInitialized = true
    }
    
    public func trackEvent(
        name: String,
        observedTimeStampInMs: Int64,
        params: [String: Any?] = [:]
    ) {
        guard isInitialized else { return }
        
        var attributes: [String: AttributeValue] = [
            PulseAttributes.pulseType: AttributeValue.string(PulseAttributes.PulseTypeValues.customEvent)
        ]
        
        for (key, value) in params {
            attributes[key] = attributeValue(from: value)
        }
        
        let observedDate = Date(timeIntervalSince1970: Double(observedTimeStampInMs) / 1000.0)
        logger.logRecordBuilder()
            .setObservedTimestamp(observedDate)
            .setBody(AttributeValue.string(name))
            .setEventName("pulse.custom_event")
            .setAttributes(attributes)
            .emit()
    }
    
    public func trackNonFatal(
        name: String,
        observedTimeStampInMs: Int64,
        params: [String: Any?] = [:]
    ) {
        guard isInitialized else { return }
        
        var attributes: [String: AttributeValue] = [
            PulseAttributes.pulseType: AttributeValue.string(PulseAttributes.PulseTypeValues.nonFatal)
        ]
        
        for (key, value) in params {
            attributes[key] = attributeValue(from: value)
        }
        
        let observedDate = Date(timeIntervalSince1970: Double(observedTimeStampInMs) / 1000.0)
        logger.logRecordBuilder()
            .setObservedTimestamp(observedDate)
            .setBody(AttributeValue.string(name))
            .setEventName("pulse.custom_non_fatal")
            .setAttributes(attributes)
            .emit()
    }
    
    public func trackNonFatal(
        error: Error,
        observedTimeStampInMs: Int64,
        params: [String: Any?] = [:]
    ) {
        guard isInitialized else { return }
        
        var attributes: [String: AttributeValue] = [
            PulseAttributes.pulseType: AttributeValue.string(PulseAttributes.PulseTypeValues.nonFatal),
            "exception.message": AttributeValue.string(error.localizedDescription),
            "exception.type": AttributeValue.string(String(describing: type(of: error)))
        ]
        
        if let nsError = error as NSError? {
            attributes["exception.stacktrace"] = AttributeValue.string(nsError.description)
        }
        
        for (key, value) in params {
            attributes[key] = attributeValue(from: value)
        }
        
        let body = error.localizedDescription.isEmpty ? "Non fatal error of type \(String(describing: type(of: error)))" : error.localizedDescription
        
        let observedDate = Date(timeIntervalSince1970: Double(observedTimeStampInMs) / 1000.0)
        logger.logRecordBuilder()
            .setObservedTimestamp(observedDate)
            .setBody(AttributeValue.string(body))
            .setEventName("pulse.custom_non_fatal")
            .setAttributes(attributes)
            .emit()
    }
    
    public func trackSpan<T>(
        spanName: String,
        params: [String: Any?] = [:],
        action: () throws -> T
    ) rethrows -> T {
        guard isInitialized else {
            return try action()
        }
        
        let span = tracer.spanBuilder(spanName: spanName).startSpan()
        defer { span.end() }
        
        for (key, value) in params {
            if let attrValue = attributeValue(from: value) {
                span.setAttribute(key: key, value: attrValue)
            }
        }
        
        return try action()
    }
    
    public func startSpan(
        spanName: String,
        params: [String: Any?] = [:]
    ) -> Span {
        let span = tracer.spanBuilder(spanName: spanName).startSpan()
        for (key, value) in params {
            if let attrValue = attributeValue(from: value) {
                span.setAttribute(key: key, value: attrValue)
            }
        }
        
        return span
    }
    
    private func attributeValue(from value: Any?) -> AttributeValue? {
        guard let value = value else { return nil }
        
        if let string = value as? String {
            return AttributeValue.string(string)
        } else if let int = value as? Int {
            return AttributeValue.int(int)
        } else if let int64 = value as? Int64 {
            return AttributeValue.int(Int(int64))
        } else if let double = value as? Double {
            return AttributeValue.double(double)
        } else if let bool = value as? Bool {
            return AttributeValue.bool(bool)
        } else {
            return AttributeValue.string(String(describing: value))
        }
    }
    
    public func getOpenTelemetry() -> OpenTelemetry? {
        return openTelemetry
    }
    
    public func isSDKInitialized() -> Bool {
        return isInitialized
    }
}
