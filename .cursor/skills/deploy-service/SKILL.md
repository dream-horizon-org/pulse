---
name: deploy-service
description: Workflow for building and deploying Pulse services locally via Docker. Use when building Docker images, starting/stopping services, or managing the local development environment.
---

# Deploy Service

## Quick Start (All Services)

```bash
cd deploy
cp .env.example .env    # first time only, then edit values
./scripts/quickstart.sh
```

## Build Specific Service

```bash
cd deploy
./scripts/build.sh ui          # pulse-ui only
./scripts/build.sh server      # pulse-server only
./scripts/build.sh cron        # pulse-alerts-cron only
./scripts/build.sh capture     # pulse-session-capture only
./scripts/build.sh ingestion   # pulse-session-replay-ingestion only
./scripts/build.sh ai          # pulse-ai-agent only
./scripts/build.sh all         # same as omitting args: ui + server + cron + capture + ingestion + ai
```

## Start/Stop

```bash
./scripts/start.sh -d              # start all detached (includes pulse-ai-agent)
./scripts/start.sh -d --build      # build + start detached
./scripts/stop.sh                  # stop all
./scripts/stop.sh ai               # stop pulse-ai-agent only (CLI path)
./scripts/stop.sh -v               # stop + remove volumes
```

## View Logs

```bash
./scripts/logs.sh                # all services
./scripts/logs.sh server         # pulse-server
./scripts/logs.sh ai             # pulse-ai-agent
```

## Pulse AI

**Integrated (deploy stack):** `pulse-ai-agent` starts with `./scripts/start.sh -d`. Set `GOOGLE_API_KEY` in
`deploy/.env` for Gemini. Health: `curl -sf http://localhost:8000/health`.

**Standalone (AI-only dev):**

```bash
cd pulse_ai && cp .env.example .env   # first time — set GOOGLE_API_KEY
cd pulse_ai && ./setup.sh             # build + start (Docker, port 8000)
curl -sf http://localhost:8000/health
```

## Reset Databases

```bash
./scripts/reset-databases.sh     # drops volumes, reinitializes
```

## Health Checks

Run `docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"` to discover actual ports, then use them in health
checks:

| Service               | Health Check                                   | Default Port |
|-----------------------|------------------------------------------------|--------------|
| pulse-server          | `curl http://localhost:<port>/healthcheck`     | 8080         |
| pulse-ui              | `curl http://localhost:<port>/healthcheck.txt` | 3000         |
| pulse-alerts-cron     | `curl http://localhost:<port>/healthcheck`     | 4000         |
| OpenFGA               | `curl http://localhost:8180/healthz`           | 8180         |
| OTEL Collector        | `curl http://localhost:<port>/`                | 13133        |
| pulse-ai-agent        | `curl -sf http://localhost:8000/health`        | 8000         |
| pulse-session-capture | `curl http://localhost:3400/healthcheck`       | 3400         |
| MinIO (dev)           | S3 API on host `9100`, console `9101`          | 9100 / 9101  |

## Troubleshooting

- **Port conflict**: check if another service uses the port (`lsof -i :8080`)
- **Build failure**: try `./scripts/build.sh --no-cache <service>`
- **DB not ready**: wait for health check or run `./scripts/reset-databases.sh`
- **Missing env vars**: compare `deploy/.env` with `deploy/.env.example`
