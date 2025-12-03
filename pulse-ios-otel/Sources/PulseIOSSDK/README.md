# Pulse iOS SDK

The Pulse iOS SDK provides a simple, unified API for instrumenting iOS applications with OpenTelemetry.

## Quick Start

```swift
import PulseIOSSDK

// Initialize the SDK
PulseSDK.shared.initialize(endpointBaseUrl: "https://your-backend.com")
```

## API Reference

### Initialization

#### `initialize(endpointBaseUrl:endpointHeaders:globalAttributes:instrumentations:)`

Initializes the Pulse SDK with the specified configuration.

**Parameters:**
- `endpointBaseUrl: String` - **Required**. The base URL for the OTLP endpoint (e.g., `"https://your-backend.com"` or `"http://localhost:4318"`)
- `endpointHeaders: [String: String]?` - **Optional**. HTTP headers to include with all OTLP requests (e.g., authentication headers)
- `globalAttributes: [String: String]?` - **Optional**. Global attributes to add to all telemetry data
- `instrumentations: ((inout InstrumentationConfiguration) -> Void)?` - **Optional**. Closure to configure instrumentations using DSL syntax

**Example:**
```swift
PulseSDK.shared.initialize(
    endpointBaseUrl: "https://your-backend.com",
    endpointHeaders: [
        "Authorization": "Bearer your-token",
        "X-API-Key": "your-api-key"
    ],
    globalAttributes: [
        "app.version": "1.0.0",
        "environment": "production"
    ]
) { config in
    config.urlSession { urlSessionConfig in
        urlSessionConfig.enabled(true)
        urlSessionConfig.setShouldInstrument { request in
            return request.url?.scheme == "https"
        }
    }
    config.sessions { sessionsConfig in
        sessionsConfig.enabled(true)
    }
    config.signPost { signPostConfig in
        signPostConfig.enabled(false)
    }
}
```

**Note:** Multiple calls to `initialize()` are safe - subsequent calls are ignored.

---

### Event Tracking

#### `trackEvent(name:observedTimeStampInMs:params:)`

Tracks a custom event as a log record.

**Parameters:**
- `name: String` - The event name
- `observedTimeStampInMs: Int64` - Timestamp in milliseconds since epoch
- `params: [String: Any?]` - Optional event parameters/attributes

**Example:**
```swift
let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
PulseSDK.shared.trackEvent(
    name: "user_action",
    observedTimeStampInMs: timestamp,
    params: [
        "action_type": "button_click",
        "screen": "home",
        "user_id": "12345"
    ]
)
```

---

### Non-Fatal Error Tracking

#### `trackNonFatal(name:observedTimeStampInMs:params:)`

Tracks a non-fatal error by name.

**Parameters:**
- `name: String` - The error name/identifier
- `observedTimeStampInMs: Int64` - Timestamp in milliseconds since epoch
- `params: [String: Any?]` - Optional error parameters/attributes

**Example:**
```swift
let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
PulseSDK.shared.trackNonFatal(
    name: "api_error",
    observedTimeStampInMs: timestamp,
    params: [
        "error_code": "500",
        "endpoint": "/api/users",
        "retry_count": 3
    ]
)
```

#### `trackNonFatal(error:observedTimeStampInMs:params:)`

Tracks a non-fatal error from a Swift `Error` object.

**Parameters:**
- `error: Error` - The error to track
- `observedTimeStampInMs: Int64` - Timestamp in milliseconds since epoch
- `params: [String: Any?]` - Optional error parameters/attributes

**Example:**
```swift
do {
    let data = try JSONSerialization.jsonObject(with: jsonData)
} catch {
    let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
    PulseSDK.shared.trackNonFatal(
        error: error,
        observedTimeStampInMs: timestamp,
        params: [
            "error_source": "json_parsing",
            "screen": "main"
        ]
    )
}
```

---

### Span Tracking

#### `trackSpan(name:params:action:)`

Creates a span, executes the provided action, and automatically ends the span.

**Parameters:**
- `name: String` - The span name
- `params: [String: Any?]` - Optional span attributes
- `action: () throws -> T` - The action to execute within the span

**Returns:** The result of the action

**Example:**
```swift
let result = PulseSDK.shared.trackSpan(
    name: "database_query",
    params: [
        "query.type": "select",
        "table": "users"
    ]
) {
    // Your code here
    return try database.fetchUsers()
}
```

#### `startSpan(name:params:)`

Creates and starts a span. You must manually call `span.end()` when done.

**Parameters:**
- `name: String` - The span name
- `params: [String: Any?]` - Optional span attributes

**Returns:** A `Span` object

**Example:**
```swift
let span = PulseSDK.shared.startSpan(
    name: "api_request",
    params: ["endpoint": "/api/data"]
)
defer { span.end() }

// Your code here
try performAPICall()
```

---

### Utility Methods

#### `isSDKInitialized() -> Bool`

Returns `true` if the SDK has been initialized, `false` otherwise.

**Example:**
```swift
if PulseSDK.shared.isSDKInitialized() {
    // SDK is ready to use
}
```

#### `getOpenTelemetry() -> OpenTelemetry?`

Returns the underlying OpenTelemetry instance, or `nil` if not initialized.

**Example:**
```swift
if let otel = PulseSDK.shared.getOpenTelemetry() {
    // Access OpenTelemetry directly if needed
}
```

---

## Instrumentation Configuration

The SDK supports configuring instrumentations using a DSL syntax. All instrumentations are enabled by default.

### URLSession Instrumentation

Automatically tracks HTTP requests made via `URLSession`.

```swift
config.urlSession { urlSessionConfig in
    urlSessionConfig.enabled(true)
    urlSessionConfig.setShouldInstrument { request in
        // Only instrument HTTPS requests
        return request.url?.scheme == "https"
    }
}
```

### Sessions Instrumentation

Tracks user sessions and adds session IDs to all telemetry.

```swift
config.sessions { sessionsConfig in
    sessionsConfig.enabled(true)
}
```

### SignPost Instrumentation

Integrates with OS Signpost for performance monitoring.

```swift
config.signPost { signPostConfig in
    signPostConfig.enabled(true)
}
```

---

## Thread Safety

All SDK methods are thread-safe. The SDK uses internal synchronization to ensure safe concurrent access.

---

## See Also

- [Instrumentation Documentation](../Instrumentation/README.md)
- [URLSession Instrumentation](../Instrumentation/URLSession/README.md)
- [Sessions Instrumentation](../Instrumentation/Sessions/README.md)
- [SignPost Instrumentation](../Instrumentation/SignPostIntegration/README.md)

