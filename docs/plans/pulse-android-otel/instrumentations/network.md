# Android — Instrumentation: Network

## Purpose

Two related responsibilities:

1. **Network state change** signals (`pulse.type=network.change`) emitted when connectivity transitions.
2. **HTTP request spans** for outgoing traffic via `HttpURLConnection`, `OkHttp3` and `OkHttp3 WebSocket`.

## Source location

- `instrumentation/network/src/main/java/io/opentelemetry/android/instrumentation/network/`:
  - `NetworkChangeInstrumentation.kt` (@AutoService).
  - `NetworkApplicationListener.kt`, `NetworkChangeAttributesExtractor.kt`.
- `instrumentation/httpurlconnection/` — bytecode-rewriting agent for `HttpURLConnection`.
- `instrumentation/okhttp3/`, `instrumentation/okhttp3-websocket/` — OkHttp interceptors.

## Public surface

DSL:

```kotlin
network { enabled(true) }
```

`HttpURLConnection` and OkHttp interceptors are registered automatically when their respective modules are on the classpath.

## Internal design

- **NetworkChange**: registers a `ConnectivityManager.NetworkCallback`. On state transitions, `NetworkChangeAttributesExtractor` derives transport type + carrier and `NetworkApplicationListener` emits a log record with `pulse.type=network.change`.
- **HTTP spans**: produced by OTel upstream HTTP client instrumentations; Pulse tags them with `pulse.type` from the `PULSE_NETWORK` `AttributeKeyTemplate` (see `PulseTypeValues.isNetworkType`).

## Dependencies

- Upstream OTel HTTP client instrumentations.
- `pulse-semconv` (`PulseTypeValues.NETWORK_CHANGE`, `PULSE_NETWORK`).

## Data contracts

- HTTP spans: standard `http.*` semconv + Pulse `pulse.type=network` (or scoped sub-value via the template).
- Network change logs: `pulse.type=network.change`, `network.connection.type`, `network.connection.subtype`.

## Tests

`instrumentation/network/src/test/`, `okhttp3/src/test/`, `httpurlconnection/src/test/`.

## History / decisions

- Network-change emitted as logs, not spans — these are events, not work intervals.

## Rebuild recipe

1. Register `NetworkCallback` from `NetworkChangeInstrumentation.install`.
2. Wire OkHttp interceptors via the OTel auto-instrumentation pattern.
3. Tag every HTTP span with `pulse.type` matching `PULSE_NETWORK`.
4. Honour `network { enabled(false) }` to skip both layers.
