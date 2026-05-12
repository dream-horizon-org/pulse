# core/feature-gate

## 1. Purpose

Decide at install-time whether each instrumentation should be wired up, based on the remote `PulseSdkConfig`. Matches Android `PulseSamplingSignalProcessors.getEnabledFeatures()` — a feature is on for installation only when its `sessionSampleRate === 1`.

## 2. Source location

- `pulse-web-otel/src/feature-gate.ts` — `FeatureGate`
- `pulse-web-otel/src/types/remote-config.ts` — `PulseFeature`, `PulseFeatureName`, `PulseSdkConfig`
- `pulse-web-otel/src/instrumentation-registry.ts` — call site (`shouldInstall`)

## 3. Public surface

```ts
export class FeatureGate {
  constructor(config: PulseSdkConfig);
  isEnabled(feature: PulseFeatureName): boolean;
  getSampleRate(feature: PulseFeatureName): number; // [0..1]
}
```

`PulseFeature` enum keys include: `SESSION`, `JS_CRASH`, `NETWORK_INSTRUMENTATION`, `CLICK`, `WEB_VITALS`, `SCREEN_NAVIGATION`, `INTERACTION`, `SESSION_REPLAY`.

## 4. Internal design

```ts
isEnabled(feature) {
  const fc = config.features.find(f => f.featureName === feature);
  if (!fc) return true;                          // absent → on (local/dev)
  if (!fc.sdks.includes(SDK_NAME)) return true;  // not targeting web → on
  return fc.sessionSampleRate === 1;             // partial sampling → off
}
```

The hard `=== 1` cut-off is the Android compat rule: anything < 1 is delegated to the runtime `ExportSamplingGate` (see `pipeline/sampling.md`). The gate only controls *whether to install* an instrumentation listener at all.

`InstrumentationRegistry.shouldInstall(key)` combines two signals:

- Local `instrumentations[key].enabled` — explicit `false` is a hard kill switch.
- Remote `FeatureGate.isEnabled(featureName)` — soft gate.

Decision: `configEnabled !== false && gateEnabled` (omitted local config defers entirely to the gate).

## 5. Dependencies

- `types/remote-config.ts`
- `semconv.ts` (`FixedValue.RUM_SDK_NAME`)

## 6. Data contracts

No emitted attributes; the `signals.signalsToSample` rules separately stamp `pulse.sampled` on signals that pass through `ExportSamplingGate`.

## 7. Tests

- `src/__tests__/export-sampling-gate.test.ts`
- `src/__tests__/integration-simplified-init.test.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md` § feature gate. The "absent feature → enabled" default makes self-hosted / local-dev runs work without a remote config endpoint.

## 9. Rebuild recipe

1. Define `PulseFeature` enum aligned to backend `feature_name` strings.
2. Implement `FeatureGate.isEnabled` as the 3-line lookup above.
3. Build the `featureMap: InstrumentationKey → PulseFeatureName` in `InstrumentationRegistry`.
4. Apply the local-kill-switch precedence rule.
