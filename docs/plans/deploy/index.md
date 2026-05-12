# deploy — Plan Handbook

Docker Compose stack + shell scripts that boot the full Pulse platform locally (and the seed Ansible/Terraform bits for cloud).

Brief: [../../components/deploy.md](../../components/deploy.md)

## Files under `deploy/`

- `docker-compose.yml` — all services.
- `scripts/` — operator CLIs (`quickstart.sh`, `start.sh`, `stop.sh`, `logs.sh`, `build.sh`, `init-clickhouse.sh`, `reset-databases.sh`, `seed-ecommerce.sh`, `seed-ecommerce-data.py`, `validate-env-variables.sh`, `common.sh`).
- `ansible/`, `terraform/` — IaC (not covered here).
- `monitoring/`, `observability-otel-prometheus-tempo/` — optional local telemetry stack.
- `openfga/` — OpenFGA authorization init scripts.
- `db/` — supporting init SQL (referenced from compose volumes).
- `README.md`, `jenkinsfile`.

## Sub-files

### `compose/`

| File | Topic |
|---|---|
| [services.md](./compose/services.md) | Every service declared in `docker-compose.yml` |
| [networks-volumes.md](./compose/networks-volumes.md) | `pulse-network`, named volumes, bind mounts |
| [env-vars.md](./compose/env-vars.md) | Required/optional env (MySQL, ClickHouse, Google OAuth, Kafka) |

### `scripts/`

| File | Topic |
|---|---|
| [quickstart.md](./scripts/quickstart.md) | `quickstart.sh` — build + start in one call |
| [start-stop.md](./scripts/start-stop.md) | `start.sh -d`, `stop.sh [-v]`; Compose vs CLI fallback |
| [logs.md](./scripts/logs.md) | `logs.sh [--tail N] [--no-follow] [service]` |

## Reading order

1. Brief.
2. `compose/services.md` to see what boots.
3. `scripts/quickstart.md` to boot it.
4. `compose/env-vars.md` if anything fails with a missing var.

## Rebuild checklist

1. Start with `docker-compose.yml` defining: `mysql`, `clickhouse`, `otel-collector`, `vector`, `openfga-migrate` + `openfga`, `pulse-server`, `pulse-alerts-cron`, `pulse-ui`, `pulse-ai-agent` (optional), Kafka/Zookeeper + `session-capture-service`, replay + heatmap ingestion.
2. Add `pulse-network` + named volumes (`mysql-data`, `clickhouse-data`, `kafka-data`).
3. Wire init scripts (mysql init.sql, clickhouse init via `init-clickhouse.sh`).
4. Write `scripts/common.sh` centralizing: `check_docker`, `has_compose`, `run_compose`, `load_env`, colors.
5. Build the three operator scripts on top of `common.sh`.
6. Document env vars via `.env.example`.
