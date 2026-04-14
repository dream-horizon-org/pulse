---
name: check-services
description: Check health of all running Pulse Docker services. Use when asked about service status or if something seems down.
allowed-tools: Bash(docker *) Bash(curl *)
---

Run health checks for all services:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Then check individual health endpoints:

```bash
curl -sf http://localhost:8080/healthcheck && echo "pulse-server: OK" || echo "pulse-server: DOWN"
curl -sf http://localhost:3000/healthcheck.txt && echo "pulse-ui: OK" || echo "pulse-ui: DOWN"
curl -sf http://localhost:8000/health && echo "pulse-ai: OK" || echo "pulse-ai: DOWN"
curl -sf http://localhost:4000/healthcheck && echo "alerts-cron: OK" || echo "alerts-cron: DOWN"
curl -sf http://localhost:8123/ && echo "clickhouse: OK" || echo "clickhouse: DOWN"
```

Summarize which services are UP and which are DOWN, and suggest next steps for anything that's down.
