Pod::Spec.new do |spec|
  spec.name = "PulseKit"
  spec.version = "0.0.1-beta.1"
  spec.summary = "Pulse iOS SDK - Simplified OpenTelemetry for iOS"
  spec.description = <<-DESC
    Pulse iOS SDK is a production-ready SDK for instrumenting iOS 
    applications with OpenTelemetry. Built on top of OpenTelemetry-Swift, 
    Pulse provides a unified API with sensible defaults for easy integration.
  DESC
  
  spec.homepage = "https://github.com/dream-horizon-org/pulse-ios-sdk"
  spec.documentation_url = "https://pulse.dreamhorizon.org/docs/sdk/ios"
  spec.license = { :type => "Apache 2.0", :file => "LICENSE" }
  spec.authors = { "Pulse iOS SDK" => "support@dream-horizon.org" }
  
  spec.source = { 
    :git => "https://github.com/dream-horizon-org/pulse-ios-sdk.git", 
    :tag => spec.version.to_s 
  }

  spec.source_files = [
    "Sources/PulseKit/**/*.{swift,h,m}",
    "Sources/Instrumentation/Sessions/*.swift",
    "Sources/Instrumentation/Crashes/**/*.{swift,h,m}",
    "Sources/Instrumentation/URLSession/*.swift",
    "Sources/Instrumentation/Interaction/**/*.swift",
    "Sources/Instrumentation/NetworkStatus/*.swift",
    "Sources/Instrumentation/SignPostIntegration/*.swift",
    "Sources/Instrumentation/SDKResourceExtension/**/*.swift",
    "Sources/Exporters/OpenTelemetryProtocolCommon/**/*.swift",
    "Sources/Exporters/OpenTelemetryProtocolHttp/**/*.swift",
    "Sources/Exporters/Persistence/**/*.swift",
    "Sources/Instrumentation/Location/*.swift",
    "Sources/Instrumentation/AppLifecycle/*.swift",
    "Sources/Instrumentation/UIKitTap/*.swift",
    "Sources/Instrumentation/SessionReplay/**/*.swift"
  ]

  spec.exclude_files = [
    "Sources/PulseKit/README.md",
    "Sources/PulseKit/Sampling/README.md",
    "Sources/Instrumentation/Sessions/README.md",
    "Sources/Instrumentation/Crashes/README.md",
    "Sources/Instrumentation/URLSession/README.md",
    "Sources/Instrumentation/Interaction/README.md",
    "Sources/Instrumentation/Interaction/Internal_Interaction.md",
    "Sources/Instrumentation/SignPostIntegration/README.md",
    "Sources/Exporters/Persistence/README.md",
    "Sources/Instrumentation/AppLifecycle/README.md",
    "Sources/Instrumentation/UIKitTap/README.md",
    "Sources/Instrumentation/SessionReplay/README.md"
  ]
  
  spec.swift_version = "5.10"
  spec.ios.deployment_target = "15.1"
  spec.module_name = "PulseKit"

  spec.ios.frameworks = "CoreTelephony", "CoreLocation"

  spec.dependency 'OpenTelemetry-Swift-Api', '2.2.0'
  spec.dependency 'OpenTelemetry-Swift-Sdk', '2.2.0'
  spec.dependency 'SwiftProtobuf', '1.34.1'
  spec.dependency 'KSCrash', '2.5.1'
  spec.dependency 'libwebp', '1.5.0'

  spec.resources = [
    "Scripts/PulseUploadSourcemaps/*.sh"
  ]
  spec.preserve_paths = [
    "Scripts/PulseUploadSourcemaps/**/*"
  ]

  spec.pod_target_xcconfig = { "OTHER_SWIFT_FLAGS" => "-module-name PulseKit -package-name pulse_kit" }
end
