---
name: view-logs
description: View logs for a specific Pulse service. Usage: /view-logs [service]. Services: server, ai, ui, cron, otel-collector, clickhouse, mysql, kafka.
allowed-tools: Bash(./deploy/scripts/logs.sh *)
---

If a service name is provided in $ARGUMENTS, tail logs for that service:

```bash
cd deploy && ./scripts/logs.sh $ARGUMENTS
```

If no service specified, ask the user which service they want logs for. Available services:
`server` · `ai` · `ui` · `cron` · `otel-collector` · `clickhouse` · `mysql` · `kafka` · `minio`
