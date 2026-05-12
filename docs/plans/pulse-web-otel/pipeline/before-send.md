# pipeline/before-send

## 1. Purpose

Host-app hook to mutate or drop signals immediately before OTLP serialisation. Mirrors Android `PulseBeforeSendData` (generic → typed, `null` = drop). Runs on the main thread inside the batch exporter.

## 2. Source location

- `pulse-web-otel/src/before-send.ts` — `resolveBeforeSend`, validators, has-X helpers
- `pulse-web-otel/src/types/before-send.ts` — `PulseBeforeSendResult`, `PulseExportSignal`, `PulseWebBeforeSendCallbacks`, `PulseWebBeforeSendConfig`, `ResolvedBeforeSend`
- `pulse-web-otel/src/exporters/before-send-exporters.ts` — `BeforeSendSpanExporter`, `BeforeSendLogRecordExporter`, `BeforeSendMetricExporter`

## 3. Public surface

```ts
// In config:
beforeSendData?: PulseWebBeforeSendConfig;
// either a single function or per-kind callbacks:
type PulseWebBeforeSendConfig =
  | ((signal: PulseExportSignal) => PulseBeforeSendResult)
  | PulseWebBeforeSendCallbacks;
type PulseBeforeSendResult = PulseExportSignal | null; // null = drop
type PulseWebBeforeSendCallbacks = {
  beforeSend?: (signal: PulseExportSignal) => PulseBeforeSendResult;
  beforeSendSpan?: (span: ReadableSpan) => ReadableSpan | null;
  beforeSendLog?: (log: ReadableLogRecord) => ReadableLogRecord | null;
  beforeSendMetric?: (m: ResourceMetrics) => ResourceMetrics | null;
};
```

`resolveBeforeSend(input)` normalises either form into a `ResolvedBeforeSend`. `hasBeforeSendForSpans/Logs/Metrics` decide whether to insert the wrapper exporter at all.

## 4. Internal design

- A single function form is broadcast to all three kinds.
- A callbacks form lets the user opt into per-kind hooks; the generic `beforeSend` (if present) runs first, then the kind-specific one.
- Returning `null` from any hook drops the signal silently.
- Validation (`validateBeforeSendConfig`) runs at `validateConfig` time so misconfigured hooks fail fast.

## 5. Dependencies

- `@opentelemetry/sdk-trace-web`, `@opentelemetry/sdk-logs`, `@opentelemetry/sdk-metrics`
- `exporters.ts` (wires the wrapper exporters)

## 6. Data contracts

No new attributes — hook implementers may mutate any attribute. The processor must not depend on `beforeSend` running, since it's optional.

## 7. Tests

- `src/__tests__/before-send-exporter.test.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md` § beforeSend hooks. The hook intentionally runs *after* sampling so dropped signals don't waste host-app CPU.

## 9. Rebuild recipe

1. Type the two-shape union and the `null` drop convention.
2. Implement `resolveBeforeSend` to flatten both shapes.
3. Implement three wrapper exporters that call the right hook(s) before delegating to the inner exporter; drop the signal if the hook returns `null`.
4. Insert wrappers into the pipeline only when the corresponding `hasBeforeSendForX` is true.
