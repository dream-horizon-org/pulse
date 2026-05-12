# pipeline/exporters

## 1. Purpose

Build the three OTel providers (trace, log, metric) with OTLP/HTTP exporters that go to the Pulse Collector. Wrap each exporter with the sampling / metrics-to-add / beforeSend / IndexedDB layers and a `keepalive` fetch transport for `pagehide`.

## 2. Source location

- `pulse-web-otel/src/exporters.ts` — `createProviders`, `GlobalAttributeInjectingMetricExporter`, `prepareForDocumentUnload`
- `pulse-web-otel/src/exporters/pulse-browser-otlp-exporters.ts` — concrete OTLP exporters (trace/log/metric)
- `pulse-web-otel/src/exporters/otlp-transport.ts` — `fetch` / `sendBeacon` transport
- `pulse-web-otel/src/exporters/pulse-retrying-transport.ts` — retry with backoff
- `pulse-web-otel/src/exporters/before-send-exporters.ts` — wraps `beforeSendData` hooks
- `pulse-web-otel/src/exporters/wrap-log-exporter-lifecycle-debug.ts` — dev wrapper
- `pulse-web-otel/src/constants/exporters.ts` — `DEFAULT_BATCH_OPTIONS`
- `pulse-web-otel/src/types/exporters.ts` — `ExporterConfig`, `ProviderBundle`

## 3. Public surface

```ts
export function createProviders(opts: {
  resource: Resource;
  exporter: ExporterConfig;
  beforeSend?: ResolvedBeforeSend;
  samplingGate?: ExportSamplingGate;
  metricsToAdd?: PulseMetricsToAddEntry[];
  ...
}): ProviderBundle;
```

`ProviderBundle` exposes `tracerProvider`, `loggerProvider`, `meterProvider`, plus lifecycle hooks (`prepareForDocumentUnload`, `forceFlush`, `shutdown`).

## 4. Internal design

Per-signal exporter stack (outermost first — order matters):

```
BatchProcessor
  → BeforeSendExporter (if configured)
    → SampledExporter (export-time session sampling gate)
      → MetricsToAddExporter (records derived metrics)
        → PulseBrowserOtlpExporter
          → PulseRetryingTransport
            → OtlpTransport (fetch / sendBeacon)
              → IdbSignalBuffer (on transport failure)
```

- Compression is hardcoded off (`USE_GZIP = false`) — mirrors Android internals.
- Wire format: `ExporterConfig.useProtobuf` toggles JSON vs protobuf.
- `prepareForDocumentUnload` swaps the trace + log transports to `keepalive` fetch so in-flight batches survive `pagehide` (`sendBeacon` size cap is ~64 KiB so we prefer keepalive fetch when available).
- `GlobalAttributeInjectingMetricExporter` patches every `ResourceMetrics` with the current global attrs at export time.

## 5. Dependencies

- `@opentelemetry/sdk-trace-web`, `@opentelemetry/sdk-logs`, `@opentelemetry/sdk-metrics`
- `@opentelemetry/core` for `ExportResult`
- Internal: `sampling/*`, `before-send.ts`, `persistence/indexed-db.ts`

## 6. Data contracts

No new attributes — but `pulse.sampled` is set by `SampledExporter` (Android parity); the IndexedDB rows preserve full OTLP envelopes for replay.

## 7. Tests

- `src/__tests__/exporters-batch-queue.test.ts`
- `src/__tests__/before-send-exporter.test.ts`
- `src/__tests__/send-beacon-transport.test.ts`
- `src/__tests__/otlp-log-event-name.test.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md` § OTLP exporters. The "outermost is batch" rule is Android-aligned: sampling happens after the host-app `beforeSend` so users can't trigger sampling decisions inadvertently.

## 9. Rebuild recipe

1. Implement `OtlpTransport` (fetch with retry / sendBeacon fallback).
2. Wrap with `PulseRetryingTransport`.
3. Build `PulseBrowserTraceExporter` / `LogExporter` / `MetricExporter` on top.
4. Layer the sampling and metrics-to-add wrappers per the diagram above.
5. Hand to `BatchSpanProcessor` / `BatchLogRecordProcessor` / `PeriodicExportingMetricReader` with `DEFAULT_BATCH_OPTIONS`.
6. Expose `prepareForDocumentUnload` from the `ProviderBundle`.
