# pipeline/sampling

## 1. Purpose

Apply Android-parity session sampling and signal-match rules at export time. One random `[0..1]` draw per SDK init, compared against the resolved per-signal `sessionSampleRate` from remote `PulseSdkConfig.signals.signalsToSample`; non-passing signals are dropped (or kept if they match `criticalAlwaysSend`).

## 2. Source location

- `pulse-web-otel/src/sampling/export-sampling-gate.ts` — `ExportSamplingGate`
- `pulse-web-otel/src/sampling/sampling-exporters.ts` — `SampledSpanExporter`, `SampledLogRecordExporter`, `SampledPushMetricExporter`
- `pulse-web-otel/src/sampling/metrics-to-add-apply.ts` — derived metric application
- `pulse-web-otel/src/sampling/metrics-to-add-recorder.ts` — factory of `DataRecorder`
- `pulse-web-otel/src/sampling/sanitize-instrumentation-name.ts` — OTel instrument name sanitisation
- `pulse-web-otel/src/utils/sampling-signal-match.ts` — `pulseSignalConditionMatches`, attribute matchers
- `pulse-web-otel/src/utils/session-sampling-rate.ts` — `resolveSessionSamplingRate`, `getCriticalAlwaysSendConditions`

## 3. Public surface

```ts
class ExportSamplingGate {
  constructor(init: ExportSamplingGateInit);
  shouldExport(scope: PulseSignalScope, signal: ReadableSpan | ReadableLogRecord | ResourceMetrics): boolean;
  drawnSampleRate(): number; // for diagnostics
}
```

Not exported from `src/index.ts`.

## 4. Internal design

- On construction: one random number in `[0..1)`; stored.
- `shouldExport`:
  1. Resolve `sessionSampleRate` for `(scope, signal)` using `resolveSessionSamplingRate` (walks `signalsToSample` rules, supports OR of property matchers).
  2. If `draw < rate` → keep, stamp `pulse.sampled = true`.
  3. Else, check `criticalAlwaysSend` conditions (e.g. `pulse.type = device.crash` is always kept).
  4. Else drop.
- `metrics-to-add-apply.ts` runs *before* the sampling drop so derived metrics aren't lost when their source signal is sampled out.
- `sanitizeInstrumentationName` enforces OTel instrument name rules (letters/digits/`._-/`, must start with letter, prefix `m` if not, max 255 chars).

## 5. Dependencies

- `@opentelemetry/api`, `@opentelemetry/sdk-trace-web`, `@opentelemetry/sdk-logs`, `@opentelemetry/sdk-metrics`
- `types/remote-config.ts`

## 6. Data contracts

Stamps `pulse.sampled = true` (`PulseWebSemconv.AttributeKey.PULSE_SAMPLED`) on signals that pass.

## 7. Tests

- `src/__tests__/export-sampling-gate.test.ts`
- `src/__tests__/sampling-signal-match.test.ts`

## 8. History / decisions

Canonical reference: Android `PulseSamplingSignalProcessors`. The one-draw-per-init policy ensures the entire session is consistently sampled or not — required for correct funnel analysis.

## 9. Rebuild recipe

1. Implement `pulseSignalConditionMatches` covering `props` (key/value), `scopes`, and `body` regex (for logs).
2. Implement `resolveSessionSamplingRate(scope, signal, config)` walking `signalsToSample`.
3. Implement `getCriticalAlwaysSendConditions(config)` returning the always-send predicate list.
4. Wrap each upstream exporter with the corresponding `Sampled*Exporter`.
