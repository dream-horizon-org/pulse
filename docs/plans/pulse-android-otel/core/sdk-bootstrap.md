# Android — Core: SDK bootstrap

## Purpose

Provide the single Kotlin entry point (`PulseSDK.INSTANCE`) that initializes OpenTelemetry RUM, wires Pulse-specific processors (global attributes, consent, sampling, beforeSend), and exposes user/event/span/non-fatal APIs.

## Source location

- `pulse-android-sdk/src/main/java/com/pulse/android/sdk/PulseSDK.kt` — public interface + companion singleton.
- `pulse-android-sdk/src/main/java/com/pulse/android/sdk/PulseSDKAdapter.kt` — adapter that delegates to internal impl.
- `pulse-android-sdk-internal/` — `PulseSDKInternal` (the actual work).
- `pulse-android-api/` — `PulseDataCollectionConsent`, `PulseBeforeSendData`.
- `android-agent/src/main/kotlin/io/opentelemetry/android/agent/OpenTelemetryRumInitializer.kt` — upstream builder used under the hood.

## Public surface

`com.pulse.android.sdk.PulseSDK` (interface), accessed as `PulseSDK.INSTANCE`:

- `initialize(application, apiKey, dataCollectionState, resource?, globalAttributes?, beforeSendData?, logLevel, instrumentations?)`
- `isInitialized(): Boolean`
- `setDataCollectionState(newState: PulseDataCollectionConsent)`
- `setUserId(id: String?)`, `setUserProperty`, `setUserProperties`
- `trackEvent(name, observedTimeStampInMs, params)`
- `trackNonFatal(name|throwable, observedTimeStampInMs, params)`
- `trackSpan(spanName, params, action)`, `startSpan(spanName, params): () -> Unit`
- `getOtelOrNull() / getOtelOrThrow(): OpenTelemetryRum`
- `shutdown()`

The DSL parameter `instrumentations: InstrumentationConfiguration.() -> Unit` (from `android-agent`) toggles `interaction`, `activity`, `fragment`, `network`, `anr`, `slowRendering`, etc.

## Internal design

1. `PulseSDK.INSTANCE` is a `lazy` Kotlin singleton creating `PulseSDKAdapter(PulseSDKInternal())`.
2. `initialize` is guarded — second invocations are ignored.
3. `PulseSDKInternal` calls `OpenTelemetryRum.builder()` (upstream), applies the user-supplied `ResourceBuilder` block + Pulse global attributes (`project.id`, `telemetry.sdk.name`), installs:
   - `BufferDelegating*` exporters synchronously (see exporter-chain).
   - Sampling signal processors (see sampling).
   - Consent processors / exporter from `pulse-android-api`.
   - `beforeSendData` hook for last-mile mutation/drop.
4. Heavy exporter wiring happens on a background executor; early telemetry is buffered.
5. `shutdown` flushes exporters, uninstalls instrumentation, marks SDK as terminal — re-init in the same process is not supported.

## Dependencies

- `projects.pulseAndroidApi`, `projects.androidAgent`, `projects.instrumentation.sessionReplay`, `projects.instrumentation.viewClick`, `projects.instrumentation.compose.click`, `projects.pulseUtils` (api).
- `projects.pulseAndroidSdkInternal` (implementation).
- `opentelemetry.api`, `opentelemetry.sdk`, alpha platform BOM.

## Data contracts

- Every emitted signal carries the resource attribute set assembled by the user `resource` block + Pulse defaults; `project.id` is mandatory and is also sent as `X-API-KEY` header.
- `pulse.type` and `pulse.name` (from `PulseAttributes`) are attached to span/log records by the relevant instrumentation.

## Tests

- `pulse-android-sdk/src/test/` covers idempotent init, consent state changes, shutdown semantics.
- `android-agent/src/test/kotlin/io/opentelemetry/android/agent/OpenTelemetryRumInitializerTest.kt` covers the underlying builder.

## History / decisions

- Public interface chosen over open class so we can swap the adapter without binary-compat breakage; `INSTANCE` is the only stable entry point.
- Consent gating is centralized at exporter level (not per-instrumentation) to keep instrumentations stateless.

## Rebuild recipe

1. Define interface `PulseSDK` with the methods above, mark `INSTANCE` `by lazy`.
2. Implement `PulseSDKInternal` that delegates to `OpenTelemetryRumInitializer.initialize(...)`.
3. Inject consent + sampling + beforeSend processors into the builder.
4. Add `shutdown` calling `openTelemetryRum.openTelemetry.sdk*.shutdown()`.
5. Wire DSL via `InstrumentationConfiguration` for per-feature toggles.
