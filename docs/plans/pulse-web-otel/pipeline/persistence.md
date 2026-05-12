# pipeline/persistence

## 1. Purpose

Buffer failed OTLP exports in IndexedDB (on by default) and replay them on the next SDK init — mirrors Android's `DiskBufferingConfigurationSpec`. Lets the SDK survive offline / closed-tab episodes without losing crash and session data.

## 2. Source location

- `pulse-web-otel/src/persistence/indexed-db.ts` — `IdbSignalBuffer`
- `pulse-web-otel/src/persistence/drain-buffered-exports.ts` — `drainBufferedOtlpExports`
- `pulse-web-otel/src/types/persistence.ts` — `BufferedOtlpEnvelope`, `BufferedSignalRow`, `BufferedSignalType`
- `pulse-web-otel/src/constants/disk-buffer.ts` — `DEFAULT_DISK_BUFFER_MAX_AGE_MS`, `DEFAULT_DISK_BUFFER_MAX_CACHE_SIZE_BYTES`, `resolveDiskBufferMaxAgeMs/Bytes`

## 3. Public surface

```ts
class IdbSignalBuffer {
  putEnvelope(env: BufferedOtlpEnvelope): Promise<void>;
  forEachRow(cb: (row: BufferedSignalRow) => Promise<void>): Promise<void>;
  deleteRow(id: number): Promise<void>;
  evictExpired(): Promise<void>;
  evictOverflow(): Promise<void>;
}

function drainBufferedOtlpExports(p: DrainBufferedExportsParams): Promise<void>;
```

Configured via `PulseWebConfig.diskBuffering = { enabled?: boolean; maxAgeMs?: number; maxCacheSizeBytes?: number }`.

## 4. Internal design

- IndexedDB: db `pulse_signal_buffer`, version 2, object store `signals`.
- A `BufferedSignalRow` records `signalType: "trace" | "log" | "metric"`, the raw OTLP envelope (base64 protobuf or JSON string), timestamp, byte size.
- Eviction:
  - `evictExpired()` drops rows older than `maxAgeMs` (default per `constants/disk-buffer.ts`).
  - `evictOverflow()` deletes oldest rows until total size ≤ `maxCacheSizeBytes`.
- Drain: on `Pulse.init()` (before instrumentations install), `drainBufferedOtlpExports` iterates rows, picks the right URL (`tracesUrl` / `logsUrl` / `metricsUrl`), POSTs them, and deletes each row on HTTP 2xx. `base64ToUint8` is reused from `exporters/otlp-transport.ts` for protobuf bodies.
- A buffered write happens only on transport failure (4xx/5xx/network); successful exports never touch IndexedDB.

## 5. Dependencies

- Browser IndexedDB
- `exporters/otlp-transport.ts` for base64 decode

## 6. Data contracts

Transparent — preserves the original OTLP envelope unchanged.

## 7. Tests

- `src/__tests__/exporters-batch-queue.test.ts` (interaction with failed exports)
- `src/__tests__/m8.test.ts` (resilience milestone)
- E2E: `examples/ecommerce-demo/e2e/m8.spec.ts`

## 8. History / decisions

Disk buffering is on by default — Android parity. The public `PulseWebDiskBufferingConfig` only exposes the `enabled`, `maxAgeMs`, and `maxCacheSizeBytes` knobs; everything else is internal.

## 9. Rebuild recipe

1. Open the db with an upgrade handler that creates `signals` with an autoincrement primary key.
2. On transport failure inside `OtlpTransport`, call `IdbSignalBuffer.putEnvelope`.
3. Run `evictExpired` + `evictOverflow` opportunistically (e.g. after each write).
4. Run `drainBufferedOtlpExports` once during `Pulse.init()` before installing instrumentations.
