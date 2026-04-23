# 01.D — Export Pipeline

**What this covers:** How signals get from the SDK to the Pulse backend — OTLP HTTP exporters, batch processing, IndexedDB persistence for crash recovery, and payload compression.

**Files produced:** `src/exporters.ts`, `src/persistence/idb-buffer.ts`, `src/utils/compression.ts`

**Android equivalent:** `OtlpGrpcSpanExporter` + `PersistenceExporterDecorator` + `BatchSpanProcessor`

---

## Main thread, batching, and `beforeSend`

Browser JS has **no background “export thread”** by default: span/log **processors**, **batch flush**, **OTLP serialization**, and any future **`beforeSend`** hook run on the **main thread** (same event loop as UI) unless we explicitly move work to a Web Worker (not current scope). That makes **`beforeSend` CPU cost stricter than Android** in user-visible impact — same guidance as Android (“don’t block export”), plus **avoid janking the page**.

**Pipeline order vs remote rules:** Remote-driven **`SignalFilterProcessor`** runs as a **processor** earlier; app **`beforeSend`** runs at **exporter batch export** (after batching, aligned with Android’s `PulseBeforeSend*Exporter` pattern). Full semantics, Sentry-style industry context, wrong-type drop rule, and **implementation plan** → **[before-send-web-android-parity.md](./before-send-web-android-parity.md)**.

---

## 5. OTLP Exporter Setup

The web SDK uses the same OTLP endpoints as the mobile SDKs — no backend changes required (only CORS headers need to be enabled — see [sdk-lifecycle.md](./sdk-lifecycle.md)).

```typescript
// src/exporters.ts

// Traces
const spanExporter = new OtlpHttpSpanExporter({
  url: `${config.endpointBaseUrl}/v1/traces`,
  headers: { 'X-API-KEY': config.apiKey },
});

// Logs
const logExporter = new OtlpHttpLogExporter({
  url: `${config.endpointBaseUrl}/v1/logs`,
  headers: { 'X-API-KEY': config.apiKey },
});

// Metrics
const metricExporter = new OtlpHttpMetricExporter({
  url: `${config.endpointBaseUrl}/v1/metrics`,
  headers: { 'X-API-KEY': config.apiKey },
});

// Page unload: flush synchronously via sendBeacon
window.addEventListener('pagehide', () => {
  tracerProvider.forceFlush();
  loggerProvider.forceFlush();
});
```

> The OTLP endpoints (`/v1/traces`, `/v1/logs`, `/v1/metrics`) are the same as mobile. The only backend prerequisite is CORS headers.

---

## 7. Batching Configuration

Both Android and iOS flush every **5 seconds**, with a max queue of **2048** and max batch of **512**. The web SDK matches these defaults.

```typescript
const BATCH_DEFAULTS = {
  scheduledDelayMillis:  5_000,   // flush every 5s
  maxQueueSize:          2_048,   // drop oldest when exceeded
  maxExportBatchSize:    512,     // max signals per export request
  exportTimeoutMillis:   30_000,  // abort export after 30s
};

// Applied to all three signal types
const spanProcessor    = new BatchSpanProcessor(spanExporter,  BATCH_DEFAULTS);
const logProcessor     = new BatchLogRecordProcessor(logExporter, BATCH_DEFAULTS);
const metricReader     = new PeriodicExportingMetricReader({
  exporter: metricExporter,
  exportIntervalMillis: BATCH_DEFAULTS.scheduledDelayMillis,
});
```

All three signal types use the same defaults. Configurable via `PulseWebConfig.export.batch` — customers with high-frequency signals can tune flush interval and batch size.

**`pagehide` flush:** `tracerProvider.forceFlush()` + `loggerProvider.forceFlush()` called synchronously on `pagehide`. Signals exceeding `sendBeacon`'s 64 KB limit are dropped — batching keeps this rare.

---

## 8. Persistence — IndexedDB Signal Buffer

**Why:** Mobile SDKs persist unsent signals to disk (Android: file cache; iOS: `PersistenceExporterDecorator`) so they survive process kill. On next launch the buffer drains before normal operation.

On web, `IndexedDB` is the only persistent async storage with sufficient capacity.

```
Signal emitted
    │
    ▼
PersistenceExporterDecorator
    ├─→ Write to IndexedDB  (pulse_signal_buffer)
    └─→ Attempt network export
            ├─ Success → delete from IndexedDB
            └─ Failure → leave in IndexedDB (retried on next page load)

On SDK init:
    └─→ DrainBuffer() → export all stored signals → clear buffer
```

### Store schema

```typescript
interface BufferedSignal {
  id:          string;                          // UUID
  signalType:  'trace' | 'log' | 'metric';
  payload:     string;                          // Serialised OTLP JSON/Protobuf bytes
  timestamp:   number;                          // ms since epoch — used for TTL pruning
}
```

### Limits

| Setting | Default | Notes |
|---|---|---|
| Max buffer size | 5 MB | Prune oldest entries when exceeded |
| Max signal age | 24 hours | Stale signals dropped on drain |
| Session replay | Not persisted | Too large; sendBeacon handles delivery |

### Config

```typescript
PulseWebConfig.diskBuffering = {
  enabled:       false,       // set false to opt out (default is on — Android OTel parity)
  maxCacheSizeBytes:  5_242_880,   // optional cap (see SDK defaults)
  maxAgeMs:      86_400_000,  // optional max row age (see SDK defaults)
}
```

**Default is on** (same as Android when `PulseSDK.initialize` does not pass a disk lambda: `DiskBufferingConfigurationSpec.isEnabled` defaults to `true`). IndexedDB access is async; the decorator wraps the sync OTel exporter interface with a fire-and-forget write (errors silently swallowed to not affect signal flow).

---

## 9. Payload Format & Compression

### Format — JSON vs Protobuf

| Format | Content-Type | Bundle impact | Notes |
|---|---|---|---|
| JSON (default) | `application/json` | Zero — OTel JS ships JSON exporters | Human-readable, easier debugging |
| Protobuf | `application/x-protobuf` | +~8 KB | Smaller payloads; needs `exporter-*-otlp-proto` packages |

Default is **JSON**. Protobuf is available via `PulseWebConfig.export.format: 'protobuf'` for customers where payload size is a concern. iOS uses Protobuf by default; Android delegates to exporter choice.

### Compression — gzip via `CompressionStream`

The browser `CompressionStream` API (Chrome 80+, Firefox 113+, Safari 16.4+) enables gzip with **zero bundle cost**:

```typescript
// src/utils/compression.ts
async function gzipBody(body: string): Promise<ArrayBuffer> {
  const stream = new CompressionStream('gzip');
  const writer = stream.writable.getWriter();
  writer.write(new TextEncoder().encode(body));
  writer.close();
  return new Response(stream.readable).arrayBuffer();
}
```

Applied to all OTLP export requests when `CompressionStream` is available. Falls back to uncompressed transparently on older browsers — no action needed by the implementer.

**Config:** `PulseWebConfig.export.compression: 'gzip' | 'none'` (default: `'gzip'` — uses `CompressionStream` if available, else no-op)

---

## Done Criteria

**Batching**
- [ ] Signals batched with 5s flush interval, 2048 queue, 512 batch size
- [ ] `pagehide` triggers `forceFlush()` before tab closes

**Persistence**
- [ ] Failed export writes payload to IndexedDB (default: buffering on; same as Android OTel default)
- [ ] On next `start()`, IndexedDB buffer is drained before normal operation
- [ ] Entries older than 24h are pruned on drain
- [ ] `diskBuffering.enabled: false` skips all IndexedDB writes

**Payload & Compression**
- [ ] Default export uses `Content-Type: application/json`
- [ ] `export.format: 'protobuf'` switches to `application/x-protobuf`
- [ ] `export.compression: 'gzip'` applies `CompressionStream` where available; no-ops on unsupported browsers
- [ ] `Content-Encoding: gzip` header present when compression applied
- [ ] Smoke test: OTLP POST to `/v1/traces` returns 200; span visible in ClickHouse
