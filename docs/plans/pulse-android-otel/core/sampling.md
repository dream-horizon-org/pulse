# Android — Core: Sampling

## Purpose

Server-driven sampling: a remote config defines which signals to keep per session/scope, with errors and critical events always-on. The SDK fetches config periodically, persists it across launches, and applies it via attribute-based signal matchers.

## Source location

- `pulse-sampling/core/src/main/java/com/pulse/sampling/core/PulseSessionParser.kt`
- `pulse-sampling/core/src/main/java/com/pulse/sampling/core/PulseSignalMatcher.kt`
- `pulse-sampling/models/src/main/java/com/pulse/sampling/models/` — `PulseSdkConfig`, `PulseSamplingConfig`, `PulseFeatureConfig`, `PulseInteractionConfig`, `PulseSignalConfig`, `PulseSignalScope`, `PulseFeatureName`, `PulseSdkName`, `PulseDeviceAttributeName`, `PulseFeatureConfigSerializer`.
- `pulse-sampling/remote/src/main/java/com/pulse/sampling/remote/PulseSdkConfigRetrofitClient.kt`

## Public surface

Internal to the SDK — consumed by `PulseSDKInternal` during init. Apps do not call sampling APIs directly. Configuration is delivered as `PulseSdkConfig` JSON from the Pulse server.

## Internal design

- **Decision dimensions**
  - **Head sampling per signal**: `PulseSignalConfig` declares the signals to include for a scope (session / non-session / global). Anything outside the include list is dropped.
  - **Error always-on**: `device.crash`, `device.anr`, `non_fatal` and exception-bearing spans bypass sampling — enforced inside `PulseSignalMatcher`.
  - **Session-scoped decisions**: `PulseSessionParser` evaluates a session token against the config; once decided per session, the decision is stable until session rollover.
  - **Feature gating**: top-level features (`interaction`, `sessionReplay`, …) can be force-enabled / -disabled via `PulseFeatureConfig`.
- **Persistence**: config fetched at runtime is stored to disk; **the next launch** uses the persisted config (so cold-start telemetry isn't blocked on a network round-trip).
- **Rate limits**: governed by remote config knobs in `PulseSamplingConfig`; default behavior when config is missing is "send everything" (defensive default for first launches).

## Dependencies

- `pulse-semconv` for attribute keys.
- Retrofit (`pulse-sampling/remote`) for config fetch.
- Kotlinx serialization (`PulseFeatureConfigSerializer`).

## Data contracts

Config JSON shape mirrors `PulseSdkConfig` in `pulse-sampling/models`. Endpoint produces the same shape used by the iOS SDK (`PulseSdkConfigModels.swift`) so a single backend can drive both.

## Tests

- `pulse-sampling/core/src/test/.../PulseSamplingSignalProcessorsTest.kt`, `PulseSessionConfigParserTest.kt`, `PulseSignalsAttrSamplerTest.kt`.
- `pulse-sampling/models/src/test/.../PulseSdkConfigTest.kt` covers serializer round-trips.
- `pulse-sampling/remote/src/test/.../PulseSdkConfigRetrofitClientTest.kt`.

## History / decisions

- "Use persisted config from previous launch" pattern keeps init synchronous and StrictMode-clean.
- Errors always exempt to preserve crash-free-sessions metric integrity.

## Rebuild recipe

1. Model `PulseSdkConfig` data classes mirroring server JSON.
2. Build `PulseSignalMatcher` that, given attributes + scope, returns keep/drop.
3. Wrap matcher in a span/log processor + metric exporter (`PulseSamplingSignalProcessors`).
4. Add a Retrofit client to fetch config; persist response JSON; load on next init.
5. Bypass matcher for crash/ANR/non-fatal signal types.
