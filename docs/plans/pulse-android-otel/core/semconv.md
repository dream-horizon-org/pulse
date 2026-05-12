# Android — Core: Semantic conventions

## Purpose

Centralize every Pulse-specific attribute key, value enum and SDK identifier so all instrumentations emit identical wire format across mobile + web.

## Source location

- `pulse-semconv/src/main/java/com/pulse/semconv/PulseAttributes.kt`
- `pulse-semconv/src/main/java/com/pulse/semconv/PulseSessionAttributes.kt`
- `pulse-semconv/src/main/java/com/pulse/semconv/PulseDeviceAttributes.kt`
- `pulse-semconv/src/main/java/com/pulse/semconv/PulseUserAttributes.kt`
- `pulse-semconv/src/main/java/com/pulse/semconv/PulseInteractionAttributes.kt`

## Public surface

`object PulseAttributes` exposes (excerpt, see source for full list):

- `PULSE_TYPE = "pulse.type"`, `PULSE_NAME = "pulse.name"`, `PULSE_SPAN_ID = "pulse.span.id"`.
- `PROJECT_ID = "project.id"` (also used as `X-API-KEY` HTTP header).
- `TELEMETRY_SDK_NAME_KEY` re-exported from upstream.
- Click semantics: `APP_CLICK_CONTEXT`, `CLICK_TYPE`, `CLICK_RAGE_COUNT`, `CLICK_IS_RAGE`, `APP_SCREEN_COORDINATE_NX/NY`.
- Nested helpers: `ClickTypeValues`, `AppClickContext.buildContext(label)`, `PulseSdkNames`, `PulseTypeValues`, `PulseTypeValues.PULSE_NETWORK` template + `isNetworkType(type)`.

## Canonical `pulse.type` values

From `PulseAttributes.PulseTypeValues`:

| Constant | Wire value |
|---|---|
| `CUSTOM_EVENT` | `custom_event` |
| `ANR` | `device.anr` |
| `CRASH` | `device.crash` |
| `TOUCH` | `app.click` |
| `APP_START` | `app_start` |
| `SCREEN_SESSION` | `screen_session` |
| `APP_SESSION_START` | `session.start` |
| `APP_SESSION_END` | `session.end` |
| `APP_INSTALLATION_START` | `pulse.app.installation.start` |
| `SCREEN_LOAD` | `screen_load` |
| `FROZEN` | `app.jank.frozen` |
| `SLOW` | `app.jank.slow` |
| `NON_FATAL` | `non_fatal` |
| `INTERACTION` | `interaction` |
| `SESSION_REPLAY` | `session_replay` |
| `NETWORK_CHANGE` | `network.change` |
| `MEMORY` / `BATTERY` | `memory` / `battery` |

Plus an `AttributeKeyTemplate` `PULSE_NETWORK` for `network.*` subtypes; `isNetworkType(type)` recognises them.

## Canonical SDK names

`PulseSdkNames`: `pulse_android_java`, `pulse_android_rn`, `pulse_ios_swift`, `pulse_ios_rn`. Emitted as `telemetry.sdk.name`.

## Internal design

Pure Kotlin object with `@JvmField` constants for Java interop. No runtime state. AttributeKeys are constructed via `AttributeKey.stringKey/longKey/booleanKey/doubleKey` and `AttributeKeyTemplate.stringKeyTemplate` from upstream semconv.

## Dependencies

- `io.opentelemetry.api.common.AttributeKey`
- `io.opentelemetry.semconv.{AttributeKeyTemplate, TelemetryAttributes}`

## Data contracts

This module IS the data contract. Any new `pulse.type` value must be added here first; instrumentations must never inline string literals.

## Tests

- `pulse-semconv/src/test/` validates key names and the `AppClickContext.buildContext` helper.

## History / decisions

- `APP_CLICK_CONTEXT` is a structured string (`label=...`) rather than a free-form attribute set so dashboards can pattern-match cheaply.
- Network subtypes use `AttributeKeyTemplate` to allow `network.request`, `network.change`, etc., without explosion of constants.

## Rebuild recipe

1. Create the `com.pulse.semconv` package in a separate Gradle module so it can be shared between sampling, SDK, and instrumentations without cycles.
2. Mirror every value in `PulseTypeValues` against the web/iOS enums (`PulseTypeValues` in `pulse-web-otel`, `PulseAttributes.PulseTypeValues` in iOS).
3. Add new types here before consuming them in instrumentations.
