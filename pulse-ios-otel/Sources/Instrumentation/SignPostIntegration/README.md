# SignPost Integration

This integration creates `os_signpost` `begin` and `end` calls when spans are started or ended. It allows automatic integration of applications instrumented with OpenTelemetry to show their spans in a profiling app like Instruments. It also exports the `OSLog` it uses for posting so the user can add extra signpost events.

## PulseKit DSL (recommended)

When using PulseKit, enable or disable SignPost via the `instrumentations` closure. It is **enabled by default** and the SDK selects the correct integration for the running OS automatically.

```swift
Pulse.shared.initialize(
    apiKey: "your-api-key",
    dataCollectionState: .allowed,
    instrumentations: { config in
        config.signPost { $0.enabled(true) }   // default: true
    }
)
```

## Version Notice

- **iOS 15+, macOS 12+, tvOS 15+, watchOS 8+**:  
  Use **`OSSignposterIntegration`**, which utilizes the modern `OSSignposter` API for improved efficiency and compatibility.
- **Older systems**:  
  Use **`SignPostIntegration`**, which relies on the traditional `os_signpost` API.

## Usage

Add the appropriate span processor based on your deployment target:

### For iOS 15+, macOS 12+, tvOS 15+, watchOS 8+:

```swift
OpenTelemetry.instance.tracerProvider.addSpanProcessor(OSSignposterIntegration())
```

### For older systems

```swift
OpenTelemetry.instance.tracerProvider.addSpanProcessor(SignPostIntegration())
```

### Or, to select automatically at runtime:

```swift
if #available(iOS 15, macOS 12, tvOS 15, watchOS 8, *) {
    OpenTelemetry.instance.tracerProvider.addSpanProcessor(OSSignposterIntegration())
} else {
    OpenTelemetry.instance.tracerProvider.addSpanProcessor(SignPostIntegration())
}
```
