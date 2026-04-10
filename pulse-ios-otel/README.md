# Pulse iOS SDK

**Monorepo:** this package is maintained at [`pulse-ios-otel/`](https://github.com/dream-horizon-org/pulse/tree/main/pulse-ios-otel) inside [dream-horizon-org/pulse](https://github.com/dream-horizon-org/pulse). CI: `.github/workflows/ios-sdk-checks.yml` on the monorepo.

## About

Pulse iOS SDK is a simplified, production-ready SDK for instrumenting iOS applications with OpenTelemetry. Built on top of [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift), Pulse provides a unified API with sensible defaults for easy integration.

> **Note:** This repository is a fork of [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift). We maintain this fork to build custom features like PulseKit while staying in sync with upstream OpenTelemetry improvements.

## Getting Started

### Using PulseKit (Recommended)

PulseKit is the recommended way to use Pulse iOS SDK. It provides a simple, unified API with automatic instrumentation and production-ready defaults.

#### Swift Package Manager

From the Pulse monorepo (or a checkout that contains `pulse-ios-otel/`), add a **path** dependency:

```swift
dependencies: [
    .package(path: "../pulse-ios-otel") // adjust relative path from your app package
]
```

Then add PulseKit to your target (the package name in `Package.swift` is `pulse-ios-sdk`):

```swift
.target(
    name: "YourApp",
    dependencies: [
        .product(name: "PulseKit", package: "pulse-ios-sdk")
    ]
)
```

For releases, consume **CocoaPods** (`PulseKit`) from the public release repo, or copy the binary XCFramework workflow described in the monorepo’s **iOS SDK — XCFramework & release PR** action.

#### Basic Usage

```swift
import PulseKit

// Initialize Pulse in your AppDelegate
Pulse.shared.initialize(
    endpointBaseUrl: "https://your-backend.com/otlp",
    apiKey: "your-api-key"
)
```

That's it! PulseKit automatically instruments your network requests, tracks sessions, and sends telemetry data to your backend.

For more details, see the [PulseKit README](Sources/PulseKit/README.md).

### Using OpenTelemetry APIs Directly

If you need direct access to OpenTelemetry APIs, you can use the underlying OpenTelemetry-Swift components:

```swift
dependencies: [
    .package(path: "../pulse-ios-otel")
]

.target(
    name: "YourApp",
    dependencies: [
        .product(name: "OpenTelemetrySdk", package: "pulse-ios-sdk")
    ]
)
```

> **Note:** This package includes the full OpenTelemetry-Swift implementation. All OpenTelemetry APIs are available if you need lower-level control.

## Documentation

- **[PulseKit Documentation](Sources/PulseKit/README.md)** - Complete guide to using PulseKit
- **[OpenTelemetry Swift Documentation](https://opentelemetry.io/docs/instrumentation/swift/)** - Official OpenTelemetry documentation
- **[OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)** - OpenTelemetry specification reference

## Features

This package provides all [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift) features plus PulseKit:

- **PulseKit**: Simplified API wrapper with one-line initialization, automatic instrumentation, and DSL configuration
- **Available Instrumentations**:
  - `URLSessionInstrumentation` - Automatic network request tracing
  - `Sessions` - Session tracking and lifecycle management
  - `SignPostIntegration` - System performance metrics via SignPost
  - `NetworkStatus` - Network connectivity monitoring
  - `MetricKitInstrumentation` - System metrics collection
  - `SDKResourceExtension` - Resource attribute enrichment
- **OpenTelemetry APIs**: Full access to OpenTelemetry-Swift APIs including Tracing (stable), Logs (beta), Metrics, and all exporters (OTLP HTTP/GRPC, Jaeger, Zipkin, Datadog, Prometheus, Stdout)
- **Future**: Direct OpenTelemetry initializer for advanced instrumentation configuration

> **Note:** PulseKit uses OTLP HTTP exporter which is production-ready. All OpenTelemetry features are available for direct use if needed.

## How to try this out?

- **PulseIOSExample** - Complete iOS app example showing PulseKit integration with all instrumentation available.

For more information about OpenTelemetry-Swift, visit:

- [OpenTelemetry-Swift Repository](https://github.com/open-telemetry/opentelemetry-swift)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/instrumentation/swift/)
- [OpenTelemetry Community](https://github.com/open-telemetry/community)

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the Apache 2.0 License, same as OpenTelemetry-Swift.
