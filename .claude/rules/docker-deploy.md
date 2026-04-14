---
paths:
  - "deploy/**"
---

# Deploy Conventions

## Docker Compose Services

| Service | Port(s) | Depends on |
|---------|---------|------------|
| mysql | 3307 | — |
| openfga | 8180, 8181, 3001 | mysql |
| clickhouse | 8123, 9000 | — |
| clickhouse-init | — | clickhouse (healthy), kafka (healthy) |
| kafka | 9092 | — |
| minio | 9100 (S3), 9101 (console) | — |
| otel-collector | 4317, 4318, 13133 | clickhouse (healthy) |
| vector | 8686, 14317, 14318 | — | *(profile: `vector`)* |
| pulse-ai-agent | 8000 | — |
| pulse-server | 8080 | mysql, clickhouse, openfga-init, pulse-ai-agent |
| pulse-ui | 3000 | pulse-server (healthy) |
| pulse-alerts-cron | 4000 | mysql, pulse-server, clickhouse, minio-init |
| pulse-session-capture | 3400 | kafka (healthy) |

All services on `pulse-network` bridge. Internal references use container names.

## Scripts

| Script | Key Options |
|--------|-------------|
| `quickstart.sh` | `[--no-cache] [--skip-env-check]` — build + start all |
| `build.sh` | `[ui|server|cron|capture|ingestion|ai|all] [--no-cache]` |
| `start.sh` | `[-d] [--build] [--no-cache] [--skip-env-check]` |
| `stop.sh` | `[-v]` removes volumes; service aliases: `ai`, `server`, `ui` |
| `logs.sh` | `[service]` e.g. `server`, `ai`, `otel-collector` |
| `reset-databases.sh` | **Destructive** — drops volumes and reinitializes |

## Key Environment Variables

- `APP_ENVIRONMENT` — **Required.** `dev` / `stag` / `uat` / `prod`
- `GOOGLE_OAUTH_ENABLED` — `false` enables dev mode
- `DEV_MODE_API_KEY` — Hardcoded API key for dev mode (default: `default-project_devkey01`)
- `GOOGLE_API_KEY` — Required for Gemini/AI agent
- `VECTOR_ENABLED=true` — Appends `vector` Compose profile
- `CLICKHOUSE_CLUSTER_NAME` — Empty for local dev, required in prod

**Required (no default in compose):** `VAULT_ENCRYPTION_MASTER_KEY`, `REACT_APP_GOOGLE_CLIENT_ID`, `REACT_APP_FIREBASE_*`, `AWS_*`

## Never

- Never commit `.env` — use `.env.example` as template
- Confirm with user before running `reset-databases.sh`
