# deploy

Docker Compose definition + bash orchestration scripts that boot the full Pulse stack locally. Also houses Ansible / Terraform / Jenkinsfiles for cluster deployment, OpenFGA bootstrap, and observability stack — but the day-to-day surface is `docker-compose.yml` + `scripts/`.

## Source layout

```
deploy/
├── .env.example                   (~15 KB; canonical env-var catalog)
├── docker-compose.yml             (~30 KB; all services)
├── README.md                      (top-level operator guide)
├── db/mysql-init.sql              (mirror of backend/db/dev/mysql/mysql-init.sql, mounted into MySQL)
├── scripts/
│   ├── common.sh                  (~48 KB library; never run directly)
│   ├── quickstart.sh              (prereqs → build → start → verify)
│   ├── build.sh                   (build ui / server / cron images, parallel)
│   ├── start.sh                   (dep-ordered start; -d / --build)
│   ├── stop.sh                    (-v removes volumes; --all wipes network)
│   ├── logs.sh                    (per-service streaming)
│   ├── reset-databases.sh         (destructive; confirms with "yes")
│   ├── init-clickhouse.sh         (applies backend/db/dev/clickhouse/*.sql)
│   ├── seed-ecommerce.sh / .py    (demo data)
│   ├── seed-ecommerce-data.py
│   └── validate-env-variables.sh
├── ansible/                       (cluster provisioning)
├── terraform/                     (cloud infra)
├── jenkinsfile/                   (CI pipelines, one per service)
├── monitoring/                    (Prometheus / Grafana side stack)
├── observability-otel-prometheus-tempo/  (alt monitoring stack)
└── openfga/                       (OpenFGA bootstrap)
```

## Compose services

From `docker-compose.yml`:

```
mysql, openfga-migrate, openfga, openfga-init,
clickhouse, clickhouse-init,
kafka, minio, minio-init,
pulse-session-capture,
pulse-session-replay-ingestion,
pulse-heatmap-screenshot-ingestion,
otel-collector,
pulse-ai-agent,
pulse-ui,
vector,
pulse-server,
pulse-alerts-cron
```

Network: `pulse-network` (single bridge). Volumes: `mysql-data`, `clickhouse-data`, `vector-data`, `pulse-ai-sessions`, `kafka-data`, `minio-data`.

## Ports (defaults)

| Service           | Host port |
|-------------------|-----------|
| pulse-ui (Nginx)  | 3000      |
| pulse-server      | 8080      |
| pulse-alerts-cron | 4000      |
| pulse-ai-agent    | 8000      |
| MySQL             | 3307      |
| ClickHouse HTTP   | 8123      |
| ClickHouse native | 9000      |
| OTel Collector OTLP HTTP | 4318 |
| OTel Collector OTLP gRPC | 4317 |
| OTel Collector health    | 13133 |
| OTel Collector Prom      | 8888 |
| Vector API        | 8686      |
| Vector Prom       | 9598      |

## Scripts (operator surface)

| Script              | Behavior                                                              |
|---------------------|-----------------------------------------------------------------------|
| `quickstart.sh`     | Interactive end-to-end: checks Docker/Colima/Engine, builds, starts, verifies. Auto-installs Colima (macOS) / Docker Engine CE (Linux). Idempotent. |
| `build.sh [--no-cache] [ui\|server\|cron\|all]` | Builds custom images in parallel.                |
| `start.sh [-d] [--build]` | Creates network/volumes, starts containers in dependency order (DBs → otel-collector → server → ui/cron). |
| `stop.sh [-v\|--all] [SERVICE...]` | Reverse-order stop in CLI mode. `-v` drops data volumes. `--all` drops volumes + network. |
| `logs.sh [--no-follow] [--tail N] [SERVICE]` | Stream or print per-service logs. |
| `reset-databases.sh` | Confirms (`yes`), drops `pulse-mysql-data` + `pulse-clickhouse-data`, restarts. **Never run unprompted.** |
| `init-clickhouse.sh` | One-shot job inside a CH container; waits-then-applies `backend/db/dev/clickhouse/*.sql`. Called by `start.sh` / Compose, not by hand. |
| `seed-ecommerce.sh [--clear]` | Seeds 12 critical interactions + ClickHouse demo data via `seed-ecommerce-data.py` (uses `docker exec pulse-mysql`). |
| `validate-env-variables.sh` | Pre-flight env validation. |

`common.sh` provides Compose-vs-CLI detection (`has_compose`, `run_compose`), path constants, env loader (`load_env`), coloured print helpers, and Docker install / health helpers. Every script sources it.

## Env vars

`.env.example` is the source of truth. Highlights:

- MySQL: `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE=pulse_db`, `MYSQL_USER=pulse_user`, `MYSQL_PASSWORD`, `MYSQL_{WRITER,READER}_MAX_POOL_SIZE`.
- Redis (external — Kong stack): `REDIS_HOST`, `REDIS_PORT`.
- Frontend: `REACT_APP_GOOGLE_CLIENT_ID`, `REACT_APP_PULSE_SERVER_URL`, `REACT_APP_GCP_TENANTS`, Firebase keys, `REACT_APP_ROOT_CAUSE_ENABLED`.
- Auth toggle: `GOOGLE_OAUTH_ENABLED` (shared FE/BE). When `false`, `DEV_MODE_API_KEY=default-project_devkey01` is the seeded API key.
- ClickHouse exporter: `CLICKHOUSE_ENDPOINT`, `CLICKHOUSE_DATABASE`, `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD` (consumed by `backend/ingestion/otel-collector.yaml`).
- SDK collector URLs returned to clients: `INTERACTION_CONFIG_URL`, `LOGS_COLLECTOR_URL`, `METRIC_COLLECTOR_URL`, `SPAN_COLLECTOR_URL`, `CUSTOM_EVENT_COLLECTOR_URL` (default `http://10.0.2.2:4318/...` for Android emulator).
- S3: `S3_BUCKET_NAME=pulse-otel-config`, `SYMBOL_FILES_S3_BUCKET_NAME=pulse-symbol-files`, plus CloudFront IDs.
- Analytics: `ANALYTICS_COMPUTE_ENGINE`, `ANALYTICS_BATCH_PROJECT_CONCURRENCY`.
- Feature flag: `ROOT_CAUSE_ENABLED` (mirror `REACT_APP_ROOT_CAUSE_ENABLED`).
- App env: `APP_ENVIRONMENT={dev,stag,uat,prod}` — drives backend config validation.

## Safety

- Never commit `.env`. Use `.env.example`.
- Never `reset-databases.sh` without explicit user confirmation.
- Never force-push to `main`.

## Plans

`/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/deploy/index.md`.

## Cross-links

- Schema bootstrap: `backend/db/` (`docs/components/pulse-db.md`).
- Ingestion config mounted into `otel-collector` service: `backend/ingestion/otel-collector.yaml` (`docs/components/pulse-ingestion.md`).
- Vector image + config: `vector/` (`docs/components/vector.md`).
- Operator README: `deploy/README.md`.
