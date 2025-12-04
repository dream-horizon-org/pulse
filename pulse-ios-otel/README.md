# <img src="https://opentelemetry.io/img/logos/opentelemetry-logo-nav.png" alt="OpenTelemetry Icon" width="45" height=""> Pulse iOS SDK

## About

**Pulse iOS SDK** is built on top of [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift) and provides a simplified, production-ready SDK for instrumenting iOS applications with OpenTelemetry.

This repository contains the Pulse iOS SDK implementation, which includes:
- **PulseKit** - A high-level wrapper API that simplifies OpenTelemetry usage
- **Instrumentation libraries** - Automatic instrumentation for common iOS frameworks
- **Exporters** - OTLP HTTP exporters for sending telemetry data
- **OpenTelemetry-Swift components** - Core OpenTelemetry functionality (built on [opentelemetry-swift-core](https://github.com/open-telemetry/opentelemetry-swift-core))

> **Note:** This SDK is built on OpenTelemetry-Swift and follows the [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/). The underlying OpenTelemetry APIs remain the same, but Pulse provides a simplified, opinionated wrapper for easier integration.

## Getting Started

### Using Pulse iOS SDK (Recommended)

For most applications, we recommend using the **PulseKit** wrapper, which provides a simplified API for common telemetry operations.

**Add the dependency in your `Package.swift`:**

```swift
dependencies: [
    .package(url: "https://github.com/your-org/pulse.git", from: "1.0.0")
]
```

**Add to your target:**

```swift
.target(
    name: "YourApp",
    dependencies: [
        .product(name: "PulseKit", package: "pulse-ios")
    ])
```

**For local development (SDK developers only):**

If you're developing the SDK itself or testing changes locally, you can reference the package by path using XcodeGen:

```yaml
# project.yml
packages:
  PulseIOS:
    path: ../path/to/pulse-ios-otel
    product: PulseKit

targets:
  YourApp:
    dependencies:
      - package: PulseIOS
        product: PulseKit
```

> **Note:** This local setup is only needed when developing the SDK itself. For production apps, use the remote package dependency shown above.

**Quick start:**

```swift
import PulseKit

// Initialize the SDK
PulseSDK.shared.initialize(endpointBaseUrl: "https://your-backend.com")

// Track events, spans, and errors
PulseSDK.shared.trackEvent(name: "user_action", ...)
```

For detailed API documentation, see [PulseKit README](Sources/PulseKit/README.md).

### Using OpenTelemetry Directly

If you need direct access to OpenTelemetry APIs, you can use the underlying OpenTelemetry-Swift components. This package includes the same OpenTelemetry APIs as the upstream [opentelemetry-swift-core](https://github.com/open-telemetry/opentelemetry-swift-core) package.

**Add the dependency:**

```swift
.package(url: "https://github.com/your-org/pulse.git", from: "1.0.0")
```

**Use OpenTelemetry APIs directly:**

```swift
.target(
    name: "YourApp",
    dependencies: [
        .product(name: "OpenTelemetryApi", package: "pulse-ios"),
        .product(name: "OpenTelemetrySdk", package: "pulse-ios")
    ])
```

> **Note:** The OpenTelemetry APIs in this package are the same as the upstream OpenTelemetry-Swift implementation. Pulse SDK is a wrapper on top of these APIs.

### CocoaPods

This package supports CocoaPods. The following pods are available:

**For Pulse SDK (Recommended):**
- `PulseKit` - The Pulse iOS SDK wrapper

**For OpenTelemetry APIs directly:**
- `OpenTelemetry-Swift-Api` - OpenTelemetry API protocols
- `OpenTelemetry-Swift-Sdk` - OpenTelemetry SDK implementation

**Most users should add:**

```ruby
pod 'PulseKit'
```

**If you need OpenTelemetry APIs directly:**

```ruby
pod 'OpenTelemetry-Swift-Sdk'  # Includes both API and SDK
# or
pod 'OpenTelemetry-Swift-Api'  # API only
```

## Documentation

### Pulse SDK Documentation

- **[PulseKit API Reference](Sources/PulseKit/README.md)** - Complete API documentation for the Pulse iOS SDK wrapper

### OpenTelemetry Documentation

For information about the underlying OpenTelemetry APIs, see the official [OpenTelemetry Swift documentation](https://opentelemetry.io/docs/instrumentation/swift/), including:

* [Manual instrumentation](https://opentelemetry.io/docs/instrumentation/swift/manual/)
* [Automatic instrumentation libraries](https://opentelemetry.io/docs/instrumentation/swift/libraries/)

## Current status

### API and SDK

Tracing and Baggage are considered stable

Logs are considered beta quality

Metrics is implemented using an outdated spec, is fully functional but will change in the future

### Supported exporters and importers

#### Traces

* Exporters: Stdout, Jaeger, Zipkin, Datadog and OpenTelemetry (OTLP) collector
* Importers: OpenTracingShim

#### Metrics

* Exporters: Prometheus, Datadog, and OpenTelemetry (OTLP) collector
* Importers: SwiftMetricsShim

#### Logs

* Exporters: OpenTelemetry (OTLP) collector

> **_NOTE:_** OTLP exporters are supported both in GRPC and HTTP, only GRPC is production ready, HTTP is still experimental

### Instrumentation libraries

The Pulse iOS SDK includes the following instrumentations:

* **[URLSession](Sources/Instrumentation/URLSession/README.md)** - Automatically tracks HTTP requests made via `URLSession`
* **[Sessions](Sources/Instrumentation/Sessions/README.md)** - Tracks user sessions and adds session IDs to all telemetry
* **[SignPost Integration](Sources/Instrumentation/SignPostIntegration/README.md)** - Integrates with OS Signpost for performance monitoring
* **NetworkStatus** - Automatically enriches HTTP spans with network connection information (wifi, cellular, carrier info). This is automatically enabled with URLSession instrumentation.
* **SDKResourceExtension** - Provides default resource attributes for iOS applications
* **[MetricKit](Sources/Instrumentation/MetricKit/README.md)** - Captures MetricKit performance metrics and diagnostics

For detailed documentation on each instrumentation, see the links above or browse the `Sources/Instrumentation/` directory.

For Pulse SDK API documentation, see [PulseKit README](Sources/PulseKit/README.md).

### Third-party exporters
In addition to the specified OpenTelemetry exporters, some third-party exporters have been contributed and can be found in the following repos: 
* [Grafana/faro](https://github.com/grafana/faro-otel-swift-exporter)

## Examples

The package includes some example projects with basic functionality:

* `Datadog Sample` -  Shows the Datadog exporter used with a Simple Exporter, showing how to configure for sending.
* `Logging Tracer` -  Simple api implementation of a Tracer that logs every api call
* `Network Tracer` -  Shows how to use the `URLSessionInstrumentation` instrumentation in your application
* `Simple Exporter` - Shows the Jaeger an Stdout exporters in action using a MultiSpanExporter. Can be easily modified for other exporters
* `Prometheus Sample` - Shows the Prometheus exporter reporting metrics to a Prometheus instance
* `OTLP Exporter` - Shows the OTLP exporter reporting traces to Zipkin and metrics to a Prometheus via the otel-collector

## Contributing

We welcome contributions! For an overview of how to contribute, see the contributing guide in [CONTRIBUTING.md](CONTRIBUTING.md).

This project is built on top of [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift). For questions about the underlying OpenTelemetry APIs, you can also refer to the [OpenTelemetry Swift community](https://github.com/open-telemetry/community#swift-sdk) and the [#otel-swift](https://cloud-native.slack.com/archives/C01NCHR19SB) channel in the [CNCF slack](https://slack.cncf.io/).
