# Pulse iOS SDK

The Pulse iOS SDK provides a simple, unified API for instrumenting iOS applications with OpenTelemetry. This SDK is built on top of [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift) and provides a simplified wrapper around the OpenTelemetry APIs.

> **Note:** The Pulse SDK uses OpenTelemetry under the hood. All telemetry data follows the [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/). If you need direct access to OpenTelemetry APIs, you can use `getOpenTelemetry()` method or import the OpenTelemetry packages directly.

## Quick Start

```swift
import PulseKit

// Initialize the SDK
PulseKit.shared.initialize(endpointBaseUrl: "https://your-backend.com", apiKey: "your-api-key")
```

## API Reference

### Initialization

#### `initialize(endpointBaseUrl:apiKey:configEndpointUrl:endpointHeaders:...)`

Initializes the Pulse SDK with the specified configuration. **apiKey** is sent as the `X-API-KEY` header on all API calls (config, traces, logs); optional **endpointHeaders** are merged with it (apiKey wins for X-API-KEY).

**Parameters:**
- `endpointBaseUrl: String` - **Required**. The base URL for the OTLP endpoint (e.g., `"https://your-backend.com"` or `"http://localhost:4318"`)
- `apiKey: String` - **Required**. API key; sent as `X-API-KEY` header and as resource attribute `project.id`.
- `configEndpointUrl: String?` - **Optional**. Config API URL; when nil, defaults to `{endpointBaseUrl with port 8080}/v1/configs/active/`.
- `endpointHeaders: [String: String]?` - **Optional**. Extra HTTP headers; merged with X-API-KEY (apiKey) for all requests.
- `globalAttributes: [String: String]?` - **Optional**. Global attributes to add to all telemetry data
- `instrumentations: ((inout InstrumentationConfiguration) -> Void)?` - **Optional**. Closure to configure instrumentations using DSL syntax

**Example:**
```swift
PulseKit.shared.initialize(
    endpointBaseUrl: "https://your-backend.com",
    projectId: "your-project-id",
    endpointHeaders: [
        "Authorization": "Bearer your-token"
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

## Batching and persistence

**Batching:** Spans and logs are batched in memory and sent to the backend on a schedule. This reduces the number of network requests and improves efficiency. Batch size and timing are tuned for typical app usage.

**Persistence:** Telemetry is written to disk before export. When the device is offline or export fails, data stays on disk and is sent automatically when the network is available again. Export is only attempted when the SDK detects a usable connection, so data is preserved across app restarts. Persistence uses the app cache directory; if disk storage cannot be created, the SDK falls back to in-memory batching only.

For more detail on the persistence layer (batch format, file lifecycle, performance presets), see [Persistence Exporter](../Exporters/Persistence/README.md).

---

### Shutdown

#### `shutdown()`

Shuts down the SDK. After calling this method, all public APIs become no-ops and the SDK cannot be re-initialized in this process.

**What it does:**
- Uninstalls all instrumentations (crashes, sessions, interactions, location)
- Disables view-controller swizzling
- Flushes and shuts down batch span/log processors
- Deletes persisted telemetry from the Caches directory
- Removes all SDK-managed `UserDefaults` keys (installation ID, user ID, location cache, session data)
- Releases OpenTelemetry resources


**Note:** `shutdown()` is a one-way operation. It is safe to call multiple times — subsequent calls are ignored.

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
PulseKit.shared.trackEvent(
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
PulseKit.shared.trackNonFatal(
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
    PulseKit.shared.trackNonFatal(
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
let result = PulseKit.shared.trackSpan(
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
let span = PulseKit.shared.startSpan(
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

Returns `true` if the SDK has been initialized and has not been shut down.

**Example:**
```swift
if PulseKit.shared.isSDKInitialized() {
    // SDK is ready to use
}
```

#### `isShutdown: Bool`

Returns `true` after `shutdown()` has been called.

**Example:**
```swift
if PulseKit.shared.isShutdown {
    // SDK has been permanently shut down
}
```

#### `getOpenTelemetry() -> OpenTelemetry?`

Returns the underlying OpenTelemetry instance, or `nil` if not initialized. This allows you to access OpenTelemetry APIs directly if you need advanced features not exposed by the Pulse SDK wrapper.

**Example:**
```swift
if let otel = PulseKit.shared.getOpenTelemetry() {
    // Access OpenTelemetry APIs directly
    let tracer = otel.tracerProvider.get(instrumentationName: "my-app")
    // ... use OpenTelemetry APIs
}
```

> **Note:** Most users should use the Pulse SDK wrapper methods (`trackEvent`, `trackSpan`, etc.) instead of accessing OpenTelemetry directly. The wrapper provides a simpler, more opinionated API.

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

- [URLSession Instrumentation](../Instrumentation/URLSession/README.md)
- [Sessions Instrumentation](../Instrumentation/Sessions/README.md)
- [SignPost Instrumentation](../Instrumentation/SignPostIntegration/README.md)

