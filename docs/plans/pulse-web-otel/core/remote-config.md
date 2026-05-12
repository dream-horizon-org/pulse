# core/remote-config

## 1. Purpose

Fetch the per-project `PulseSdkConfig` from the Pulse backend, normalise dashboard JSON into the internal shape, cache it in `localStorage`, and provide a synchronous default for first run.

## 2. Source location

- `pulse-web-otel/src/remote-config.ts` — `SdkConfigFetcher`, normalisers, exports
- `pulse-web-otel/src/types/remote-config.ts` — `PulseSdkConfig`, `PulseFeatureConfig`, sampling/filter types
- `pulse-web-otel/src/constants/default-sdk-config.ts` — `DEFAULT_SDK_CONFIG`

## 3. Public surface

```ts
export class SdkConfigFetcher {
  constructor(opts: { apiKey: string; endpointBaseUrl: string; ... });
  fetch(): Promise<PulseSdkConfig>;
}

export { PulseFeature } from "./types/remote-config";
export type {
  PulseSdkConfig, PulseFeatureConfig, PulseFeatureName, PulseSdkName,
  PulseSamplingConfig, PulseSessionSamplingRule,
  PulseSignalConfig, PulseSignalFilter, PulseSignalMatchCondition,
  PulseAttributesToAddEntry, PulseAttributesToDropEntry,
  PulseMetricsToAddEntry, PulseMetricsToAddTarget, PulseMetricsType,
  PulseAttributeValue,
} from "./types/remote-config";
export function normalizeSignalMatchCondition(c: PulseSignalMatchCondition): PulseSignalMatchCondition;
```

## 4. Internal design

- Cache key: `pulse_sdk_config` in `localStorage`.
- On `fetch()`:
  1. Read cached config; if fresh, resolve immediately while a background revalidate runs.
  2. POST to the config endpoint with `apiKey`; on success, normalise + store.
  3. On failure, fall back to cache, then to `DEFAULT_SDK_CONFIG`.
- Normalisation:
  - `normalizeSignalMatchCondition` upper-cases `scopes` to `LOGS | TRACES | METRICS` and renames `props[].name → key`.
  - `normalizePulseMetricsToAddTarget` recurses into nested conditions.
- `PulseFeature` keys mirror backend strings: `SESSION`, `JS_CRASH`, `NETWORK_INSTRUMENTATION`, `CLICK`, `WEB_VITALS`, `SCREEN_NAVIGATION`, `INTERACTION`, `SESSION_REPLAY`.

## 5. Dependencies

- `pulse-web-logger.ts`
- `constants/default-sdk-config.ts`

## 6. Data contracts

Drives:

- `FeatureGate` (see `core/feature-gate.md`)
- `ExportSamplingGate` via `signals.signalsToSample` (see `pipeline/sampling.md`)
- `SignalFilterProcessor` via `signals.attributesToAdd` / `attributesToDrop`
- `metrics-to-add-recorder.ts` via `signals.metricsToAdd`

## 7. Tests

- `src/__tests__/sampling-signal-match.test.ts`
- `src/__tests__/signal-filter-processor.test.ts`
- `src/__tests__/integration-simplified-init.test.ts`

## 8. History / decisions

Lowercase-vs-uppercase scope normalisation exists because the dashboard form emits lowercase while the matcher historically expected uppercase. Default config is conservative: all features `sessionSampleRate = 1`.

## 9. Rebuild recipe

1. Type the shape in `types/remote-config.ts`.
2. Ship a `DEFAULT_SDK_CONFIG` with all features enabled, empty sampling/filter arrays.
3. Implement `SdkConfigFetcher.fetch()` with stale-while-revalidate cache.
4. Run every inbound condition through `normalizeSignalMatchCondition` before storing.
