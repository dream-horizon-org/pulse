Stop all running Pulse Docker services.

1. Change to the `deploy/` directory
2. Run `./scripts/stop.sh` to stop all containers (keeps volumes/network)
3. Verify all Pulse containers are stopped with `docker ps --filter name=pulse`
4. Report the result

## Common variants

- `./scripts/stop.sh -v` — stop and remove volumes (wipes MySQL/ClickHouse/Kafka/MinIO data)
- `./scripts/stop.sh --all` — stop, remove volumes, and remove the `pulse-network` bridge
- `./scripts/stop.sh <service>` — stop a single service (CLI fallback path); accepted: `ui`, `server`, `cron`, `ai`, `mysql`, `clickhouse`, `otel` (and their `pulse-*` aliases)
