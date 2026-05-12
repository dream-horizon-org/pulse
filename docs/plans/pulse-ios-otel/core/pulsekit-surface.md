# iOS — Core: PulseKit public surface

## Purpose

Provide a single Swift entry point (`Pulse.shared`) that initializes OpenTelemetry-Swift, wires Pulse processors (global attributes, screen, network, consent, sampling, beforeSend, redaction) and exposes user/event/span/non-fatal APIs to integrating apps.

## Source location

- `Sources/PulseKit/PulseKit.swift` — `Pulse` singleton, init/shutdown, consent state, sampling integration.
- `Sources/PulseKit/PulseKitConfiguration.swift` — public configuration struct.
- `Sources/PulseKit/PulseHostConfiguration.swift` — host/endpoint config.
- `Sources/PulseKit/Consent/PulseDataCollectionConsent.swift` — `.pending / .allowed / .denied` enum.
- `Sources/PulseKit/PulseSignalProcessor.swift`, `PulseUserSessionEmitter.swift`, `PulseInstallationIdManager.swift`.
- `Sources/PulseKit/AppStartupTimer.swift`, `DiskUsageBytes.swift`, `PersistenceUtils.swift`, `PulseUtils.swift`.
- Per-instrumentation directory: `Sources/PulseKit/Instrumentation/`.

## Public surface

`Pulse` (class with `static let shared = Pulse()`):

- `initialize(...)` — accepts API key, host configuration, consent, global attributes, BeforeSend hook, instrumentation DSL.
- `setDataCollectionState(_:)`
- `setUserId`, `setUserProperty`, `setUserProperties`
- `trackEvent`, `trackNonFatal`, `trackSpan`, `startSpan`
- `shutdown()` — terminal; re-init not supported in the same process.
- `isShutdown` accessor; init state is private but every API guards on `isActive`.

Public attribute / type symbols: `PulseAttributes`, `PulseAttributeValue`, `PulseKitConfiguration`, `PulseHostConfiguration`, `PulseDataCollectionConsent`, `PulseLogLevel`.

## Internal design

1. `Pulse.shared` is the only instance. `initializationQueue` (`DispatchQueue`) serializes init/shutdown; `consentStateLock` (`NSLock`) guards consent changes.
2. `initialize` builds the OTel `LoggerProvider` / `TracerProvider` / `MeterProvider` with this pipeline:
   - **Span path**: instrumentation → `GlobalAttributesSpanProcessor` → `ScreenAttributesSpanProcessor` → `NetworkAttributesSpanProcessor` → `ConsentSpanProcessor` → `FilteringSpanExporter` → `BeforeSendSpanExporter` → sampling exporter → OTLP/persistence.
   - **Log path**: analogous with `GlobalAttributesLogRecordProcessor`, `ScreenAttributesLogRecordProcessor`, `NetworkAttributesLogRecordProcessor`, `ConsentLogProcessor`, `BeforeSendLogExporter`, sampling exporter.
   - **Metric path**: `ConsentMetricExporter` → `BeforeSendMetricExporter` → OTLP.
3. `currentSdkConfig` is loaded from disk at init (via `PulseSdkConfigStorage`). When a fresh config arrives over the network it is persisted for the **next** launch only.
4. `customEventsEnabled` is derived from `getEnabledFeatures()` of the loaded config; when `false`, `trackEvent` and `trackNonFatal` become no-ops.
5. `PulseUserSessionEmitter` produces `pulse.user.session.start` / `pulse.user.session.end` events tied to `user.id` changes.
6. `PulseInstallationIdManager` ensures a stable `app.installation.id` is attached as a resource attribute.

## Dependencies

- `OpenTelemetryApi`, `OpenTelemetrySdk`.
- `OpenTelemetryProtocolExporterHttp` (when available).
- UIKit (iOS / tvOS only).

## Data contracts

- Resource attributes: `project.id`, `telemetry.sdk.name` (one of `PulseSdkNames`), `app.installation.id`, plus user-supplied globals.
- Every signal carries `pulse.type` set by the originating instrumentation.

## Tests

- `Tests/PulseKitTests/` covers init idempotency, consent state, shutdown semantics, sampling integration.

## History / decisions

- Sampling config "use last launch's config, persist current API response for next launch" — keeps cold start fast and StrictMode-equivalent clean.
- Consent processors are positioned BEFORE BeforeSend so a denied state cannot leak into customer code.

## Rebuild recipe

1. Create `class Pulse` with `static let shared`.
2. Serialize init via a `DispatchQueue`. Refuse double-init.
3. Build the OTel providers with the processor pipeline above.
4. Load `PulseSdkConfig` from `PulseSdkConfigStorage`; expose `currentSdkConfig`.
5. Implement `setDataCollectionState` to push the new state into all three consent processors atomically.
6. Provide `trackEvent` / `trackNonFatal` / span APIs that no-op when `isActive == false`.
