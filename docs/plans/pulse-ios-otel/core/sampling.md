# iOS — Core: Sampling

## Purpose

Apply server-driven sampling decisions to spans, logs and metrics. Same conceptual model as Android: scope-based signal include lists, error always-on, persisted config used at next launch.

## Source location

`Sources/PulseKit/Sampling/`:

- `PulseSdkConfigModels.swift` — `PulseSdkConfig`, feature config, signal config, scope enum.
- `PulseSdkConfigCoordinator.swift` — orchestrator: load → fetch → persist.
- `PulseSdkConfigRestProvider.swift` — networking.
- `PulseSdkConfigStorage.swift` — disk persistence.
- `PulseSamplingSignalProcessors.swift` — span/log processor wrapping `PulseSignalMatcher`.
- `PulseSignalMatcher.swift`, `PulseSessionParser.swift` — decision logic.
- `PulseSignalSelectExporter.swift` — terminal exporter that consults the matcher.
- `PulseDeviceContext.swift`, `PulseMockConfigProvider.swift`, `PulseOtelUtils.swift`.
- Feature-specific helpers: `ClickFeatureRemoteConfig.swift`, `SessionReplayConfigResolver.swift`, `SessionReplayRemoteConfig.swift`.
- `PulseMetricsToAddModels.swift` — server-pushed custom metric definitions.
- `AnyCodable.swift` — JSON helper.

## Public surface

Internal. App-visible only via `PulseKitConfiguration` overrides (mock provider for tests).

## Internal design

- **Decision dimensions** mirror Android:
  - Per-scope (`session` / `non-session` / `global`) include list of signals.
  - `PulseSignalMatcher` evaluates `pulse.type` + attributes against the active config.
  - Errors (`device.crash`, `non_fatal`, anything with `exception.*` attrs) are **always sent** — bypassed in `PulseSignalSelectExporter`.
  - `PulseSessionParser` ensures decisions are stable for the lifetime of a session token.
- **Persistence**: at init, `PulseSdkConfigStorage` loads the previously-persisted `PulseSdkConfig`. After init, `PulseSdkConfigCoordinator` fetches fresh config from the server; the response is **only used on the next launch**.
- **Feature gating**: `getEnabledFeatures()` toggles `customEventsEnabled` (used by `PulseKit.swift` to no-op `trackEvent` / `trackNonFatal`), `clickFeatureRemoteConfig`, `sessionReplayConfigResolver`.
- **Rate limiting**: governed by `PulseSdkConfig` thresholds; no client-side override.
- `_samplingSignalProcessors` is held strongly inside `Pulse` so the `weak parent` ref in `SampledSpanExporter` / `SampledLogExporter` stays valid for the SDK's lifetime.

## Dependencies

- `OpenTelemetryApi`, `OpenTelemetrySdk`.
- `Foundation` URLSession for `PulseSdkConfigRestProvider`.

## Data contracts

Wire format JSON identical to the Android `PulseSdkConfig`. Endpoint is the same Pulse backend route.

## Tests

- `Tests/PulseKitTests/Sampling/` — matcher, parser, storage, coordinator. `PulseMockConfigProvider` supplies fixtures.

## History / decisions

- Persist-now-use-next-launch keeps the cold-start path free of network I/O.
- Errors always-on regardless of config to keep crash-free metrics honest.

## Rebuild recipe

1. Model `PulseSdkConfig` to match the Android schema.
2. Implement `PulseSdkConfigStorage` using `FileManager` under the app's caches directory.
3. Implement `PulseSignalMatcher` (`pulse.type` + scope + attribute predicates).
4. Wire `PulseSamplingSignalProcessors` into the OTel pipeline before exporters.
5. Hold the processors strongly inside `Pulse` to keep weak refs alive.
