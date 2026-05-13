# SDK Core — Exporters and persistence — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/exporters-and-persistence/SPEC.md`

---

## 1. Goal

Specify **OTLP provider wiring**, **IndexedDB disk buffering / drain**, **beacon relay**, and **`beforeSendData`** export hooks.

---

## 2. Assumptions

See [`../assumptions/SPEC.md`](../assumptions/SPEC.md).

---

## 3. Requirements

**R10 — IndexedDB drain** and export pipeline obligations — [`../requirements/SPEC.md`](../requirements/SPEC.md).

---

## 4. Architectural Design

`createProviders` → batch processors → OTLP HTTP exporters; optional IDB buffer layer; `drainBufferedOtlpExports` on next init — see [`../architecture-and-bootstrap/SPEC.md`](../architecture-and-bootstrap/SPEC.md).

---

## 5. LLD

### 5.1 OTLP exporters and providers

`src/exporters.ts`: `createProviders(exporterConfig, resource, spanProcessors, logProcessors)` builds three providers:

- `WebTracerProvider` → `BatchSpanProcessor` → `OtlpHttpExporter` → `/v1/traces`
- `LoggerProvider` → `BatchLogRecordProcessor` → `OtlpHttpLogExporter` → `/v1/logs`
- `MeterProvider` → `PeriodicExportingMetricReader` → `OtlpHttpMetricExporter` → `/v1/metrics`

Wire format: `protobuf` (default after `useProtobuf` flag) or `json`. `export.format: "json"` is intended for DevTools-readable debugging.

**IndexedDB disk buffering:** When `diskBuffering.enabled !== false` (on by default), the OTLP exporters write to an IndexedDB signal buffer (`IdbSignalBuffer`) before sending over the network. On the next init, `drainBufferedOtlpExports()` replays any unsent batches. Max age and max size are configurable; defaults enforced in `src/constants/disk-buffer.ts`.

**Beacon relay:** If `beaconRelayUrl` is set, `sendBeacon` calls are routed through a relay to avoid the API-key-in-querystring constraint of the native `sendBeacon` API.

**beforeSendData hooks:** `PulseWebBeforeSendConfig` — either a generic `beforeSend(signal) → signal | null` function or a typed object with `beforeSendSpan`, `beforeSendLog`, `beforeSendMetric`. Returning `null` drops the signal. Runs at export time in the exporter pipeline.

---

## 6. Test Coverage

[`../test-coverage/SPEC.md`](../test-coverage/SPEC.md) — `integration-simplified-init.test.ts`, persistence / beacon tests as listed there.

---

## 7. Known Bugs & Gaps

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) (beforeSend naming P0:4).

---

## 8. Redundancy & Cleanup Notes

None.

---

## 9. Open Questions

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9 (IDB drain vs first batch).
