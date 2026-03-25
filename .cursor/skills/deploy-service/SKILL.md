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
./scripts/build.sh ui         # pulse-ui only
./scripts/build.sh server     # pulse-server only
./scripts/build.sh cron       # pulse-alerts-cron only
./scripts/build.sh all        # everything
```

## Start/Stop

```bash
./scripts/start.sh -d           # start all detached
./scripts/start.sh -d --build   # build + start detached
./scripts/stop.sh                # stop all
./scripts/stop.sh -v             # stop + remove volumes
```

## View Logs

```bash
./scripts/logs.sh                # all services
./scripts/logs.sh pulse-server   # specific service
```

## AI Agent (own Docker Compose)

```bash
cd pulse_ai && cp .env.example .env   # first time — set GOOGLE_API_KEY
cd pulse_ai && ./setup.sh             # build + start (Docker, port 8000)
curl http://localhost:8000             # health check
```

## Reset Databases

```bash
./scripts/reset-databases.sh     # drops volumes, reinitializes
```

## Health Checks

Run `docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"` to discover actual ports, then use them in health checks:

| Service | Health Check | Default Port |
|---------|-------------|--------------|
| pulse-server | `curl http://localhost:<port>/healthcheck` | 8080 |
| pulse-ui | `curl http://localhost:<port>/healthcheck.txt` | 3000 |
| pulse-alerts-cron | `curl http://localhost:<port>/healthcheck` | 4000 |
| OpenFGA | `curl http://localhost:8180/healthz` | 8180 |
| OTEL Collector | `curl http://localhost:<port>/` | 13133 |
| pulse-ai (own compose) | `curl http://localhost:8000` | 8000 |

## Troubleshooting

- **Port conflict**: check if another service uses the port (`lsof -i :8080`)
- **Build failure**: try `./scripts/build.sh --no-cache <service>`
- **DB not ready**: wait for health check or run `./scripts/reset-databases.sh`
- **Missing env vars**: compare `deploy/.env` with `deploy/.env.example`
