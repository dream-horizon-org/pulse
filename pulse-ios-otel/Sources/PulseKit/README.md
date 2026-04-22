# PulseKit — API Reference

PulseKit provides a unified, OpenTelemetry-backed API for instrumenting iOS applications. It wraps [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift) into a streamlined surface for common use cases while leaving the lower-level APIs accessible when needed.

> For direct OpenTelemetry access, use `getOpenTelemetry()` or import the OpenTelemetry packages directly.

---

## Contents

- [Quick Start](#quick-start)
- [Initialization](#initialization)
  - [Parameters](#parameters)
  - [`PulseKitConfiguration`](#pulsekitconfiguration-the-configuration-closure)
- [Instrumentations](#instrumentations)
  - [Summary table](#instrumentation-summary)
  - [URLSession](#urlsession-enabled-by-default)
  - [Sessions](#sessions-enabled-by-default)
  - [Crashes](#crashes-enabled-by-default)
  - [App Lifecycle](#app-lifecycle-enabled-by-default)
  - [Screen Lifecycle](#screen-lifecycle-enabled-by-default)
  - [App Startup](#app-startup-enabled-by-default)
  - [Interaction](#interaction-enabled-by-default)
  - [UIKit Tap](#uikit-tap-disabled-by-default)
  - [Location](#location-disabled-by-default)
  - [Session Replay](#session-replay-disabled-by-default)
- [Event Tracking](#event-tracking)
- [Non-Fatal Error Tracking](#non-fatal-error-tracking)
- [Span Tracking](#span-tracking)
- [Batching and Persistence](#batching-and-persistence)
- [Shutdown](#shutdown)
- [Utility Methods](#utility-methods)
- [Thread Safety](#thread-safety)
- [See Also](#see-also)

---

## Quick Start

```swift
import PulseKit

Pulse.shared.initialize(
    apiKey: "your-api-key",
    dataCollectionState: .allowed
)
```

Collector base URL, active config URL, and interaction config URL are **derived from the API key** (and optional remote SDK config), matching Android Pulse.

---

## Initialization

### `Pulse.shared.initialize(...)`

Initializes the SDK. Call this once, typically in `AppDelegate.application(_:didFinishLaunchingWithOptions:)`. Subsequent calls are ignored.

```swift
Pulse.shared.initialize(
    apiKey: "your-api-key",
    dataCollectionState: .allowed,
    globalAttributes: ["environment": .string("production")],
    configuration: { config in
        config.disableNetworkAttributes()
    },
    instrumentations: { config in
        config.urlSession { $0.setShouldInstrument { $0.url?.scheme == "https" } }
        config.sessions { $0.maxLifetime(2 * 60 * 60) }   // 2 hours
        config.uiKitTap { $0.enabled(true) }
        config.location { $0.enabled(true) }
    }
)
```

---

### Parameters

| Parameter                  | Type                                                  | Default      | Description                                                                                              |
| -------------------------- | ----------------------------------------------------- | ------------ | -------------------------------------------------------------------------------------------------------- |
| `apiKey`                   | `String`                                              | **required** | Sent as `X-API-KEY` header and as the `project.id` resource attribute; drives dev vs prod host selection |
| `dataCollectionState`      | `PulseDataCollectionConsent`                          | **required** | Initial consent state. Use `.denied` to skip initialization entirely when the user has not consented     |
| `globalAttributes`         | `[String: AttributeValue]?`                           | `nil`        | Attributes added to every span and log                                                                   |
| `resource`                 | `((inout [String: AttributeValue]) -> Void)?`         | `nil`        | Closure to add or override resource attributes                                                           |
| `configuration`            | `((inout PulseKitConfiguration) -> Void)?`            | `nil`        | Closure to configure SDK-level feature flags (screen/network/global attributes) — see below              |
| `instrumentations`         | `((inout InstrumentationConfiguration) -> Void)?`     | `nil`        | Closure to configure individual instrumentations — see [Instrumentations](#instrumentations)             |
| `beforeSendSpan`           | `BeforeSendSpanCallback?`                             | `nil`        | Called before each span is exported; return `nil` to drop                                                |
| `beforeSendLog`            | `BeforeSendLogCallback?`                              | `nil`        | Called before each log is exported; return `nil` to drop                                                 |
| `beforeSendMetric`         | `BeforeSendMetricCallback?`                           | `nil`        | Called before each metric is exported; return `nil` to drop                                              |
| `tracerProviderCustomizer` | `((TracerProviderBuilder) -> TracerProviderBuilder)?` | `nil`        | Advanced: customize the `TracerProvider` directly                                                        |
| `loggerProviderCustomizer` | `(([LogRecordProcessor]) -> [LogRecordProcessor])?`   | `nil`        | Advanced: insert or replace `LogRecordProcessor`s                                                        |

---

### `PulseKitConfiguration` (the `configuration` closure)

Controls SDK-level attribute injection. All options default to **enabled**.

```swift
configuration: { config in
    config.disableScreenAttributes()   // stop injecting screen.name on every signal
    config.disableNetworkAttributes()  // stop injecting network.type / network.subtype
    config.disableGlobalAttributes()   // stop injecting globalAttributes on every signal
}
```

| Method                       | Default | Effect                                             |
| ---------------------------- | ------- | -------------------------------------------------- |
| `disableScreenAttributes()`  | enabled | Stops adding `screen.name` to spans and logs       |
| `disableNetworkAttributes()` | enabled | Stops adding `network.type` / `network.subtype`    |
| `disableGlobalAttributes()`  | enabled | Stops injecting `globalAttributes` on every signal |

---

## Instrumentations

Configure via the `instrumentations` closure in `initialize`. All instrumentations are independent — disable or tune any without affecting others.

### Instrumentation Summary

| Instrumentation  | DSL method            | Enabled by default | Key options                                                                               |
| ---------------- | --------------------- | :----------------: | ----------------------------------------------------------------------------------------- |
| URLSession       | `urlSession { }`      |        Yes         | `setShouldInstrument`, optional `excludeOtlpEndpoints` for a non-default collector origin |
| Sessions         | `sessions { }`        |        Yes         | `maxLifetime` (4 h), `backgroundInactivityTimeout` (15 min), `shouldPersist`              |
| Crashes          | `crash { }`           |        Yes         | `enabled`                                                                                 |
| App Lifecycle    | `appLifecycle { }`    |        Yes         | `enabled`                                                                                 |
| Screen Lifecycle | `screenLifecycle { }` |        Yes         | `enabled`                                                                                 |
| App Startup      | `appStartup { }`      |        Yes         | `enabled`                                                                                 |
| Interaction      | `interaction { }`     |        Yes         | `setConfigUrl`                                                                            |
| UIKit Tap        | `uiKitTap { }`        |       **No**       | `captureContext`, `rage { }`                                                              |
| Location         | `location { }`        |       **No**       | `enabled`                                                                                 |
| Session Replay   | `sessionReplay { }`   |       **No**       | `configure { }` with full `SessionReplayConfig`                                           |

---

### URLSession _(enabled by default)_

Automatically intercepts all `URLSession` traffic and creates OpenTelemetry spans. Requests to OTLP export paths on the **same origin** as the collector base URL derived from your `apiKey` (`/v1/traces`, `/v1/logs`, `/v1/metrics`, `/session-capture`, `/vector/v1/logs`) are **not** instrumented by default, so export traffic does not create feedback spans.

```swift
config.urlSession { urlSession in
    urlSession.enabled(true)

    // Only instrument HTTPS requests
    urlSession.setShouldInstrument { request in
        request.url?.scheme == "https"
    }

    // Optional: second collector origin (defaults already use init’s collector base)
    urlSession.excludeOtlpEndpoints(baseUrl: "https://other-collector.example.com")
}
```

| Method                                      | Description                                                                                        |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `enabled(Bool)`                             | Enable / disable (default: `true`)                                                                 |
| `setShouldInstrument((URLRequest) -> Bool)` | Filter which requests to instrument. Return `false` to skip a request                              |
| `excludeOtlpEndpoints(baseUrl:)`            | Same path rules as the built-in filter, but scoped to **`baseUrl`**’s origin (secondary collector) |

> For advanced options (payload capture, custom header injection, span customization), see [URLSession Instrumentation README](../Instrumentation/URLSession/README.md).

---

### Sessions _(enabled by default)_

Manages session lifecycle and stamps every span and log with a `session.id`. Sessions expire either after a fixed maximum lifetime or after the app has been inactive in the background for a configurable timeout.

```swift
config.sessions { sessions in
    sessions.enabled(true)
    sessions.maxLifetime(2 * 60 * 60)               // 2 hours (default: 4 hours)
    sessions.backgroundInactivityTimeout(5 * 60)    // 5 min (default: 15 min)
    sessions.shouldPersist(true)                    // survive app restarts (default: false)
}
```

| Method                                       | Default        | Description                                                                           |
| -------------------------------------------- | -------------- | ------------------------------------------------------------------------------------- |
| `enabled(Bool)`                              | `true`         | Enable / disable                                                                      |
| `maxLifetime(TimeInterval?)`                 | `14400` (4 h)  | Maximum session duration regardless of activity. Pass `nil` for no fixed limit        |
| `backgroundInactivityTimeout(TimeInterval?)` | `900` (15 min) | Session expires if the app stays backgrounded longer than this. Pass `nil` to disable |
| `shouldPersist(Bool)`                        | `false`        | Persist the session ID across cold app launches                                       |

> See [Sessions Instrumentation README](../Instrumentation/Sessions/README.md) for emitted events and session ID attributes.

---

### Crashes _(enabled by default)_

KSCrash-backed crash reporter. Captures the crash at the time of the fault and emits a `device.crash` log event on the next app launch.

```swift
config.crash { crash in
    crash.enabled(true)
}
```

| Method          | Default | Description      |
| --------------- | ------- | ---------------- |
| `enabled(Bool)` | `true`  | Enable / disable |

> See [Crashes Instrumentation README](../Instrumentation/Crashes/README.md) for emitted attributes and crash types.

---

### App Lifecycle _(enabled by default)_

Emits `device.app.lifecycle` log events on foreground, background, and launch transitions.

```swift
config.appLifecycle { appLifecycle in
    appLifecycle.enabled(true)
}
```

| Method          | Default | Description      |
| --------------- | ------- | ---------------- |
| `enabled(Bool)` | `true`  | Enable / disable |

> See [App Lifecycle README](../Instrumentation/AppLifecycle/README.md) for emitted states.

---

### Screen Lifecycle _(enabled by default)_

Emits `Created`, `Restarted`, `Stopped`, and `ViewControllerSession` spans on UIViewController lifecycle transitions. Swizzles `viewDidLoad`, `viewWillAppear`, `viewDidDisappear`.

```swift
config.screenLifecycle { screenLifecycle in
    screenLifecycle.enabled(true)
}
```

| Method          | Default | Description      |
| --------------- | ------- | ---------------- |
| `enabled(Bool)` | `true`  | Enable / disable |

---

### App Startup _(enabled by default)_

Emits an `AppStart` span measuring the time from SDK initialization to the first screen appearance. Useful for tracking cold and warm launch performance.

```swift
config.appStartup { appStartup in
    appStartup.enabled(true)
}
```

| Method          | Default | Description      |
| --------------- | ------- | ---------------- |
| `enabled(Bool)` | `true`  | Enable / disable |

---

### Interaction _(enabled by default)_

Tracks server-configured multi-step user flows. Fetches interaction configs from a remote API and creates spans measuring time between configured event sequences.

```swift
config.interaction { interaction in
    interaction.enabled(true)
    interaction.setConfigUrl { "https://your-backend.com/v1/interaction-configs/" }
}
```

| Method                       | Default     | Description                           |
| ---------------------------- | ----------- | ------------------------------------- |
| `enabled(Bool)`              | `true`      | Enable / disable                      |
| `setConfigUrl(() -> String)` | SDK default | URL to fetch interaction flow configs |

> See [Interaction README](../Instrumentation/Interaction/README.md) for config payload format and flow tracking details.

---

### UIKit Tap _(disabled by default)_

Automatically intercepts user taps via `UIWindow.sendEvent` and emits `app.widget.click` log events with touch coordinates, element type, label, and rage-click detection. Opt-in because of the additional swizzling involved.

```swift
config.uiKitTap { uiKitTap in
    uiKitTap.enabled(true)

    // Extract label text from tapped views (slightly more CPU-intensive)
    uiKitTap.captureContext(true)

    // Tune rage-click detection
    uiKitTap.rage { rage in
        rage.timeWindowMs = 2000   // default: 2000 ms
        rage.rageThreshold = 3     // default: 3 taps
        rage.radiusPt = 50.0       // default: 50 pt radius
    }
}
```

| Method                 | Default   | Description                                                                                              |
| ---------------------- | --------- | -------------------------------------------------------------------------------------------------------- |
| `enabled(Bool)`        | `false`   | Enable / disable                                                                                         |
| `captureContext(Bool)` | `false`   | Extract and emit label text from the tapped view. Disable for apps with very large/deep view hierarchies |
| `rage { }`             | see below | Configure rage-click detection sensitivity                                                               |

**`RageConfig` defaults:**

| Property        | Default | Description                                                |
| --------------- | ------- | ---------------------------------------------------------- |
| `timeWindowMs`  | `2000`  | Sliding window (ms) used to count consecutive taps         |
| `rageThreshold` | `3`     | Number of taps in the window that triggers a rage event    |
| `radiusPt`      | `50.0`  | Radius (points) within which taps count as the same target |

> Note: Text input field content is never captured. SwiftUI-only screens get best-effort coverage via UIKit's internal view tree. See [UIKit Tap README](../Instrumentation/UIKitTap/README.md).

---

### Location _(disabled by default)_

Attaches geolocation attributes to spans and logs using a one-shot `CLLocationManager` fix with reverse geocoding. Cached for 1 hour. Opt-in because it requires user location permission.

**Requires** `NSLocationWhenInUseUsageDescription` in `Info.plist`.

```swift
config.location { location in
    location.enabled(true)
}
```

| Method          | Default | Description      |
| --------------- | ------- | ---------------- |
| `enabled(Bool)` | `false` | Enable / disable |

Attributes added: `geo.location.lat`, `geo.location.lon`, `geo.country.iso_code`, `geo.region.iso_code`, `geo.locality.name`, `geo.postal_code`.

> See [Location README](../Instrumentation/Location/README.md) for accuracy details and battery impact.

---

### Session Replay _(disabled by default)_

Captures screenshots at a configurable interval, applies privacy masking, and uploads batched replay data. Opt-in due to data volume and privacy implications.

```swift
config.sessionReplay { replay in
    replay.enabled(true)

    replay.configure { replayConfig in
        replayConfig.captureIntervalMs = 1000          // default: 1000 ms between frames
        replayConfig.compressionQuality = 0.3          // default: 0.3 (WebP; JPEG fallback)
        replayConfig.textAndInputPrivacy = .maskAll    // default: .maskAll
        replayConfig.imagePrivacy = .maskAll           // default: .maskAll
        replayConfig.screenshotScale = 1.0             // default: 1.0
        replayConfig.flushIntervalSeconds = 60         // default: 60 s
        replayConfig.flushAt = 10                      // default: 10 batches
        replayConfig.maxBatchSize = 50                 // default: 50 batches per flush
        replayConfig.replayEndpointBaseUrl = nil       // default: uses derived collector base from apiKey
    }
}
```

| `SessionReplayConfig` property | Default    | Description                                                                |
| ------------------------------ | ---------- | -------------------------------------------------------------------------- |
| `captureIntervalMs`            | `1000`     | Milliseconds between screenshot captures                                   |
| `compressionQuality`           | `0.3`      | WebP/JPEG quality (0.0 = smallest, 1.0 = lossless)                         |
| `textAndInputPrivacy`          | `.maskAll` | `.maskAll` · `.maskAllInputs` · `.maskSensitiveInputs`                     |
| `imagePrivacy`                 | `.maskAll` | `.maskAll` · `.maskNone`                                                   |
| `screenshotScale`              | `1.0`      | Screenshot resolution scale relative to screen scale                       |
| `flushIntervalSeconds`         | `60`       | Time-based flush interval                                                  |
| `flushAt`                      | `10`       | Flush when this many batches accumulate                                    |
| `maxBatchSize`                 | `50`       | Maximum batches included in a single flush                                 |
| `replayEndpointBaseUrl`        | `nil`      | Custom replay endpoint; uses derived collector base from `apiKey` when nil |
| `maskViewClasses`              | `[]`       | Class names to always mask (by class name string)                          |
| `unmaskViewClasses`            | `[]`       | Class names to always unmask                                               |

> UIKit-first. SwiftUI is not reliably supported. See [Session Replay README](../Instrumentation/SessionReplay/README.md).

---

## Event Tracking

### `trackEvent(name:observedTimeStampInMs:params:)`

Tracks a custom event as an OpenTelemetry log record.

```swift
Pulse.shared.trackEvent(
    name: "purchase_completed",
    observedTimeStampInMs: Int64(Date().timeIntervalSince1970 * 1000),
    params: [
        "item_id": "sku_123",
        "amount": 49.99
    ]
)
```

| Parameter               | Type             | Description         |
| ----------------------- | ---------------- | ------------------- |
| `name`                  | `String`         | Event name          |
| `observedTimeStampInMs` | `Int64`          | Epoch milliseconds  |
| `params`                | `[String: Any?]` | Optional attributes |

---

## Non-Fatal Error Tracking

### `trackNonFatal(name:observedTimeStampInMs:params:)`

Tracks a non-fatal error by name.

```swift
Pulse.shared.trackNonFatal(
    name: "api_timeout",
    observedTimeStampInMs: Int64(Date().timeIntervalSince1970 * 1000),
    params: ["endpoint": "/api/feed", "timeout_ms": 5000]
)
```

### `trackNonFatal(error:observedTimeStampInMs:params:)`

Tracks a non-fatal error from a Swift `Error` object.

```swift
do {
    try riskyOperation()
} catch {
    Pulse.shared.trackNonFatal(
        error: error,
        observedTimeStampInMs: Int64(Date().timeIntervalSince1970 * 1000),
        params: ["context": "data_load"]
    )
}
```

| Parameter               | Type               | Description                     |
| ----------------------- | ------------------ | ------------------------------- |
| `name` / `error`        | `String` / `Error` | Error identifier or Swift error |
| `observedTimeStampInMs` | `Int64`            | Epoch milliseconds              |
| `params`                | `[String: Any?]`   | Optional attributes             |

---

## Span Tracking

### `trackSpan(name:params:action:)`

Creates a span, runs the closure, ends the span automatically. Prefer this over `startSpan` when the work fits in a closure.

```swift
let result = Pulse.shared.trackSpan(
    name: "database_query",
    params: ["table": "users", "query.type": "select"]
) {
    try database.fetchUsers()
}
```

### `startSpan(name:params:)`

Starts a span manually. You must call `span.end()` when done.

```swift
let span = Pulse.shared.startSpan(
    name: "file_upload",
    params: ["file.size_bytes": fileData.count]
)
defer { span.end() }

try performUpload()
```

---

## Batching and Persistence

**Batching:** Spans and logs are buffered in memory and sent on a schedule, reducing network requests.

**Persistence:** Telemetry is written to disk before export. Data is retained across restarts and retried automatically when the network is available. Falls back to in-memory batching if disk storage cannot be created.

> See [Persistence Exporter README](../Exporters/Persistence/README.md) for batch format, file lifecycle, and performance tuning.

---

## Shutdown

### `shutdown()`

Permanently shuts down the SDK. All subsequent API calls become no-ops. Cannot be re-initialized in the same process.

**What it does:**

- Uninstalls all instrumentations
- Disables view-controller swizzling
- Flushes and shuts down span / log processors
- Deletes persisted telemetry from the Caches directory
- Clears SDK-managed `UserDefaults` keys (installation ID, session data, location cache)

Safe to call multiple times — subsequent calls are ignored.

---

## Utility Methods

### `isSDKInitialized() -> Bool`

Returns `true` if the SDK is initialized and not shut down.

```swift
guard Pulse.shared.isSDKInitialized() else { return }
Pulse.shared.trackEvent(name: "app_ready", observedTimeStampInMs: ...)
```

### `isShutdown: Bool`

Returns `true` after `shutdown()` has been called.

### `getOpenTelemetry() -> OpenTelemetry?`

Returns the underlying `OpenTelemetry` instance for direct API access. Returns `nil` if the SDK is not initialized.

```swift
if let otel = Pulse.shared.getOpenTelemetry() {
    let tracer = otel.tracerProvider.get(instrumentationName: "my-lib")
}
```

> Reach for this only when `trackEvent`, `trackSpan`, or `trackNonFatal` don't cover your use case.

---

## Thread Safety

All public SDK methods are thread-safe. The SDK uses internal dispatch queues and locks to serialize access from any thread.

---

## See Also

- [URLSession Instrumentation](../Instrumentation/URLSession/README.md)
- [Sessions Instrumentation](../Instrumentation/Sessions/README.md)
- [Crashes Instrumentation](../Instrumentation/Crashes/README.md)
- [App Lifecycle Instrumentation](../Instrumentation/AppLifecycle/README.md)
- [UIKit Tap Instrumentation](../Instrumentation/UIKitTap/README.md)
- [Interaction Instrumentation](../Instrumentation/Interaction/README.md)
- [Location Instrumentation](../Instrumentation/Location/README.md)
- [Session Replay Instrumentation](../Instrumentation/SessionReplay/README.md)
- [MetricKit Instrumentation](../Instrumentation/MetricKit/README.md)
- [Persistence Exporter](../Exporters/Persistence/README.md)
