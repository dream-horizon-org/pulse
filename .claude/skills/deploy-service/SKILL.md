---
name: deploy-service
description: Build and deploy a specific Pulse service via Docker Compose. Usage: /deploy-service [service|all]
allowed-tools: Bash(./deploy/scripts/*)  Bash(docker *)
---

Deploy service: `$ARGUMENTS` (default: `all` if not specified)

**Step 1 — Build**
```bash
cd deploy && ./scripts/build.sh $ARGUMENTS
```

Valid targets: `ui` · `server` · `cron` · `capture` · `ingestion` · `ai` · `all`

**Step 2 — Restart service**
```bash
cd deploy && ./scripts/stop.sh $ARGUMENTS
cd deploy && ./scripts/start.sh -d
```

**Step 3 — Verify health**
```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

Report which services are healthy and flag any that failed to start.

**Safety rules:**
- Never run `reset-databases.sh` unless the user explicitly asks
- Never use `--no-cache` unless the user asks (slow)
- If a service fails to start, check logs: `./scripts/logs.sh <service>`
