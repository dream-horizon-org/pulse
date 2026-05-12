# iOS — Core: Exporter chain

## Purpose

Deliver telemetry over OTLP/HTTP without losing signals when offline, while respecting consent and the BeforeSend hook. Persistence buffers signals to disk when the network is unavailable.

## Source location

- `Sources/Exporters/OpenTelemetryProtocolHttp/` — OTLP HTTP exporter.
- `Sources/Exporters/OpenTelemetryProtocolGrpc/` — OTLP gRPC (optional).
- `Sources/Exporters/OpenTelemetryProtocolCommon/` — shared OTLP transforms.
- `Sources/Exporters/Persistence/` — disk-backed buffer.
- `Sources/Exporters/InMemory/`, `Jaeger/`, `Prometheus/`, `Zipkin/` — non-default options.
- `Sources/PulseKit/Consent/{ConsentSpanProcessor,ConsentLogProcessor,ConsentMetricExporter}.swift` — consent enforcement.
- `Sources/PulseKit/BeforeSend/{BeforeSendSpanExporter,BeforeSendLogExporter,BeforeSendMetricExporter}.swift` — last-mile hook.
- `Sources/PulseKit/FilteringSpanExporter.swift` — drops spans by predicate.
- `Sources/PulseKit/PulseRedaction.swift` — attribute redaction.
- `Sources/PulseKit/PulseLoggingExport.swift` — diagnostic stdout exporter.

## Public surface

Apps configure the chain through `PulseKitConfiguration` (endpoint, headers, BeforeSend closures, redaction rules). Direct exporter construction is not exposed.

## Internal design

### Span pipeline

```
TracerProvider
  └─ GlobalAttributesSpanProcessor
     └─ ScreenAttributesSpanProcessor      (Sources/PulseKit/ScreenAttributesSpanProcessor.swift)
        └─ NetworkAttributesSpanProcessor   (Sources/PulseKit/NetworkAttributesSpanProcessor.swift)
           └─ ConsentSpanProcessor          (consent gate — drops when not allowed)
              └─ FilteringSpanExporter      (predicate-based drop)
                 └─ BeforeSendSpanExporter  (user-supplied transform / drop)
                    └─ PulseSignalSelectExporter (sampling)
                       └─ Persistence layer (disk queue)
                          └─ OTLP HTTP exporter
```

### Log pipeline

Analogous with `GlobalAttributesLogRecordProcessor`, `ScreenAttributesLogRecordProcessor`, `NetworkAttributesLogRecordProcessor`, `ConsentLogProcessor`, `BeforeSendLogExporter`, sampling exporter, persistence, OTLP.

### Metric pipeline

`ConsentMetricExporter → BeforeSendMetricExporter → OTLP`.

### Persistence

`Sources/Exporters/Persistence/` writes serialized OTLP requests to disk under `PulseKit.persistenceDirectory`. On network failure, the request is queued; on next successful export window (or process launch via `PersistenceUtils`), queued batches are replayed. Disk cap is enforced; oldest batches drop first.

### Retry behavior

OTLP/HTTP retries transient 5xx / network errors with exponential backoff up to a bounded number of attempts. After exhaustion the batch goes to persistence.

### HTTP headers

`X-API-KEY: <project.id>` is set automatically (see `PulseAttributes.apiKeyHeaderKey`).

## Dependencies

- `OpenTelemetryProtocolExporterHttp` (when imported).
- `Foundation` for `URLSession`, `FileManager`.

## Data contracts

OTLP wire format. Identical bytes to the Android SDK's output for the same logical signal.

## Tests

- `Tests/PulseKitTests/Exporters/` and `Tests/PulseKitTests/BeforeSend/` cover each stage.
- Persistence has integration tests under `Tests/PulseKitTests/Persistence/`.

## History / decisions

- Consent processors deliberately placed BEFORE BeforeSend so customer code can never observe data the user has not consented to share.
- Persistence is opt-out (default ON) because mobile networks are unreliable.

## Rebuild recipe

1. Construct OTel providers in `PulseKit.swift`.
2. Insert processors in the order above; insert exporters in the order above.
3. Wire the persistence layer between BeforeSend / sampling and the OTLP exporter.
4. Always retain `_consentSpanProcessor`, `_consentLogProcessor`, `_consentMetricExporter` strongly on `Pulse` so consent state mutations are visible.
