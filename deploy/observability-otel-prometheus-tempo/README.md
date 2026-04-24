# Local stack: OTEL Collector → Prometheus + Tempo (MinIO) + Grafana

Optional compose for **application** metrics and traces using OTLP, separate from Pulse’s ClickHouse-backed collector.

## Flow

1. Apps export **OTLP** to the Collector (`14317` gRPC / `14318` HTTP on the host).
2. **Metrics:** Collector `prometheus` exporter on `:8889` → **Prometheus scrapes** `obs_otel_collector:8889`.
3. **Traces:** Collector forwards OTLP to **Tempo** → blocks stored on **MinIO** (S3 API).
4. **Grafana** (`3310`): Prometheus + Tempo datasources.

## Run

```bash
cd deploy/observability-otel-prometheus-tempo
docker compose up -d
```

- Grafana: http://localhost:3310  
- Prometheus: http://localhost:9091  
- OTLP HTTP: `http://localhost:14318`  
- OTLP gRPC: `localhost:14317`  
- MinIO console: http://localhost:9003 (user `minio` / `minio123`)

### Prometheus UI (no Grafana “dashboard”)

Prometheus does **not** store Grafana-style dashboards. Use **Graph** (and **Alerts** if you add alert rules) at http://localhost:9091.

**Recording rules** (pre-aggregated series) live in **`prometheus/rules/pulse-otel.yml`**. After `docker compose up -d`, open **Status → Rules** to confirm they load, then in **Graph** try:

| Metric (recording rule) | Meaning |
|-------------------------|---------|
| `otel:http_server:request_rate5m` | req/s by `job`, method, status |
| `otel:http_server:request_rate5m_total` | total req/s per `job` |
| `otel:http_server:latency_mean_5m` | mean latency (s) per `job` |
| `otel:http_server:latency_p95_5m` | p95 latency (s) per `job` |
| `otel:http_server:error_ratio_5m` | 5xx ratio per `job` |

**Raw queries** (same data, no recording rule):  
`sum(rate(otel_http_server_request_duration_seconds_count[5m])) by (job, http_response_status_code)`  
`histogram_quantile(0.95, sum(rate(otel_http_server_request_duration_seconds_bucket[5m])) by (job, le))`

After changing `otel-collector.yaml`, restart the stack so **`resource_to_telemetry_conversion`** takes effect (may add `host.name` etc. from the JVM resource as extra labels when the SDK exports them).

### Grafana dashboard (OTEL JVM / HTTP)

On `docker compose up`, Grafana loads **`grafana/dashboards/pulse-jvm-otel.json`** into folder **Pulse** (HTTP RED-style panels + JVM memory/CPU/classes/GC when metrics exist).

**Manual import:** Dashboards → **Import** → upload `grafana/dashboards/pulse-jvm-otel.json` → choose the **Prometheus** datasource when asked (`DS_PROMETHEUS`).

**Paths vs metrics:** JVM HTTP Prometheus series are usually grouped by **method + status**, not full URL. **Response time** uses the histogram (`_bucket` / `_sum` / `_count` or `histogram_quantile`). **Path and host** detail lives in **Tempo** traces (Explore → Tempo), not on these panels.

## SDK env (example)

```text
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:14318
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
```

(Or gRPC to `http://localhost:14317` per your SDK.)

## Retention

`tempo.yaml` sets `block_retention: 2160h` (90 days). For six months use `4320h`. For local demos you can shorten (e.g. `168h`).

## Ports vs Pulse

Pulse `docker-compose` uses host `4317` for its collector and `9100` for MinIO. This stack uses **14317/14318**, **9002/9003**, **3310**, **9091** to reduce conflicts.

Do not enable the Pulse **Vector** profile at the same time: Vector also maps **14317/14318** on the host.

## Pulse JVM backends (`pulse-server`, `pulse-alerts-cron`)

With **default** `deploy/.env`, behavior is unchanged (no OTEL agent).

1. Start this stack: `docker compose up -d` in this directory (collector on host **14318**).
2. In `deploy/.env` set `PULSE_BACKEND_OTEL_ENABLED=true`.
3. Rebuild and restart Pulse: `docker compose build pulse-server pulse-alerts-cron && docker compose up -d pulse-server pulse-alerts-cron`.

Containers use `host.docker.internal:14318` (see `extra_hosts: host-gateway` in main compose) so OTLP reaches the observability collector from the Pulse stack without merging Docker networks.

Optional overrides: `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME` (defaults: `pulse-server` / `pulse-alerts-cron` when enabled).
