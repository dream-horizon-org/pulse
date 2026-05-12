# Vector · Sources

What Vector listens on.

Brief: [../../../components/vector.md](../../../components/vector.md) · Peers: [transforms](./transforms.md), [sinks](./sinks.md).

## Source location

- `vector/vector.yaml` (dev) and `vector/vector-prod.yaml` (prod).

## Receivers

```yaml
sources:
  otlp_logs:
    type: opentelemetry
    grpc:
      address: "0.0.0.0:4317"
    http:
      address: "0.0.0.0:4318"

  vector_metrics:
    type: internal_metrics
```

- `otlp_logs` — OTLP receiver accepting both gRPC (4317) and HTTP (4318). This means SDKs can talk to either the OTel Collector or Vector at those ports; routing is decided at deploy time.
- `vector_metrics` — Vector's own internal metrics, fed to the Prometheus exporter sink for ops visibility.

## Data contracts

Incoming OTLP logs follow the standard OTLP JSON shape: `{resources: {...}, attributes: {...}, body, severity, timestamp, ...}`. Vector's source emits each log record as a `logs` stream (`otlp_logs.logs`) consumed by the `to_pulse_schema` transform.

## Operational notes

- `api: enabled: true` on `:8686` exposes Vector's admin API (disable/firewall in prod).
- `data_dir: /var/lib/vector` is required for disk buffers — the S3 sink depends on it.

## Rebuild recipe

1. Add the `opentelemetry` source with both protocol bindings.
2. Add `internal_metrics` so Prometheus scraping works out of the box.
3. Set `data_dir` — disk buffers will fail without it.
