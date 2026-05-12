# Android — Core: Exporter chain

## Purpose

Deliver telemetry to OTLP without (a) blocking app startup, (b) losing signals produced before the network stack is ready, or (c) dropping data while offline.

Authoritative reference: `pulse-android-otel/docs/EXPORTER_CHAIN.md`. StrictMode discussion: `pulse-android-otel/docs/STRICTMODE.md`.

## Source location

- `core/src/main/java/io/opentelemetry/android/export/BufferDelegatingSpanExporter.kt`
- `core/src/main/java/io/opentelemetry/android/export/BufferDelegatingLogExporter.kt`
- `core/src/main/java/io/opentelemetry/android/export/BufferDelegatingMetricExporter.kt`
- Upstream `io.opentelemetry.contrib.disk.buffering.{SpanToDiskExporter, LogRecordToDiskExporter, MetricToDiskExporter, SignalFromDiskExporter}` (consumed via dependency).
- `android-agent/src/main/kotlin/io/opentelemetry/android/agent/connectivity/HttpEndpointConnectivity.kt` — endpoint config.
- `android-agent/src/main/kotlin/io/opentelemetry/android/agent/dsl/DiskBufferingConfigurationSpec.kt`.

## Public surface

Apps don't touch the chain directly; they configure it via the `OpenTelemetryRum` DSL (`diskBufferingConfig { enabled = true }` etc.) and through `PulseSDK.initialize`'s underlying builder. Customizers can be injected to add filtering/redaction layers.

## Internal design — default chain (disk buffering ON)

For each signal type:

```
BufferDelegating*Exporter  →  *ToDiskExporter  →  Original exporter (OTLP/Logging)
```

Replay path:

```
SignalFromDiskExporter → *FromDiskExporter → Original exporter
```

### Stages

1. **In-memory buffer (`BufferDelegating*Exporter`)** — installed synchronously at builder time. Holds up to **5,000** items per signal type while real exporters spin up. On overflow: drop with warning `The <type> buffer was filled before export delegate set...`. When `setDelegate(...)` fires, buffered items flush, pending `flush()`/`shutdown()` are honored, and subsequent calls pass straight through (no further buffering).
2. **Disk layer (optional)** — `*ToDiskExporter` writes batches to per-signal disk queues, then forwards.
3. **OTLP exporter** — typically OTLP/HTTP, replacing default `LoggingSpanExporter` / `SystemOutLogRecordExporter` / `LoggingMetricExporter` in production.
4. **Replay scheduler** — `SignalFromDiskExporter` periodically reads batches from disk and replays through the original exporter.

### Disk buffering OFF

```
BufferDelegating*Exporter → Original exporter
```

No disk layer, no replay scheduler.

### Asynchronous init

The real exporter wiring runs on a background executor. The main thread returns from `PulseSDK.initialize` quickly with only the in-memory buffer in place — this is what keeps StrictMode clean (see `docs/STRICTMODE.md`).

## Retry / persistence behavior

- OTLP exporter applies its standard retry policy on transient failures.
- When persistence is enabled, failed exports are kept on disk (oldest batches drop only when the configured size cap is exceeded). On next process launch, `SignalFromDiskExporter` replays pending batches before new signals are exported.
- Buffer overflows during init are surfaced via Logcat warnings, not via exceptions — telemetry loss is non-fatal by design.

## Dependencies

- OTel Android `core` module (`BufferDelegating*`).
- `io.opentelemetry.contrib:opentelemetry-disk-buffering`.
- OTLP exporter dependency (HTTP or gRPC).

## Data contracts

Wire format is OTLP (signals identical to web SDK). HTTP requests carry `X-API-KEY: <project.id>`.

## Tests

- `core/src/test/.../BufferDelegating*ExporterTest` (upstream tests verify the buffer→delegate handoff and overflow behavior).
- Integration with disk replay covered in the disk-buffering contrib module.

## History / decisions

- 5,000-item cap is per signal type and chosen to bound memory during first-launch storms.
- Errors during early init must never crash the app; warnings + drop preferred over throw.

## Rebuild recipe

1. Install `BufferDelegating{Span,Log,Metric}Exporter` synchronously inside the `OpenTelemetryRum` builder.
2. Launch a background executor that constructs the real chain: `OTLP → ToDisk → BufferDelegate.setDelegate(...)`.
3. Wire `SignalFromDiskExporter` on a periodic task when disk buffering enabled.
4. Expose customizer hooks so apps can splice in filtering / redaction / fan-out exporters between the buffer and the OTLP layer.
