# Compose · Services

Every service declared in `deploy/docker-compose.yml` and what it does.

Brief: [../../../components/deploy.md](../../../components/deploy.md) · Peers: [networks-volumes](./networks-volumes.md), [env-vars](./env-vars.md).

## Service list

| Service | Image / Build | Role | Host port |
|---|---|---|---|
| `mysql` | `mysql:8.0` | Control-plane DB | `3307:3306` |
| `openfga-migrate` | `alpine:3.19` + `migrate-openfga.sh` | One-shot: bootstraps OpenFGA schema | — |
| `openfga` | OpenFGA server | Fine-grained authorization store (MySQL-backed) | as configured |
| `clickhouse` | `clickhouse/clickhouse-server` | Telemetry store (`otel` DB) | 8123/9000 |
| `otel-collector` | OTel Collector contrib | OTLP → ClickHouse | 4317/4318, 13133 |
| `vector` | custom (`vector/Dockerfile`) | OTLP → S3/ClickHouse custom-event path | 4317/4318, 8686, 9598 |
| Kafka + Zookeeper | bitnami / confluent | Bus for replay + heatmap | 9092 |
| `session-capture-service` | builds from `backend/session-capture-service/` | WS ingestor → Kafka | as configured |
| `session-replay-ingestion` | builds from `backend/session-replay-ingestion/` | Kafka → S3 | — |
| `heatmap-screenshot-ingestion` | builds from `backend/heatmap-screenshot-ingestion/` | Kafka → Redis + S3 | — |
| `pulse-server` | builds from `backend/server/` | REST API | `8080:8080` |
| `pulse-alerts-cron` | builds from `backend/pulse-alerts-cron/` | Alert cron | `4000:4000` |
| `pulse-ui` | builds from `pulse-ui/` | Dashboard | `3000:3000` |
| `pulse-ai-agent` | builds from `pulse_ai/` | ADK agent | `8000:8000` |

Service names may vary slightly in the YAML; consult `deploy/docker-compose.yml` for the authoritative list.

## Dependencies / healthchecks

- `mysql` has a healthcheck (`mysqladmin ping`); most services `depends_on: { mysql: { condition: service_healthy } }`.
- `openfga` depends on `openfga-migrate` completing.
- `pulse-server` waits for both `mysql` and `clickhouse`.

## Init

- `mysql` mounts `../backend/db/dev/mysql/mysql-init.sql` as `/docker-entrypoint-initdb.d/init.sql:ro`.
- `clickhouse` bootstrap is handled externally by `scripts/init-clickhouse.sh`.

## Rebuild recipe

1. Start with a `services:` map; declare each row above.
2. Add healthchecks for stateful services.
3. Wire init SQL via volume mounts.
4. Use `${VAR:-default}` patterns for overridable env.
5. Put everything on a single `pulse-network`.
